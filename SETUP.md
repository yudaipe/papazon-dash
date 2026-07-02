# papazon-dash — Setup Guide

This guide walks you through setting up your own Firebase backend and building the Android app from scratch.  
It is written for first-time contributors and AI-assisted development (Claude, GPT, etc.) — every step is explicit.

---

## Prerequisites

Install the following tools before starting:

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Hedgehog (2023.1) or later | Required to build the Android app |
| Java | 17 | Bundled with recent Android Studio |
| Node.js | 18 or later | Required for Cloud Functions |
| npm | Bundled with Node.js | — |
| Firebase CLI | Latest | `npm install -g firebase-tools` |

Verify your setup:

```bash
node --version      # v18.x or later
firebase --version  # 13.x or later
java -version       # openjdk 17
```

---

## Step 1: Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/) and click **Add project**
2. Choose a project name (e.g. `my-papazon-dash`) and follow the wizard
3. Note your **Project ID** — you will use it throughout this guide

### 1-a: Enable Authentication

In Firebase Console → **Authentication** → **Sign-in method**:

1. Enable **Anonymous** sign-in (legacy path — still used in the codebase)
2. Enable **Email/Password** sign-in (required for v1.0.2 and later)

Both must be enabled. The app starts with Anonymous auth for the pairing flow and upgrades to Email/Password on first sign-in.

### 1-b: Create Firestore Database

In Firebase Console → **Firestore Database** → **Create database**:

- Choose **Production mode**
- Select region: `asia-northeast1` (Tokyo) — recommended for low latency in Japan

### 1-c: Enable Cloud Messaging

In Firebase Console → **Cloud Messaging** → verify it is enabled (it usually is by default).  
This is required for FCM push notifications.

### 1-d: Upgrade to Blaze Plan

In Firebase Console → **Usage and billing** → **Modify plan** → **Blaze (pay as you go)**

> Cloud Functions deployment requires the Blaze plan. You will not be charged unless usage exceeds the free tier.

---

## Step 2: Register the Android App and Get `google-services.json`

1. In Firebase Console → **Project settings** → **Your apps** → **Add app** → choose **Android**
2. Enter the package name: `com.smartse.papazon_dash`
3. (Optional) Enter a nickname and SHA-1 fingerprint, then click **Register app**
4. Download `google-services.json`
5. Place the file at:

```
android/app/google-services.json
```

> **Important**: `google-services.json` is listed in `.gitignore`. Never commit this file — it contains your Firebase project credentials.

You can check the expected file format in `android/app/google-services.json.example`.

---

## Step 3: Configure `.firebaserc`

```bash
cp .firebaserc.example .firebaserc
```

Edit `.firebaserc` and replace `<YOUR_FIREBASE_PROJECT_ID>` with your actual project ID:

```json
{
  "projects": {
    "default": "my-papazon-dash"
  }
}
```

> **Important**: `.firebaserc` is listed in `.gitignore`. Never commit it.

Log in and select your project:

```bash
firebase login
firebase use my-papazon-dash
```

---

## Step 4: Deploy Firestore Rules and Indexes

```bash
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

If this is the first deploy, you may be prompted to confirm enabling the Firestore API.  
See [SCHEMA.md](SCHEMA.md) for a full description of the security rules.

---

## Step 5: Deploy Cloud Functions

```bash
cd functions
npm install
npm run build
cd ..
firebase deploy --only functions
```

Deployed functions:
- `onItemCreated` — sends FCM push when a partner adds an item
- `onItemCompleted` — sends FCM push when an item is marked done
- `onPairDeactivated` — cascade-deletes items when a pair is dissolved
- `reminderSchedule` — scheduled reminder notifications (every 1 min)

---

## Step 6: Build the Android App

### Option A: Command line

```bash
cd android
./gradlew assembleDebug
```

The APK is output to:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device:

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### Option B: Android Studio

1. Open Android Studio
2. **File** → **Open** → select the `android/` folder (not the repo root)
3. Wait for Gradle sync to complete
4. Click **Run** (▶) to install on a connected device or emulator

---

## Step 7: First Launch

On first launch the app shows the Sign-In screen:

1. One user selects **User A** role and enters email + password → **アカウント作成**
2. The same or another user selects **User B** role and creates a second account
3. The master user taps **招待コードを作成** and shares the 6-digit code
4. The member user taps **招待コードで参加** and enters the code
5. Both users are now paired and the shared shopping list opens

---

## Step 8: Admin Scripts (optional)

Scripts in `functions/src/` (e.g. `create_item.js`, `dump_firestore.js`) are developer utilities.  
Set `FIREBASE_PROJECT_ID` before running:

```bash
export FIREBASE_PROJECT_ID=my-papazon-dash
node functions/src/create_item.js
```

---

## Troubleshooting

### `google-services.json` not found

```
File google-services.json is missing from the /app directory.
```

→ Make sure `android/app/google-services.json` exists. Placing the `.example` file is not enough — you need the real file from Firebase Console.

---

### Cloud Functions deploy fails

```
Error: Firebase project is on the Spark plan. Upgrade to Blaze to use Cloud Functions.
```

→ Upgrade your Firebase project to the **Blaze** plan in Firebase Console.

```
Error: HTTP Error: 403, The caller does not have permission
```

→ Run `firebase login --reauth` and try again.

---

### `PERMISSION_DENIED` when accessing Firestore

```
PERMISSION_DENIED: Missing or insufficient permissions.
```

→ Check that `firebase deploy --only firestore:rules` completed successfully.  
→ Check that the user is authenticated (not `null`) in the app.

---

### Email/Password sign-in fails immediately

```
FirebaseAuthException: The sign in method is disabled for this Firebase project.
```

→ Go to Firebase Console → **Authentication** → **Sign-in method** and enable **Email/Password**.

---

### Anonymous sign-in fails

```
FirebaseAuthException: This operation is not allowed.
```

→ Go to Firebase Console → **Authentication** → **Sign-in method** and enable **Anonymous**.

---

### Admin script shows `YOUR_FIREBASE_PROJECT_ID` error

→ Run `export FIREBASE_PROJECT_ID=<your-project-id>` in the same shell session before running the script.

---

### Gradle sync fails in Android Studio

→ Make sure `android/app/google-services.json` is present before syncing.  
→ Check that Java 17 is selected: **File** → **Project Structure** → **SDK Location** → **JDK location**.

---

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — codebase structure and data flows
- [SCHEMA.md](SCHEMA.md) — Firestore collections, fields, and security rules
- [SECURITY.md](SECURITY.md) — security policy
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [backlog.md](backlog.md) — known limitations and planned features
