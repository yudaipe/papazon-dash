const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const db = getFirestore();
  console.log("Firebase Admin SDK initialized.");

  const pairId = '<YOUR_PAIR_DOCUMENT_ID>';
  const masterUid = '<YOUR_MASTER_USER_UID>';
  const memberUid = '<YOUR_MEMBER_USER_UID>';

  // 1. pairs ドキュメントの更新
  const pairRef = db.collection('pairs').doc(pairId);
  const updatePair = pairRef.update({
    member_uid: memberUid,
    partners: FieldValue.arrayUnion(memberUid)
  });

  // 2. master_user ドキュメントの更新
  const masterRef = db.collection('users').doc(masterUid);
  const updateMaster = masterRef.update({
    pairId: pairId
  });

  // 3. member_user ドキュメントの更新
  const memberRef = db.collection('users').doc(memberUid);
  const updateMember = memberRef.update({
    pairId: pairId
  });

  Promise.all([updatePair, updateMaster, updateMember])
  .then(() => {
    console.log("SUCCESS: Pairing data successfully restored!");
    console.log(`Pair ID: ${pairId}`);
    console.log(`Master: ${masterUid}`);
    console.log(`Member: ${memberUid}`);
  })
  .catch(err => {
    console.error("Error restoring pairing data:", err);
  });

} catch (e) {
  console.error("Initialization error:", e);
}
