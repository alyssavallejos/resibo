import * as admin from "firebase-admin";
import { GoogleGenerativeAI } from "@google/generative-ai";

export interface ExtractedReceipt {
  provider: "gcash" | "maya" | "bank" | "unknown";
  bank_name: string | null;
  amount: number;
  currency: string;
  reference_number: string | null;
  date_time: string;
  recipient_name: string;
  recipient_number_last4: string;
  sender_name: string;
  transaction_status: "successful" | "pending" | "failed" | "scheduled" | "unknown";
  is_screenshot_of_real_app: boolean;
  confidence: number;
}

export interface CheckResult {
  name: string;
  passed: boolean;
  detail: string;
}

import { ImageForensics } from "./forensics";

export interface VerificationResult {
  verdict: "verified" | "not_yet_received" | "fake";
  reasons: string[];
  checks: CheckResult[];
  extracted: ExtractedReceipt | null;
  forensics: ImageForensics | null;
  matchedPaymentId: string | null;
  matchMethod: "reference" | "amount_time" | "manual" | null;
}

export interface BusinessAccount {
  provider: string;
  accountName: string;
  last4: string;
}

/**
 * Normalize reference numbers: uppercase alphanumeric only, no spaces or dashes
 */
export function normalizeRef(ref: string | null | undefined): string {
  if (!ref) return "";
  return ref.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

/**
 * Step 1: AI extraction using Gemini Flash Vision
 */
export async function extractWithGeminiVision(
  imageBase64: string,
  mimeType: string = "image/jpeg"
): Promise<{ extracted: ExtractedReceipt | null; isAiAvailable: boolean }> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return { extracted: null, isAiAvailable: false };
  }

  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    // Use gemini-1.5-flash or gemini-2.5-flash
    const model = genAI.getGenerativeModel({
      model: "gemini-1.5-flash",
      generationConfig: {
        responseMimeType: "application/json",
      },
    });

    const prompt = `You are an expert fraud-detection AI inspecting Philippine payment receipts (GCash, Maya, BDO, BPI, UnionBank, Metrobank, LandBank).
Analyze this uploaded screenshot carefully. Extract all receipt details and return ONLY a valid JSON object with this exact structure:
{
  "provider": "gcash" | "maya" | "bank" | "unknown",
  "bank_name": string or null,
  "amount": number (e.g. 150.00),
  "currency": "PHP",
  "reference_number": string or null (the transaction reference or ref no),
  "date_time": string (as shown on receipt, e.g. "Oct 18, 2026, 10:15 AM"),
  "recipient_name": string (name of the person or store receiving money),
  "recipient_number_last4": string (last 4 digits of phone or account number),
  "sender_name": string (name of person sending money),
  "transaction_status": "successful" | "pending" | "failed" | "scheduled" | "unknown",
  "is_screenshot_of_real_app": boolean (false if this is a random photo, meme, or clearly fabricated document),
  "confidence": number between 0 and 1
}
If the image is not a payment receipt at all, set "is_screenshot_of_real_app": false and "confidence": 0.`;

    const result = await model.generateContent([
      prompt,
      {
        inlineData: {
          data: imageBase64,
          mimeType,
        },
      },
    ]);

    const responseText = result.response.text();
    const parsed = JSON.parse(responseText) as ExtractedReceipt;
    return { extracted: parsed, isAiAvailable: true };
  } catch (err) {
    console.warn("Gemini vision extraction failed, falling back to basic check:", err);
    return { extracted: null, isAiAvailable: false };
  }
}

/**
 * Step 2: Hard checks (Deterministic code, not AI)
 */
export async function runHardChecks(
  businessId: string,
  receiptId: string,
  extracted: ExtractedReceipt,
  businessAccounts: BusinessAccount[],
  firestore: admin.firestore.Firestore
): Promise<{ passed: boolean; checks: CheckResult[]; reasons: string[] }> {
  const checks: CheckResult[] = [];
  const reasons: string[] = [];
  let overallPassed = true;

  // Check 1: Must be a real payment receipt
  if (!extracted.is_screenshot_of_real_app || extracted.confidence < 0.25) {
    checks.push({
      name: "valid_receipt_image",
      passed: false,
      detail: "Hindi ito lehitimong screenshot ng resibo ng bayad.",
    });
    reasons.push("Hindi ito lehitimong resibo ng bayad (This is not a payment receipt).");
    return { passed: false, checks, reasons };
  } else {
    checks.push({
      name: "valid_receipt_image",
      passed: true,
      detail: "Kinikilalang lehitimong app screenshot ang resibo.",
    });
  }

  // Check 2: Reference number format
  const ref = normalizeRef(extracted.reference_number);
  let refFormatValid = false;
  if (extracted.provider === "gcash") {
    // GCash ref numbers are typically 10 to 14 digits
    refFormatValid = ref.length >= 10 && ref.length <= 16 && /^[0-9]+$/.test(ref);
  } else if (extracted.provider === "maya") {
    refFormatValid = ref.length >= 10 && ref.length <= 20;
  } else {
    refFormatValid = ref.length >= 6 && ref.length <= 25;
  }

  if (ref && refFormatValid) {
    checks.push({
      name: "ref_format",
      passed: true,
      detail: `Wastong format ng reference number (${ref}).`,
    });
  } else {
    checks.push({
      name: "ref_format",
      passed: false,
      detail: `Maling format o nawawalang reference number: "${ref || "wala"}".`,
    });
    reasons.push("Mali ang format ng reference number para sa provider na ito.");
    overallPassed = false;
  }

  // Check 3: Duplicate Reference Number Check
  if (ref) {
    const dupSnapshot = await firestore
      .collection("businesses")
      .doc(businessId)
      .collection("receipts")
      .where("extracted.reference_number", "==", extracted.reference_number)
      .get();

    const duplicates = dupSnapshot.docs.filter((d: admin.firestore.QueryDocumentSnapshot) => d.id !== receiptId);
    if (duplicates.length > 0) {
      const earlierDate = duplicates[0].data()?.createdAt?.toDate()?.toLocaleDateString() || "nakaraang araw";
      checks.push({
        name: "no_duplicate_ref",
        passed: false,
        detail: `Nagamit na ang reference number na ito noong ${earlierDate}.`,
      });
      reasons.push(`Nagamit na ang reference number na ito sa naunang resibo noong ${earlierDate} (Duplicate Reference).`);
      overallPassed = false;
    } else {
      checks.push({
        name: "no_duplicate_ref",
        passed: true,
        detail: "Bago at hindi pa nagamit ang reference number.",
      });
    }
  }

  // Check 4: Recipient Account Match
  const recipientLast4 = (extracted.recipient_number_last4 || "").trim();
  const recipientName = (extracted.recipient_name || "").toLowerCase().trim();

  const accountMatched = businessAccounts.some((acc) => {
    const last4Matches = recipientLast4 && acc.last4.endsWith(recipientLast4);
    const nameMatches =
      recipientName &&
      (recipientName.includes(acc.accountName.toLowerCase()) ||
        acc.accountName.toLowerCase().includes(recipientName));
    return last4Matches || nameMatches;
  });

  if (businessAccounts.length === 0 || accountMatched) {
    checks.push({
      name: "recipient_match",
      passed: true,
      detail: "Tumutugma ang pinadalhan sa nakarehistrong account ng tindahan.",
    });
  } else {
    checks.push({
      name: "recipient_match",
      passed: false,
      detail: `Ipinadala sa ibang account: "${extracted.recipient_name || ""} ••••${recipientLast4}".`,
    });
    reasons.push("Ipinadala sa ibang account / Hindi tugma sa rehistradong account ng tindahan.");
    overallPassed = false;
  }

  // Check 5: Transaction Status must be successful
  if (extracted.transaction_status === "successful") {
    checks.push({
      name: "successful_status",
      passed: true,
      detail: "Matagumpay (Successful) ang transaksyon.",
    });
  } else {
    checks.push({
      name: "successful_status",
      passed: false,
      detail: `Hindi matagumpay ang transaksyon: status ay "${extracted.transaction_status}".`,
    });
    reasons.push(`Hindi matagumpay ang bayad (Status: ${extracted.transaction_status}).`);
    overallPassed = false;
  }

  // Check 6: Date not in future and not older than 7 days
  const now = Date.now();
  const receiptTimestamp = extracted.date_time ? new Date(extracted.date_time).getTime() : now;
  const sevenDaysMs = 7 * 24 * 60 * 60 * 1000;

  if (isNaN(receiptTimestamp) || (receiptTimestamp <= now + 3600000 && now - receiptTimestamp <= sevenDaysMs)) {
    checks.push({
      name: "valid_date",
      passed: true,
      detail: "Pasok sa wastong petsa (huling 7 araw).",
    });
  } else if (receiptTimestamp > now + 3600000) {
    checks.push({
      name: "valid_date",
      passed: false,
      detail: "Nasa hinaharap (future date) ang petsa ng resibo.",
    });
    reasons.push("Mali ang petsa: nasa hinaharap ang oras sa resibo.");
    overallPassed = false;
  } else {
    checks.push({
      name: "valid_date",
      passed: false,
      detail: "Masyado nang luma ang resibo (higit 7 araw).",
    });
    reasons.push("Luma na ang resibo: mahigit 7 araw na ang nakalipas.");
    overallPassed = false;
  }

  return { passed: overallPassed, checks, reasons };
}

/**
 * Step 3: Match against the Merchant's Real Incoming Payments Ledger
 */
export async function matchAgainstLedger(
  businessId: string,
  extracted: ExtractedReceipt,
  hardChecksPassed: boolean,
  hardCheckReasons: string[],
  checks: CheckResult[],
  firestore: admin.firestore.Firestore,
  forensics?: ImageForensics | null
): Promise<VerificationResult> {
  const normRef = normalizeRef(extracted.reference_number);

  // Search incoming payments for this business
  const paymentsSnap = await firestore
    .collection("businesses")
    .doc(businessId)
    .collection("payments")
    .orderBy("receivedAt", "desc")
    .limit(50)
    .get();

  const payments = paymentsSnap.docs.map((d: admin.firestore.QueryDocumentSnapshot) => ({
    id: d.id,
    ...d.data(),
  })) as Array<{
    id: string;
    provider: string;
    amount: number;
    referenceNumber?: string | null;
    senderNameMasked?: string;
    receivedAt?: admin.firestore.Timestamp;
  }>;

  // 1. Direct Reference Number Match
  if (normRef) {
    const refMatch = payments.find((p) => normalizeRef(p.referenceNumber) === normRef);
    if (refMatch) {
      const amountDiff = Math.abs(refMatch.amount - extracted.amount);
      if (amountDiff <= 0.05) {
        // MATCHED BY REFERENCE NUMBER & AMOUNT -> VERIFIED ✅
        const reasons = [
          "Tugma ang reference number at halaga sa natanggap na bayad sa telepono ng may-ari.",
        ];
        if (forensics && forensics.tamper_risk === "high") {
          reasons.push(`Babala: Mukhang na-edit ang larawan (${forensics.explanation}), ngunit pumasok ang tunay na bayad sa account.`);
        } else if (!hardChecksPassed) {
          reasons.push("Puna: May kaunting babala sa hard checks ngunit kumpirmadong pumasok ang tunay na pera.");
        }

        return {
          verdict: "verified",
          reasons,
          checks: [
            ...checks,
            {
              name: "ledger_match",
              passed: true,
              detail: `Tugma sa totoong bayad (${refMatch.provider.toUpperCase()} ₱${refMatch.amount.toFixed(2)}).`,
            },
          ],
          extracted,
          forensics: forensics || null,
          matchedPaymentId: refMatch.id,
          matchMethod: "reference",
        };
      } else {
        // Reference matched BUT Amount is DIFFERENT -> FAKE ❌
        const reason = `Hindi tugma ang halaga: sinabi sa resibo ay ₱${extracted.amount.toFixed(2)} ngunit ₱${refMatch.amount.toFixed(2)} lang ang pumasok sa account.`;
        return {
          verdict: "fake",
          reasons: [reason, ...(forensics?.explanation ? [forensics.explanation] : []), ...hardCheckReasons].slice(0, 4),
          checks: [
            ...checks,
            {
              name: "ledger_match",
              passed: false,
              detail: `Halagang natanggap sa telepono (₱${refMatch.amount.toFixed(2)}) ay iba sa resibo (₱${extracted.amount.toFixed(2)}).`,
            },
          ],
          extracted,
          forensics: forensics || null,
          matchedPaymentId: refMatch.id,
          matchMethod: "reference",
        };
      }
    }
  }

  // 2. Fallback Amount + Masked Sender Name + Time Match (within ±10 mins)
  // For notifications that didn't provide a reference number
  const receiptTime = extracted.date_time ? new Date(extracted.date_time).getTime() : Date.now();
  const fallbackMatch = payments.find((p) => {
    if (!p.amount) return false;
    const diff = Math.abs(p.amount - extracted.amount);
    if (diff > 0.05) return false;

    if (p.receivedAt) {
      const pTime = p.receivedAt.toDate().getTime();
      const timeDiffMins = Math.abs(receiptTime - pTime) / (60 * 1000);
      return timeDiffMins <= 15;
    }
    return false;
  });

  if (fallbackMatch && hardChecksPassed) {
    return {
      verdict: "verified",
      reasons: [
        "Verified by amount and time: Na-verify ayon sa eksaktong halaga at oras ng pagdating ng pera.",
      ],
      checks: [
        ...checks,
        {
          name: "ledger_match",
          passed: true,
          detail: `Tugma sa halaga (₱${fallbackMatch.amount.toFixed(2)}) at oras ng natanggap na notification.`,
        },
      ],
      extracted,
      forensics: forensics || null,
      matchedPaymentId: fallbackMatch.id,
      matchMethod: "amount_time",
    };
  }

  // 3. No match found in the ledger yet
  if (forensics && forensics.tamper_risk === "high") {
    // High tamper risk with NO ledger confirmation is strictly FAKE ❌
    return {
      verdict: "fake",
      reasons: [
        `❌ Posibleng peke o binagong resibo: ${forensics.explanation}`,
        ...hardCheckReasons,
      ].slice(0, 4),
      checks: [
        ...checks,
        {
          name: "image_forensics",
          passed: false,
          detail: `High tamper risk: ${forensics.edited_signs.join(", ") || forensics.explanation}`,
        },
      ],
      extracted,
      forensics,
      matchedPaymentId: null,
      matchMethod: null,
    };
  }

  if (!hardChecksPassed) {
    // If any hard check failed and no real money arrived, verdict is FAKE ❌
    return {
      verdict: "fake",
      reasons: hardCheckReasons.slice(0, 4),
      checks: [
        ...checks,
        {
          name: "ledger_match",
          passed: false,
          detail: "Walang natagpuang katugmang bayad sa ledger ng tindahan.",
        },
      ],
      extracted,
      forensics: forensics || null,
      matchedPaymentId: null,
      matchMethod: null,
    };
  }

  // If no bank transfer notification arrived in ledger -> FAKE (Fraud Receipt)
  return {
    verdict: "fake",
    reasons: [
      `❌ Pekeng Resibo (Fraud): Walang natanggap na bank transfer notification ang Resibo para sa reference number (${normRef || "Wala"}).`,
      "Huwag i-release ang paninda hangga't hindi natatanggap ang tunay na notification sa telepono.",
    ],
    checks: [
      ...checks,
      {
        name: "ledger_match",
        passed: false,
        detail: "Walang natagpuang bank transfer notification para sa reference number na ito sa telepono ng may-ari.",
      },
      {
        name: "reference_cross_reference",
        passed: false,
        detail: `Hindi ma-cross-reference ang reference number (${normRef || "Wala"}) sa anumang natanggap na notification.`,
      },
    ],
    extracted,
    forensics: forensics || null,
    matchedPaymentId: null,
    matchMethod: null,
  };
}

/**
 * Re-check unmatched receipts when a new payment arrives (Step 4)
 */
export async function recheckUnmatchedReceipts(
  businessId: string,
  newPayment: { id: string; provider: string; amount: number; referenceNumber?: string | null },
  firestore: admin.firestore.Firestore
): Promise<number> {
  const normPayRef = normalizeRef(newPayment.referenceNumber);
  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);

  const pendingReceiptsSnap = await firestore
    .collection("businesses")
    .doc(businessId)
    .collection("receipts")
    .where("verdict", "==", "not_yet_received")
    .where("createdAt", ">=", admin.firestore.Timestamp.fromDate(oneDayAgo))
    .get();

  let matchedCount = 0;

  for (const doc of pendingReceiptsSnap.docs) {
    const data = doc.data();
    const extracted = data.extracted as ExtractedReceipt | undefined;
    if (!extracted) continue;

    const receiptRef = normalizeRef(extracted.reference_number);

    // Check match
    const isRefMatch = normPayRef && receiptRef && normPayRef === receiptRef;
    const isAmountMatch = Math.abs(extracted.amount - newPayment.amount) <= 0.05;

    if (isRefMatch && isAmountMatch) {
      await doc.ref.update({
        verdict: "verified",
        matchedPaymentId: newPayment.id,
        matchMethod: "reference",
        reasons: [
          "✅ Pumasok na ang bayad! Awtomatikong na-verify ang resibo nang matanggap ang notification sa telepono.",
        ],
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      matchedCount++;
    } else if (isRefMatch && !isAmountMatch) {
      await doc.ref.update({
        verdict: "fake",
        matchedPaymentId: newPayment.id,
        matchMethod: "reference",
        reasons: [
          `❌ Hindi tugma ang halaga: sinabi sa resibo ay ₱${extracted.amount.toFixed(2)} ngunit ₱${newPayment.amount.toFixed(2)} lang ang pumasok.`,
        ],
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      matchedCount++;
    }
  }

  return matchedCount;
}
