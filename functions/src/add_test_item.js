const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');

process.env.GCLOUD_PROJECT = 'papazon-dash';
initializeApp({ projectId: 'papazon-dash' });
const db = getFirestore();

async function main() {
  const pairId = 'YOUR_PAIR_ID';
  
  const pairBefore = await db.doc(`pairs/${pairId}`).get();
  console.log('BEFORE pair.active:', pairBefore.data()?.active);
  
  const itemsBefore = await db.collection(`pairs/${pairId}/items`).get();
  console.log('BEFORE items count:', itemsBefore.size);
  itemsBefore.forEach(d => console.log('  item:', d.id, JSON.stringify(d.data().name)));
  
  const ref = await db.collection(`pairs/${pairId}/items`).add({
    name: 'CTL3_phase4_test',
    status: 'open',
    created_at: Timestamp.now(),
    pairId,
    created_by: 'YOUR_USER_UID'
  });
  console.log('\nADDED item:', ref.id);
  console.log('pairId:', pairId);
}

main().catch(console.error);
