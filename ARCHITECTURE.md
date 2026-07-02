# papazon-dash — Architecture

## Overview

papazon-dash is a couple-facing Android app built on Firebase.  
The codebase follows a layered architecture: **UI → ViewModel → Repository → Firestore/Cloud Functions**.

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer (Compose)                  │
│  SplashScreen  SignInScreen  PairingScreen  MainScreen  │
│  MainSlaveScreen  HistoryScreen  SettingsScreen         │
└────────────────────────┬────────────────────────────────┘
                         │ StateFlow / events
┌────────────────────────▼────────────────────────────────┐
│               ViewModel Layer (Hilt)                    │
│  AppViewModel  SplashViewModel  SignInViewModel          │
│  PairingViewModel  MainViewModel                        │
└────────────────────────┬────────────────────────────────┘
                         │ suspend fns / callbacks
┌────────────────────────▼────────────────────────────────┐
│              Data Layer                                 │
│  FirebaseRepository  (singleton, injected via Hilt)     │
│  Models: User, Item, PairInfo, UserRole                 │
└──────┬──────────────────────────────┬───────────────────┘
       │                              │
┌──────▼──────┐              ┌────────▼─────────────────┐
│  Firebase   │              │  Cloud Functions (TS)     │
│  Auth       │              │  onItemCreated            │
│  Firestore  │              │  onItemCompleted          │
│  FCM        │              │  onPairDeactivated        │
└─────────────┘              │  reminderSchedule         │
                             └──────────────────────────┘
```

---

## Layer Details

### UI Layer — `ui/screens/`

Each screen is a `@Composable` function that observes `StateFlow` from its ViewModel.

| Screen | File | Role |
|---|---|---|
| SplashScreen | `splash/SplashScreen.kt` | Auth state check on cold start |
| SignInScreen | `signin/SignInScreen.kt` | Email/Password sign-in & account creation |
| PairingScreen | `pairing/PairingScreen.kt` | Generate invite code (master) |
| PairingInviteScreen | `pairing/PairingInviteScreen.kt` | Wait for member to join |
| PairingJoinScreen | `pairing/PairingJoinScreen.kt` | Join by 6-digit code (member) |
| MainScreen | `main/MainScreen.kt` | Active shopping list (master view) |
| MainSlaveScreen | `main/MainSlaveScreen.kt` | Active shopping list (member view) |
| HistoryScreen | `history/HistoryScreen.kt` | Purchased item history (last 30) |
| SettingsScreen | `settings/SettingsScreen.kt` | Unpair & sign-out |

Navigation is wired in `ui/navigation/AppNavigation.kt` using Jetpack Navigation Compose.

### ViewModel Layer — `ui/state/` and `ui/screens/**/`

ViewModels inject `FirebaseRepository` via Hilt and expose `StateFlow<T>` to Compose UI.

`AppViewModel` holds app-wide state (current user, paired status). Screen-specific ViewModels handle per-screen logic (e.g. `PairingViewModel` manages invite code generation and join flow).

### Data Layer — `data/`

**`FirebaseRepository`** (`data/repository/FirebaseRepository.kt`) is the single source of truth for all Firebase interactions:

- Email/Password auth (`createAccount`, `signInWithEmail`, `tryAutoSignIn`)
- Anonymous auth (`signIn`) — legacy/fallback path
- Pairing lifecycle (`generateInviteCode`, `joinByCode`, `unpair`)
- Item CRUD (`addItem`, `toggleItem`)
- Realtime listeners (`listenToItems` → `StateFlow<List<Item>>`)
- FCM token refresh

**Models** (`data/model/Models.kt`): `User`, `Item`, `PairInfo`, `UserRole`

### Backend — Cloud Functions (`functions/src/index.ts`)

| Function | Trigger | Action |
|---|---|---|
| `onItemCreated` | Firestore `pairs/{pairId}/items/{itemId}` create | FCM push to partner |
| `onItemCompleted` | Firestore item `status` changes to `done` | FCM push to master |
| `onPairDeactivated` | Firestore pair `active` changes to `false` | Cascade-delete all items |
| `reminderSchedule` | Cloud Scheduler — every 1 min | FCM reminder for items with `reminder_at` in the past |

---

## Dependency Injection

Hilt is set up in `PapazonApp.kt` (`@HiltAndroidApp`).  
`AppModule.kt` (`di/AppModule.kt`) provides `FirebaseAuth`, `FirebaseFirestore`, and `FirebaseMessaging` as singletons.

---

## Key Data Flows

### Sign-In (v1.0.2 — Email/Password)

```
SignInScreen → SignInViewModel.signIn()
  → FirebaseRepository.signInWithEmail()
    → Firebase Auth
    → loadUserProfile() → Firestore users/{uid}
    → _currentUser.value = User(...)
    → if pairId != null → setPaired() → listenToItems()
  → navigate to MainScreen or PairingScreen
```

### Add Item

```
MainScreen (FAB tap) → MainViewModel.addItem(name)
  → FirebaseRepository.addItem()
    → Firestore pairs/{pairId}/items.add(...)
  → Cloud Function onItemCreated fires
    → FCM push to partner
  → Firestore realtime listener updates _items StateFlow
  → UI recomposes automatically
```

### Pairing Flow

```
Master: PairingScreen → generateInviteCode()
  → Firestore pairs/{pairId}.set(inviteCode, active=true, partners=[master_uid])
  → startListeningForMemberJoin() (snapshot listener)

Member: PairingJoinScreen → joinByCode(code)
  → Firestore query pairs where inviteCode == code
  → pairs/{pairId}.update(member_uid, partners += member_uid)
  → Master snapshot listener fires → setPaired() → both users paired
```

---

## Extension Points

### Add a new screen

1. Create `ui/screens/<feature>/<Feature>Screen.kt` (Composable)
2. Create `ui/screens/<feature>/<Feature>ViewModel.kt` (HiltViewModel)
3. Add a route in `ui/navigation/AppNavigation.kt`
4. Add navigation triggers from adjacent screens

### Add a new Cloud Function trigger

1. Add the function to `functions/src/index.ts` using `onDocumentCreated` / `onDocumentUpdated` / `onSchedule`
2. Define any new data types in `functions/src/notifications.ts` or a new module
3. Deploy: `firebase deploy --only functions`

### Add a new Firestore collection

1. Define the schema in `SCHEMA.md`
2. Add security rules in `firestore.rules`
3. Add any required indexes in `firestore.indexes.json` and deploy
4. Add data model in `data/model/Models.kt`
5. Add repository methods in `FirebaseRepository.kt`
