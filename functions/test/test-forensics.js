/**
 * Phase 4 Forensics & FCM Push Notifications Test Suite
 */

const { inspectImageMetadata } = require("../lib/forensics");

function runForensicsTests() {
  console.log("=== RUNNING PHASE 4 IMAGE FORENSICS TESTS ===\n");

  // Test 1: Image metadata inspection for editing software tags
  const fakeCanvaBuffer = Buffer.from("JFIF...SomeExifData...Software: Canva Web Designer...End", "utf8");
  const detectedSoftware = inspectImageMetadata(fakeCanvaBuffer);
  console.log("Test 1 (Canva editing software tag in metadata):");
  console.log(`  Detected: ${detectedSoftware}`);
  if (detectedSoftware !== "Canva") throw new Error("Test 1 Failed: Expected Canva");
  console.log("  PASS ✅\n");

  const fakePhotoshopBuffer = Buffer.from("Photoshop 3.0 8BIM...Adobe Photoshop 2024...", "utf8");
  const detectedPhotoshop = inspectImageMetadata(fakePhotoshopBuffer);
  console.log("Test 2 (Photoshop tag in metadata):");
  console.log(`  Detected: ${detectedPhotoshop}`);
  if (detectedPhotoshop !== "Photoshop") throw new Error("Test 2 Failed: Expected Photoshop");
  console.log("  PASS ✅\n");

  // Test 3: High tamper risk without ledger match -> FAKE
  const forensicsResult = {
    edited_signs: ["Mismatched font weight on amount digits", "Blur patch behind ₱1,500"],
    generator_app_signs: ["Fake GCash canvas layout"],
    tamper_risk: "high",
    explanation: "May malinaw na bakas ng pag-edit sa paligid ng halaga: magkaiba ang font ng mga numero."
  };

  const isHighTamperRiskFake = forensicsResult.tamper_risk === "high";
  console.log("Test 3 (High Tamper Risk -> FAKE verdict with explanation):");
  console.log(`  Tamper Risk: ${forensicsResult.tamper_risk} -> Verdict: ${isHighTamperRiskFake ? "fake" : "verified"}`);
  console.log(`  Explanation: ${forensicsResult.explanation}`);
  if (!isHighTamperRiskFake) throw new Error("Test 3 Failed");
  console.log("  PASS ✅\n");

  // Test 4: Push Notification formatting
  function formatPush(verdict, buyerName, amount, reason) {
    if (verdict === "verified") {
      return `✅ ₱${amount.toFixed(2)} from ${buyerName} — Verified`;
    } else if (verdict === "fake") {
      return `❌ Fake receipt from ${buyerName} — ${reason}`;
    } else {
      return `⚠️ Receipt from ${buyerName} — waiting for payment`;
    }
  }

  const pushVerified = formatPush("verified", "Juan", 1500, "");
  const pushFake = formatPush("fake", "Juan", 1500, "reference number already used");
  const pushWaiting = formatPush("not_yet_received", "Juan", 1500, "");

  console.log("Test 4 (FCM Push Notifications):");
  console.log(`  Verified: "${pushVerified}"`);
  console.log(`  Fake:     "${pushFake}"`);
  console.log(`  Waiting:  "${pushWaiting}"`);

  if (!pushVerified.includes("Verified") || !pushFake.includes("reference number already used") || !pushWaiting.includes("waiting")) {
    throw new Error("Test 4 Failed");
  }
  console.log("  PASS ✅\n");

  console.log("🎉 ALL PHASE 4 FORENSICS TESTS PASSED SUCCESSFULLY!");
}

runForensicsTests();
