const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const db = getFirestore();
  console.log("Firebase Admin SDK initialized.");

  const pairId = 'YOUR_PAIR_ID';
  const masterUid = 'YOUR_USER_UID';

  // 5分前の過去のタイムスタンプを設定して即時発火対象にする
  const pastDate = new Date(Date.now() - 5 * 60 * 1000);
  const reminderAt = Timestamp.fromDate(pastDate);

  const newItem = {
    name: 'milk (reminder test)',
    createdBy: masterUid,
    status: 'open',
    createdAt: new Date(),
    reminder_at: reminderAt
  };

  db.collection('pairs').doc(pairId).collection('items').add(newItem)
  .then(docRef => {
    console.log("SUCCESS: New reminder item successfully created in Firestore!");
    console.log(`Document ID: ${docRef.id}`);
    console.log(`Path: pairs/${pairId}/items/${docRef.id}`);
  })
  .catch(err => {
    console.error("Error creating item:", err);
  });

} catch (e) {
  console.error("Initialization error:", e);
}
