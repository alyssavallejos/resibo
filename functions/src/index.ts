import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import {
  extractWithGeminiVision,
  runHardChecks,
  matchAgainstLedger,
  recheckUnmatchedReceipts,
  ExtractedReceipt,
  BusinessAccount,
} from "./verification";
import { analyzeImageForensics, ImageForensics } from "./forensics";

admin.initializeApp();
const db = admin.firestore();

/**
 * Health check endpoint for Cloud Functions
 */
export const healthCheck = functions.https.onRequest((req, res) => {
  res.status(200).json({
    status: "ok",
    service: "Resibo Cloud Functions (Phase 4 Forensics & Alerts)",
    timestamp: new Date().toISOString(),
  });
});

/**
 * Send FCM push notification on every verdict or verdict change
 */
async function sendVerdictPushNotification(
  businessId: string,
  verdict: "verified" | "not_yet_received" | "fake",
  buyerName: string,
  amount: number,
  reason: string
) {
  let title = "";
  let body = "";

  const formattedAmount = amount > 0 ? `₱${amount.toFixed(2)}` : "";

  if (verdict === "verified") {
    title = `✅ ${formattedAmount} from ${buyerName} — Verified`;
    body = "Payment confirmed in your ledger.";
  } else if (verdict === "fake") {
    title = `❌ Fake receipt from ${buyerName}`;
    body = reason || "Reference number already used or edited image.";
  } else {
    title = `⚠️ Receipt from ${buyerName}`;
    body = `${formattedAmount} — waiting for payment.`;
  }

  try {
    const tokensSnap = await db.collection("businesses").doc(businessId).collection("fcmTokens").get();
    const tokens = tokensSnap.docs.map((d: admin.firestore.QueryDocumentSnapshot) => d.id);
    if (tokens.length > 0) {
      await admin.messaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        data: {
          businessId,
          verdict,
        },
      });
      functions.logger.info(`Push notification sent to ${tokens.length} devices: ${title}`);
    } else {
      functions.logger.info(`Push notification simulated (no devices registered yet): "${title}" - "${body}"`);
    }
  } catch (err) {
    functions.logger.warn(`Could not dispatch FCM notification for ${businessId}`, err);
  }
}

/**
 * Step 1–3 Trigger: Whenever a new receipt document is created
 */
export const onReceiptCreated = functions.firestore
  .document("businesses/{businessId}/receipts/{receiptId}")
  .onCreate(async (snapshot, context) => {
    const { businessId, receiptId } = context.params;
    const receiptData = snapshot.data();

    functions.logger.info(`Processing verification for receipt ${receiptId} in business ${businessId}`);

    try {
      // 1. Load business accounts
      const bizDoc = await db.collection("businesses").doc(businessId).get();
      const bizData = bizDoc.data();
      const accounts: BusinessAccount[] = bizData?.accounts || [];

      // 2. Extract image data if available in Storage
      let base64Image = "";
      let imageBuffer: Buffer | undefined;
      let mimeType = "image/jpeg";

      if (receiptData?.imagePath) {
        try {
          const bucket = admin.storage().bucket();
          const file = bucket.file(receiptData.imagePath);
          const [exists] = await file.exists();
          if (exists) {
            const [buffer] = await file.download();
            imageBuffer = buffer;
            base64Image = buffer.toString("base64");
            const [metadata] = await file.getMetadata();
            mimeType = metadata.contentType || "image/jpeg";
          }
        } catch (storageErr) {
          functions.logger.warn(`Could not download image from storage: ${receiptData.imagePath}`, storageErr);
        }
      }

      if (!base64Image && receiptData?.imageUrl && typeof receiptData.imageUrl === "string" && receiptData.imageUrl.startsWith("data:")) {
        const parts = receiptData.imageUrl.split(",");
        if (parts.length === 2) {
          const match = parts[0].match(/data:(.*?);base64/);
          mimeType = match ? match[1] : "image/jpeg";
          base64Image = parts[1];
          imageBuffer = Buffer.from(base64Image, "base64");
        }
      }

      // 3. Step 1: AI Vision Extraction with Gemini
      let extracted: ExtractedReceipt | null = null;
      let isAiAvailable = false;

      if (base64Image) {
        const aiResult = await extractWithGeminiVision(base64Image, mimeType);
        extracted = aiResult.extracted;
        isAiAvailable = aiResult.isAiAvailable;
      }

      // Step 1b: Image Forensics (Second Gemini call + EXIF metadata)
      let forensics: ImageForensics | null = null;
      if (base64Image) {
        forensics = await analyzeImageForensics(base64Image, imageBuffer, mimeType);
      }

      // Fallback: If AI is unavailable or no image in storage, use buyer inputs
      if (!extracted) {
        functions.logger.info(`Using basic check fallback for receipt ${receiptId}`);
        extracted = {
          provider: "gcash",
          bank_name: null,
          amount: receiptData?.expectedAmount || 0,
          currency: "PHP",
          reference_number: receiptData?.referenceNumber || null,
          date_time: new Date().toISOString(),
          recipient_name: accounts[0]?.accountName || "Merchant",
          recipient_number_last4: accounts[0]?.last4 || "",
          sender_name: receiptData?.buyerName || "Buyer",
          transaction_status: "successful",
          is_screenshot_of_real_app: true,
          confidence: 0.8,
        };
      }

      // 4. Step 2: Hard checks
      const hardCheckResult = await runHardChecks(
        businessId,
        receiptId,
        extracted,
        accounts,
        db
      );

      // 5. Step 3: Ledger match with forensics
      const verificationResult = await matchAgainstLedger(
        businessId,
        extracted,
        hardCheckResult.passed,
        hardCheckResult.reasons,
        hardCheckResult.checks,
        db,
        forensics
      );

      // Add label if AI was unavailable
      if (!isAiAvailable) {
        verificationResult.reasons.push("Puna: Basic check ang ginamit (AI vision temporarily unavailable).");
      }

      // 6. Update receipt document with verdict & forensics
      await snapshot.ref.update({
        verdict: verificationResult.verdict,
        reasons: verificationResult.reasons.slice(0, 4),
        checks: verificationResult.checks,
        extracted: verificationResult.extracted,
        forensics: verificationResult.forensics,
        matchedPaymentId: verificationResult.matchedPaymentId,
        matchMethod: verificationResult.matchMethod,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // 7. Dispatch push notification (FCM)
      await sendVerdictPushNotification(
        businessId,
        verificationResult.verdict,
        receiptData?.buyerName || "Buyer",
        extracted.amount,
        verificationResult.reasons[0] || ""
      );

      functions.logger.info(`Verdict for receipt ${receiptId}: ${verificationResult.verdict}`);
    } catch (err: unknown) {
      functions.logger.error(`Error verifying receipt ${receiptId}`, err);
      await snapshot.ref.update({
        verdict: "not_yet_received",
        reasons: ["Kasalukuyang sinusuri ang resibo (Verification pending retry)."],
      });
    }
  });

/**
 * Step 4 Trigger: When a new payment arrives in the ledger, re-check unmatched receipts
 */
export const onPaymentCreated = functions.firestore
  .document("businesses/{businessId}/payments/{paymentId}")
  .onCreate(async (snapshot, context) => {
    const { businessId, paymentId } = context.params;
    const paymentData = snapshot.data();

    functions.logger.info(`New payment ${paymentId} arrived for business ${businessId}. Re-checking unmatched receipts.`);

    try {
      const matched = await recheckUnmatchedReceipts(
        businessId,
        {
          id: paymentId,
          provider: paymentData?.provider || "gcash",
          amount: paymentData?.amount || 0,
          referenceNumber: paymentData?.referenceNumber,
        },
        db
      );

      functions.logger.info(`Re-check complete: ${matched} receipt(s) updated.`);
    } catch (err: unknown) {
      functions.logger.error(`Error re-checking receipts for payment ${paymentId}`, err);
    }
  });

/**
 * Manual override endpoint: "Mark as received (cash/other)" and "Mark as fake"
 */
export const manualOverrideReceipt = functions.https.onCall(async (data, context) => {
  const { businessId, receiptId, action } = data; // action: "mark_received" | "mark_fake"
  if (!businessId || !receiptId || !action) {
    throw new functions.https.HttpsError("invalid-argument", "Missing businessId, receiptId, or action");
  }

  const receiptRef = db.collection("businesses").doc(businessId).collection("receipts").doc(receiptId);
  const snap = await receiptRef.get();
  if (!snap.exists) {
    throw new functions.https.HttpsError("not-found", "Receipt not found");
  }

  if (action === "mark_received") {
    await receiptRef.update({
      verdict: "verified",
      matchMethod: "manual",
      reasons: ["Manu-manong minarkahan ng may-ari bilang natanggap (Manual override by owner)."],
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return { success: true, verdict: "verified" };
  } else if (action === "mark_fake") {
    await receiptRef.update({
      verdict: "fake",
      matchMethod: "manual",
      reasons: ["Manu-manong minarkahan ng may-ari bilang peke (Marked fake by owner)."],
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return { success: true, verdict: "fake" };
  }

  throw new functions.https.HttpsError("invalid-argument", "Invalid override action");
});

/**
 * Scheduled cleanup: Expire receipts still unmatched after 24 hours to FAKE
 */
export const expireUnmatchedReceipts = functions.pubsub
  .schedule("every 1 hours")
  .onRun(async () => {
    const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);

    const snapshot = await db
      .collectionGroup("receipts")
      .where("verdict", "==", "not_yet_received")
      .where("createdAt", "<", admin.firestore.Timestamp.fromDate(twentyFourHoursAgo))
      .get();

    for (const doc of snapshot.docs) {
      await doc.ref.update({
        verdict: "fake",
        reasons: ["Walang pumasok na bayad sa account sa loob ng 24 oras (No payment arrived within 24 hours)."],
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
  });
