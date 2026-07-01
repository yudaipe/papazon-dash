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
  const masterUid = 'YOUR_USER_UID';

  const newItem = {
    name: 'milk (snapshotListener test)',
    createdBy: masterUid,
    status: 'open',
    createdAt: new Date()
  };

  db.collection('pairs').doc(pairId).collection('items').add(newItem)
  .then(docRef => {
    console.log("SUCCESS: New item successfully created in Firestore!");
    console.log(`Document ID: ${docRef.id}`);
    console.log(`Path: pairs/${pairId}/items/${docRef.id}`);
  })
  .catch(err => {
    console.error("Error creating item:", err);
  });

} catch (e) {
  console.error("Initialization error:", e);
}
