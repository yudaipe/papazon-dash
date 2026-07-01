const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const db = getFirestore();
  console.log("Firebase Admin SDK initialized.");

  const uid = 'test-user-p12c';
  const testName = 'タロウ（検証用）';

  const userData = {
    uid: uid,
    displayName: testName,
    role: 'member',
    pairId: 'YOUR_PAIR_ID',
    updatedAt: new Date()
  };

  // 1. users コレクションへの書き込み
  db.collection('users').doc(uid).set(userData)
  .then(() => {
    console.log(`SUCCESS: User profile created for ${uid} with displayName='${testName}'`);
    
    // 2. 書き込まれたデータの読み込み検証
    return db.collection('users').doc(uid).get();
  })
  .then(docSnap => {
    if (!docSnap.exists) {
      console.error("FAIL: Document not found after set!");
      return;
    }
    const data = docSnap.data();
    console.log("SUCCESS: Read back displayName from Firestore:", data.displayName);
    if (data.displayName === testName) {
      console.log("VERIFICATION PASSED: Firestore successfully registered customized displayName!");
    } else {
      console.error("VERIFICATION FAILED: displayName mismatch!");
    }
  })
  .catch(err => {
    console.error("Error running validation:", err);
  });

} catch (e) {
  console.error("Initialization error:", e);
}
