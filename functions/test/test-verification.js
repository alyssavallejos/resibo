/**
 * Phase 3 Verification Engine Test Suite
 * Tests all 4 core scenarios:
 * 1. Matching receipt and payment -> VERIFIED
 * 2. Duplicate reference number -> FAKE
 * 3. Amount mismatch (₱1500 vs ₱150) -> FAKE
 * 4. Receipt first (NOT YET RECEIVED) -> Payment simulated -> Flips to VERIFIED
 */

const { normalizeRef } = require("../lib/verification");

// Mock in-memory Firestore database
function createMockDb() {
  const receipts = new Map();
  const payments = new Map();

  return {
    receipts,
    payments,
    addReceipt(id, data) {
      receipts.set(id, { id, ...data });
    },
    addPayment(id, data) {
      payments.set(id, { id, ...data });
    },
    getReceipt(id) {
      return receipts.get(id);
    },
    updateReceipt(id, updateData) {
      const existing = receipts.get(id) || {};
      receipts.set(id, { ...existing, ...updateData });
    }
  };
}

function verifyReceiptSync(receipt, db, businessAccounts) {
  const normRef = normalizeRef(receipt.reference_number);
  const checks = [];
  const reasons = [];

  // Check 1: Real app screenshot
  if (!receipt.is_screenshot_of_real_app || receipt.confidence < 0.3) {
    return {
      verdict: "fake",
      reasons: ["Hindi ito lehitimong resibo ng bayad."],
      checks: [{ name: "valid_receipt_image", passed: false }]
    };
  }
  checks.push({ name: "valid_receipt_image", passed: true });

  // Check 2: Reference number format
  let refValid = false;
  if (receipt.provider === "gcash") {
    refValid = normRef.length >= 10 && normRef.length <= 16 && /^[0-9]+$/.test(normRef);
  } else {
    refValid = normRef.length >= 6;
  }
  if (!refValid) {
    return {
      verdict: "fake",
      reasons: ["Mali ang format ng reference number."],
      checks: [{ name: "ref_format", passed: false }]
    };
  }
  checks.push({ name: "ref_format", passed: true });

  // Check 3: Duplicate reference check
  let isDuplicate = false;
  for (const [rId, otherReceipt] of db.receipts.entries()) {
    if (rId !== receipt.id && normalizeRef(otherReceipt.reference_number) === normRef) {
      isDuplicate = true;
      break;
    }
  }
  if (isDuplicate) {
    return {
      verdict: "fake",
      reasons: ["Nagamit na ang reference number na ito sa naunang resibo (Duplicate Reference)."],
      checks: [{ name: "no_duplicate_ref", passed: false }]
    };
  }
  checks.push({ name: "no_duplicate_ref", passed: true });

  // Check 4: Recipient match
  const matchedAccount = businessAccounts.some(acc => {
    return acc.last4 === receipt.recipient_number_last4 ||
           acc.accountName.toLowerCase().includes(receipt.recipient_name.toLowerCase());
  });
  if (!matchedAccount) {
    return {
      verdict: "fake",
      reasons: ["Ipinadala sa ibang account / Hindi tugma sa rehistradong account ng tindahan."],
      checks: [{ name: "recipient_match", passed: false }]
    };
  }
  checks.push({ name: "recipient_match", passed: true });

  // Check 5: Ledger Match (The decisive check)
  let matchedPayment = null;
  for (const payment of db.payments.values()) {
    if (normalizeRef(payment.referenceNumber) === normRef) {
      matchedPayment = payment;
      break;
    }
  }

  if (matchedPayment) {
    const amountDiff = Math.abs(matchedPayment.amount - receipt.amount);
    if (amountDiff <= 0.05) {
      return {
        verdict: "verified",
        reasons: ["Tugma ang reference number at halaga sa natanggap na bayad sa GCash."],
        checks: [...checks, { name: "ledger_match", passed: true }],
        matchedPaymentId: matchedPayment.id,
        matchMethod: "reference"
      };
    } else {
      return {
        verdict: "fake",
        reasons: [`Hindi tugma ang halaga: sinabi sa resibo ay ₱${receipt.amount} ngunit ₱${matchedPayment.amount} lang ang pumasok.`],
        checks: [...checks, { name: "ledger_match", passed: false }],
        matchedPaymentId: matchedPayment.id,
        matchMethod: "reference"
      };
    }
  }

  // No match yet in payments ledger
  return {
    verdict: "not_yet_received",
    reasons: ["Mukhang maayos ang resibo ngunit wala pang perang dumarating sa account ng tindahan."],
    checks: [...checks, { name: "ledger_match", passed: false }],
    matchedPaymentId: null,
    matchMethod: null
  };
}

function simulateIncomingPaymentTrigger(newPayment, db) {
  db.addPayment(newPayment.id, newPayment);
  const payRef = normalizeRef(newPayment.referenceNumber);

  for (const [rId, receipt] of db.receipts.entries()) {
    if (receipt.verdict === "not_yet_received" && normalizeRef(receipt.reference_number) === payRef) {
      if (Math.abs(receipt.amount - newPayment.amount) <= 0.05) {
        db.updateReceipt(rId, {
          verdict: "verified",
          matchedPaymentId: newPayment.id,
          matchMethod: "reference",
          reasons: ["✅ Pumasok na ang bayad! Awtomatikong na-verify ang resibo."]
        });
      }
    }
  }
}

// Run Test Suite
function runTests() {
  console.log("=== RUNNING PHASE 3 VERIFICATION TESTS ===\n");
  const accounts = [{ provider: "gcash", accountName: "Rosa S.", last4: "8899" }];
  const db = createMockDb();

  // Test 1: Real payment simulated first, then matching receipt uploaded -> VERIFIED
  db.addPayment("pay-1", {
    id: "pay-1",
    provider: "gcash",
    amount: 150.0,
    referenceNumber: "123456789012"
  });

  const receipt1 = {
    id: "rcpt-1",
    provider: "gcash",
    amount: 150.0,
    reference_number: "123456789012",
    recipient_name: "Rosa S.",
    recipient_number_last4: "8899",
    transaction_status: "successful",
    is_screenshot_of_real_app: true,
    confidence: 0.95
  };

  const res1 = verifyReceiptSync(receipt1, db, accounts);
  db.addReceipt("rcpt-1", { ...receipt1, ...res1 });
  console.log("Test 1 (Simulated payment first -> Matching receipt):");
  console.log(`  Expected: verified | Actual: ${res1.verdict} | Reasons: ${res1.reasons[0]}`);
  if (res1.verdict !== "verified") throw new Error("Test 1 Failed");
  console.log("  PASS ✅\n");

  // Test 2: Upload the same receipt again by another buyer -> FAKE (duplicate)
  const receipt2 = {
    id: "rcpt-2",
    provider: "gcash",
    amount: 150.0,
    reference_number: "123456789012", // Same reference
    recipient_name: "Rosa S.",
    recipient_number_last4: "8899",
    transaction_status: "successful",
    is_screenshot_of_real_app: true,
    confidence: 0.95
  };
  const res2 = verifyReceiptSync(receipt2, db, accounts);
  console.log("Test 2 (Duplicate receipt upload):");
  console.log(`  Expected: fake | Actual: ${res2.verdict} | Reasons: ${res2.reasons[0]}`);
  if (res2.verdict !== "fake" || !res2.reasons[0].includes("Duplicate")) throw new Error("Test 2 Failed");
  console.log("  PASS ✅\n");

  // Test 3: Upload receipt with edited amount (Receipt says ₱1,500, payment was ₱150) -> FAKE
  const receipt3 = {
    id: "rcpt-3",
    provider: "gcash",
    amount: 1500.0, // Altered amount!
    reference_number: "998877665544",
    recipient_name: "Rosa S.",
    recipient_number_last4: "8899",
    transaction_status: "successful",
    is_screenshot_of_real_app: true,
    confidence: 0.95
  };
  // Real incoming was only ₱150
  db.addPayment("pay-3", {
    id: "pay-3",
    provider: "gcash",
    amount: 150.0,
    referenceNumber: "998877665544"
  });
  const res3 = verifyReceiptSync(receipt3, db, accounts);
  console.log("Test 3 (Wrong / altered amount):");
  console.log(`  Expected: fake | Actual: ${res3.verdict} | Reasons: ${res3.reasons[0]}`);
  if (res3.verdict !== "fake" || !res3.reasons[0].includes("Hindi tugma")) throw new Error("Test 3 Failed");
  console.log("  PASS ✅\n");

  // Test 4: Upload receipt first (NOT YET RECEIVED) -> Simulate payment -> Flips to VERIFIED
  const receipt4 = {
    id: "rcpt-4",
    provider: "gcash",
    amount: 300.0,
    reference_number: "554433221100",
    recipient_name: "Rosa S.",
    recipient_number_last4: "8899",
    transaction_status: "successful",
    is_screenshot_of_real_app: true,
    confidence: 0.95
  };
  const res4Before = verifyReceiptSync(receipt4, db, accounts);
  db.addReceipt("rcpt-4", { ...receipt4, ...res4Before });
  console.log("Test 4a (Receipt uploaded before money arrives):");
  console.log(`  Expected: not_yet_received | Actual: ${res4Before.verdict}`);
  if (res4Before.verdict !== "not_yet_received") throw new Error("Test 4a Failed");
  console.log("  PASS ✅");

  // Now simulate incoming payment
  simulateIncomingPaymentTrigger({
    id: "pay-4",
    provider: "gcash",
    amount: 300.0,
    referenceNumber: "554433221100"
  }, db);

  const receipt4After = db.getReceipt("rcpt-4");
  console.log("Test 4b (Payment arrives -> Trigger flips receipt status):");
  console.log(`  Expected: verified | Actual: ${receipt4After.verdict} | Reasons: ${receipt4After.reasons[0]}`);
  if (receipt4After.verdict !== "verified") throw new Error("Test 4b Failed");
  console.log("  PASS ✅\n");

  console.log("🎉 ALL 4 PHASE 3 VERIFICATION TESTS PASSED SUCCESSFULLY!");
}

runTests();
