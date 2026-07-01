const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const db = getFirestore();
  console.log("Firebase Admin SDK initialized.");

  const pairId = 'YOUR_PAIR_ID';
  const itemId = 'YOUR_ITEM_ID';

  db.collection('pairs').doc(pairId).collection('items').doc(itemId).update({
    status: 'done',
    completedAt: new Date()
  })
  .then(() => {
    console.log(`SUCCESS: Item ${itemId} status updated to 'done' in Firestore!`);
  })
  .catch(err => {
    console.error("Error updating item status:", err);
  });

} catch (e) {
  console.error("Initialization error:", e);
}
