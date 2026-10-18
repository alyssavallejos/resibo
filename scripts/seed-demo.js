/**
 * Resibo Demo Seeding Script
 * Seeds the demo shop "Kape ni Aling Rosa" with registered GCash and Maya accounts.
 */

const admin = require("firebase-admin");

async function seedDemoShop() {
  console.log("=== SEEDING DEMO SHOP: Kape ni Aling Rosa ===");

  const shopData = {
    name: "Kape ni Aling Rosa",
    slug: "kape-ni-aling-rosa",
    ownerUid: "demo_owner_1",
    accounts: [
      {
        provider: "gcash",
        accountName: "Rosa S.",
        last4: "8899"
      },
      {
        provider: "maya",
        accountName: "Rosa Santos",
        last4: "4321"
      }
    ],
    updatedAt: new Date().toISOString()
  };

  // If running with initialized firebase-admin
  if (admin.apps.length > 0) {
    const db = admin.firestore();
    const shopRef = db.collection("businesses").doc("demo-shop-1");
    await shopRef.set(shopData, { merge: true });
    console.log("Successfully seeded shop to Firestore document: businesses/demo-shop-1");
  } else {
    console.log("Local/Offline preview seed object ready:");
    console.log(JSON.stringify(shopData, null, 2));
  }

  console.log("Shop slug: /pay/kape-ni-aling-rosa");
  console.log("Registered Accounts:");
  console.log("  1. GCash: Rosa S. (•••• 8899)");
  console.log("  2. Maya:  Rosa Santos (•••• 4321)");
  console.log("Done! ✅\n");
}

if (require.main === module) {
  seedDemoShop().catch(console.error);
}

module.exports = { seedDemoShop };
