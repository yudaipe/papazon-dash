# papazon-dash — Firestore Schema (v1.0.2)

This document describes the Firestore data model as of v1.0.2 (Email/Password Auth integration).

---

## Collection: `users`

Path: `users/{uid}`

Each document corresponds to one Firebase Auth user. Created/updated in `FirebaseRepository.saveUserProfile()` and `refreshFcmToken()`.

| Field | Type | Required | Description |
|---|---|---|---|
| `uid` | `string` | ✅ | Firebase Auth UID (same as document ID) |
| `displayName` | `string` | ✅ | User's display name (e.g. "はなこ") |
| `role` | `string` | ✅ | `"master"` or `"member"` |
| `pairId` | `string \| null` | ✅ | Active pair document ID. `null` if not paired |
| `fcmToken` | `string` | — | FCM registration token for push notifications |
| `fcmTokenUpdatedAt` | `Timestamp` | — | When `fcmToken` was last refreshed |
| `updatedAt` | `Timestamp` | — | Last profile update (server timestamp) |

**Security**: Read and write allowed only by the document owner (`request.auth.uid == userId`).

### Roles

| Value | Meaning |
|---|---|
| `"master"` | The user who created the pair (sent the invite) |
| `"member"` | The user who joined via invite code |

---

## Collection: `pairs`

Path: `pairs/{pairId}`

One document per active or deactivated pair. Created by the master user in `generateInviteCode()`.

| Field | Type | Required | Description |
|---|---|---|---|
| `pairId` | `string` | ✅ | Pair document ID (same as document ID) |
| `inviteCode` | `string` | ✅ | 6-digit numeric code used by the member to join |
| `master_uid` | `string` | ✅ | UID of the master (pair creator) |
| `master_role` | `string` | ✅ | Always `"master"` |
| `member_role` | `string` | ✅ | Always `"member"` |
| `member_uid` | `string` | — | UID of the member. Set when member calls `joinByCode()` |
| `partners` | `string[]` | ✅ | Array of UIDs with access. Starts as `[master_uid]`, grows to `[master_uid, member_uid]` |
| `active` | `boolean` | ✅ | `true` while pair is live. Set to `false` on unpair |
| `created_at` | `Timestamp` | ✅ | When the pair was created (server timestamp) |
| `joined_at` | `Timestamp` | — | When the member joined (server timestamp) |

**Security**:
- `list`: Any authenticated user (needed for invite code lookup query)
- `get`: Only users whose UID is in `partners`
- `create`: Master only (own UID must be in `partners`)
- `update`: Master can always update; member can update only to set `member_uid` (join flow)
- `delete`: Always denied — cascade deletion is handled by the `onPairDeactivated` Cloud Function

---

## Sub-collection: `pairs/{pairId}/items`

Path: `pairs/{pairId}/items/{itemId}`

Shopping list items for a specific pair. Created in `FirebaseRepository.addItem()`.

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | ✅ | Item name (e.g. "牛乳") |
| `status` | `string` | ✅ | `"open"` (active) or `"done"` (purchased) |
| `createdBy` | `string` | ✅ | UID of the user who added this item |
| `createdAt` | `Timestamp` | ✅ | Creation time (server timestamp) |
| `completedAt` | `Timestamp \| null` | — | Set when status changes to `"done"`. Cleared when toggled back to `"open"` |
| `reminderAt` | `Timestamp \| null` | — | Android-side scheduled reminder time (UI not yet implemented in v1.0.2) |
| `reminder_at` | `Timestamp \| null` | — | Cloud Function–side reminder time. Cleared after reminder FCM is sent |

> **Note**: `reminderAt` (camelCase) is the Android client field; `reminder_at` (snake_case) is the Cloud Functions field. These will be unified in a future version.

**Security**:
- `read`: Only users in `pairs/{pairId}.partners`
- `create`, `update`: Users in `partners` AND `pairs/{pairId}.active == true`
- `delete`: Always denied — cascade deletion is handled by the `onPairDeactivated` Cloud Function

---

## Firestore Indexes (`firestore.indexes.json`)

Three composite indexes are defined for the `items` sub-collection:

| Collection | Fields | Used by |
|---|---|---|
| `items` | `status ASC`, `createdAt DESC` | Active items list (open items, newest first) |
| `items` | `status ASC`, `completedAt DESC` | Purchase history (done items, most recently completed first) |
| `items` | `status ASC`, `reminderAt ASC` | Reminder scan (open items with earliest reminder time) |

Deploy with:

```bash
firebase deploy --only firestore:indexes
```

---

## Data Lifecycle Summary

```
User registers
  → users/{uid} created

Master creates pair
  → pairs/{pairId} created (active=true, partners=[master_uid])

Member joins
  → pairs/{pairId}.member_uid set, partners=[master_uid, member_uid]
  → users/{master_uid}.pairId = pairId
  → users/{member_uid}.pairId = pairId

Items added / toggled
  → pairs/{pairId}/items/{itemId} created / updated

Unpair
  → pairs/{pairId}.active = false
  → users/{uid}.pairId = null (both users)
  → Cloud Function onPairDeactivated deletes all items in sub-collection
```
