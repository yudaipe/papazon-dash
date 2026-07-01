const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const db = getFirestore();
  console.log("Firestore initialized.");

  async function dumpCollections() {
    // 1. users
    console.log("\n=== USERS ===");
    const usersSnap = await db.collection('users').get();
    console.log(`Total users: ${usersSnap.size}`);
    usersSnap.forEach(doc => {
      console.log(`Doc ID: ${doc.id}`);
      console.log(JSON.stringify(doc.data(), null, 2));
    });

    // 2. pairs
    console.log("\n=== PAIRS ===");
    const pairsSnap = await db.collection('pairs').get();
    console.log(`Total pairs: ${pairsSnap.size}`);
    pairsSnap.forEach(doc => {
      console.log(`Doc ID: ${doc.id}`);
      console.log(JSON.stringify(doc.data(), null, 2));
    });

    // 3. items (Collection Group)
    console.log("\n=== ITEMS (Collection Group) ===");
    const itemsSnap = await db.collectionGroup('items').get();
    console.log(`Total items: ${itemsSnap.size}`);
    itemsSnap.forEach(doc => {
      console.log(`Doc ID: ${doc.id} (Path: ${doc.ref.path})`);
      console.log(JSON.stringify(doc.data(), null, 2));
    });
  }

  dumpCollections().catch(err => {
    console.error("Error dumping collections:", err);
  });
} catch (e) {
  console.error("Initialization error:", e);
}
