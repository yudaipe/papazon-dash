# papazon-dash

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

カップル向けショッピングリスト共有 Android アプリ。

パートナーとリアルタイムで買い物リストを共有し、購入済みアイテムを FCM プッシュ通知で同期できます。

---

## Features

- **Pairing** — 6-digit invite code to connect with a partner (1-to-1)
- **Shared shopping list** — real-time item sync via Firestore
- **Done toggle** — tap to mark items as purchased (syncs instantly)
- **Push notifications** — FCM alert when a partner adds or completes an item
- **Reminder** — scheduled notification for pending items (Cloud Functions–backed)
- **Persistent login** — Email/Password auth preserves UID and pair across reinstalls

---

## Tech stack

| Layer | Technology |
|---|---|
| Android | Kotlin / Jetpack Compose / Hilt |
| Auth | Firebase Email/Password + Anonymous (fallback) |
| Database | Cloud Firestore |
| Backend | Firebase Cloud Functions (TypeScript) |
| Notifications | Firebase Cloud Messaging (FCM) |

---

## Fork & set up your own instance

This project is MIT-licensed — feel free to fork, modify, and run your own instance.

→ **[SETUP.md](SETUP.md)** — complete step-by-step guide (Firebase project creation through first app launch)

Quick summary:
1. Create a Firebase project and enable Anonymous + Email/Password auth
2. Create a Firestore database and enable Cloud Messaging
3. Upgrade to the Blaze plan (required for Cloud Functions)
4. Download `google-services.json` → place at `android/app/google-services.json`
5. Copy `.firebaserc.example` → `.firebaserc` and set your project ID
6. `firebase deploy --only firestore:rules,firestore:indexes`
7. `cd functions && npm install && npm run build && cd .. && firebase deploy --only functions`
8. `cd android && ./gradlew assembleDebug`

---

## Documentation

| Document | Contents |
|---|---|
| [SETUP.md](SETUP.md) | Step-by-step setup for a new Firebase project |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Codebase layers, components, and data flows |
| [SCHEMA.md](SCHEMA.md) | Firestore collections, fields, and security rules |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [SECURITY.md](SECURITY.md) | Security policy and vulnerability reporting |
| [backlog.md](backlog.md) | Known limitations and planned features |

---

## Known limitations

- **Re-install behavior**: Email/Password auth preserves UID, so existing pair is restored on re-login.  
  Anonymous-only accounts lose their pair on reinstall (legacy behavior, pre-v1.0.2).
- **Item editing not implemented**: Rename an item by deleting and re-adding it. (Planned for v1.1.0)
- **Reminder UI not implemented**: Cloud Functions–side reminder scheduling is ready, but the Android UI to set `reminderAt` is pending.

Details → [backlog.md](backlog.md)

---

## License

[MIT License](LICENSE) — Copyright (c) 2026 yudaipe
