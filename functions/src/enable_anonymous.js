const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

process.env.GCLOUD_PROJECT = process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID';
try {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'YOUR_FIREBASE_PROJECT_ID'
  });
  const auth = getAuth();
  console.log("Auth SDK initialized.");

  // 現在の設定を取得
  auth.projectConfigManager().getProjectConfig()
  .then(config => {
    console.log("Current config:", JSON.stringify(config, null, 2));
  })
  .catch(err => {
    console.error("Error getting project config:", err);
  });
} catch (e) {
  console.error("Initialization error:", e);
}
