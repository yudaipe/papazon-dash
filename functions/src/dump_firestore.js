const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const fs = require('fs');
const path = require('path');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const db = getFirestore();
  console.log("Firebase Admin SDK initialized.");

  const outputDir = './output';
  if (!fs.existsSync(outputDir)){
    fs.mkdirSync(outputDir, { recursive: true });
  }

  // 1. Dump Users
  db.collection('users').get()
  .then(snapshot => {
    const users = [];
    snapshot.forEach(doc => {
      users.push({ id: doc.id, ...doc.data() });
    });
    fs.writeFileSync(path.join(outputDir, 'users.json'), JSON.stringify(users, null, 2));
    console.log("SUCCESS: users.json dumped.");
    return db.collection('pairs').get();
  })
  .then(snapshot => {
    // 2. Dump Pairs
    const pairs = [];
    const itemPromises = [];
    snapshot.forEach(doc => {
      const pairData = { id: doc.id, ...doc.data() };
      pairs.push(pairData);

      // items サブコレクションのダンプ準備
      const itemPromise = db.collection('pairs').doc(doc.id).collection('items').get()
        .then(itemSnap => {
          const items = [];
          itemSnap.forEach(itemDoc => {
            items.push({ id: itemDoc.id, pairId: doc.id, ...itemDoc.data() });
          });
          return items;
        });
      itemPromises.push(itemPromise);
    });

    fs.writeFileSync(path.join(outputDir, 'firestore_pairs.json'), JSON.stringify(pairs, null, 2));
    console.log("SUCCESS: firestore_pairs.json dumped.");

    return Promise.all(itemPromises);
  })
  .then(allPairItems => {
    // 3. Dump Items
    const items = allPairItems.flat();
    fs.writeFileSync(path.join(outputDir, 'items.json'), JSON.stringify(items, null, 2));
    console.log("SUCCESS: items.json dumped.");
    console.log("Firestore dump completed successfully!");
  })
  .catch(err => {
    console.error("Error dumping Firestore:", err);
  });

} catch (e) {
  console.error("Initialization error:", e);
}
