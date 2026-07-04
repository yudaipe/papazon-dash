# papazon-dash — Basic Design Document (v1.0.4)

作成日: 2026-07-04
更新日: 2026-07-04
バージョン: v1.0.4（本書更新は docs only。実装変更なし）
対象リポジトリ: [github.com/yudaipe/papazon-dash](https://github.com/yudaipe/papazon-dash) (public)

本書は papazon-dash の基本設計をまとめた総合設計書です。既存の `ARCHITECTURE.md` / `SCHEMA.md` / `SECURITY.md` / `SETUP.md` に加え、開発過程の設計検討資料（`design_v6.md` / `design_v6.1.md` / `cf_design.md` / `notification_design.md` / `backlog.md`）を統合し、現行実装（v1.0.4）を ground truth として全面的に書き直しました。過去バージョンの検討経緯・不採用案は §12 リリースノートに集約しています。

---

## 目次

1. [概要・利用者ペルソナ](#1-概要利用者ペルソナ)
2. [アーキテクチャ全体像](#2-アーキテクチャ全体像)
3. [データモデル](#3-データモデル)
4. [認証・認可設計](#4-認証認可設計)
5. [画面遷移図](#5-画面遷移図)
6. [機能仕様](#6-機能仕様)
7. [Cloud Functions設計](#7-cloud-functions設計)
8. [通信プロトコル](#8-通信プロトコル)
9. [セキュリティ設計](#9-セキュリティ設計)
10. [形式検証（K19 モデル検査手法の適用）](#10-形式検証k19-モデル検査手法の適用)
11. [運用・拡張性](#11-運用拡張性)
12. [リリースノート](#12-リリースノート)

---

## 1. 概要・利用者ペルソナ

### 1-1. プロダクト概要

papazon-dash は、カップル/家族向けの「買い物リスト共有」Android アプリです。SmartSE K01 講義プロジェクトとして開発し、個人の学習成果物として GitHub 上で公開・保守しています。

- 依頼者（Master）がアイテムを追加すると、実行者（Member）にリアルタイムで通知される
- Member がアイテムを完了にすると、Master にリアルタイムで通知される
- 6桁の招待コードによる 1対1 ペアリングでプライベートな共有空間を実現する

### 1-2. 利用者ペルソナ

| ペルソナ | ロール | 主な利用シーン | 期待する体験 |
|---|---|---|---|
| 依頼する側（例: 妻） | Master | 「牛乳買ってきて」を都度口頭で伝えるのが手間 | アプリに追加するだけで確実に相手に伝わる。完了したかも通知で分かる |
| 依頼される側（例: 夫） | Member | 頼まれたことを忘れる/リストを都度確認するのが面倒 | 通知で気づける。完了操作だけで報告が完了する。労いの一言がもらえると嬉しい |

### 1-3. 想定規模

家族/カップル 2名 1ペアでの利用を前提とした PoC 規模のプロダクトです。大規模同時接続・マルチテナント運用は設計スコープ外です（§11 参照）。

---

## 2. アーキテクチャ全体像

### 2-1. アーキテクチャ簡単版

3層構成の要点のみを示す簡易図です（発表資料 1 slide 版と同等）。

<svg viewBox="0 0 720 220" xmlns="http://www.w3.org/2000/svg" font-family="sans-serif">
  <rect x="20" y="70" width="200" height="90" rx="10" fill="#e8f0fe" stroke="#4285f4" stroke-width="2"/>
  <text x="120" y="95" text-anchor="middle" font-weight="bold" font-size="15">Android App</text>
  <text x="120" y="118" text-anchor="middle" font-size="12">Kotlin</text>
  <text x="120" y="136" text-anchor="middle" font-size="12">Jetpack Compose</text>

  <rect x="260" y="70" width="200" height="90" rx="10" fill="#fef7e0" stroke="#f9ab00" stroke-width="2"/>
  <text x="360" y="95" text-anchor="middle" font-weight="bold" font-size="15">Firebase</text>
  <text x="360" y="118" text-anchor="middle" font-size="12">Auth / Firestore</text>
  <text x="360" y="136" text-anchor="middle" font-size="12">FCM</text>

  <rect x="500" y="70" width="200" height="90" rx="10" fill="#e6f4ea" stroke="#34a853" stroke-width="2"/>
  <text x="600" y="95" text-anchor="middle" font-weight="bold" font-size="15">Cloud Functions</text>
  <text x="600" y="118" text-anchor="middle" font-size="12">TypeScript</text>
  <text x="600" y="136" text-anchor="middle" font-size="12">通知trigger / cascade削除</text>

  <line x1="220" y1="115" x2="260" y2="115" stroke="#333" stroke-width="2" marker-end="url(#arrow)" marker-start="url(#arrow)"/>
  <line x1="460" y1="115" x2="500" y2="115" stroke="#333" stroke-width="2" marker-end="url(#arrow)" marker-start="url(#arrow)"/>
  <defs>
    <marker id="arrow" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8 z" fill="#333"/>
    </marker>
  </defs>
  <text x="360" y="195" text-anchor="middle" font-size="12" fill="#555">3層クライアント・サーバー構成（双方向データフロー）</text>
</svg>

### 2-2. アーキテクチャ詳細版

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer (Compose)                   │
│  SplashScreen  SignInScreen  PairingScreen  MainScreen   │
│  MainSlaveScreen  HistoryScreen  SettingsScreen          │
└────────────────────────┬──────────────────────────────────┘
                          │ StateFlow / events
┌────────────────────────▼──────────────────────────────────┐
│               ViewModel Layer (Hilt)                      │
│  AppViewModel  SplashViewModel  SignInViewModel            │
│  PairingViewModel  MainViewModel                           │
└────────────────────────┬──────────────────────────────────┘
                          │ suspend fns / callbacks
┌────────────────────────▼──────────────────────────────────┐
│              Data Layer                                   │
│  FirebaseRepository（singleton, Hilt注入）                 │
│  Models: User, Item, PairInfo, UserRole                    │
└──────┬──────────────────────────────┬──────────────────────┘
       │                              │
┌──────▼──────┐              ┌────────▼─────────────────┐
│  Firebase   │              │  Cloud Functions (TS)     │
│  Auth       │              │  onItemCreated            │
│  Firestore  │              │  onItemCompleted          │
│  FCM        │              │  onPairDeactivated        │
└─────────────┘              │  reminderSchedule         │
                              └──────────────────────────┘
```

| レイヤー | 内容 |
|---|---|
| UI Layer (`ui/screens/`) | 9画面。各画面は `@Composable` 関数で ViewModel の `StateFlow` を購読 |
| ViewModel Layer (Hilt) | `AppViewModel`（全体状態）+ 画面別 ViewModel |
| Data Layer (`data/repository/FirebaseRepository.kt`) | 全 Firebase 操作の単一窓口（Auth/Firestore CRUD/リアルタイムリスナー/FCM トークン管理） |
| Backend (`functions/src/index.ts`) | Cloud Functions 4種（§7） |

依存性注入は Hilt（`PapazonApp.kt` / `di/AppModule.kt`）で構成し、`FirebaseAuth` / `FirebaseFirestore` / `FirebaseMessaging` をシングルトン提供しています。

### 2-3. 採用構成に至った経緯（設計判断の要点）

開発初期には Firebase Blaze プランの課金リスクを避けるため、Spark プラン固定・Cloud Functions 不使用（WorkManager + ローカル通知によるクライアント完結構成）を検討した時期がありました。最終的には Cloud Functions（Blaze プラン）を採用し、通知の即時性・実装のシンプルさを優先しています。判断根拠と不採用案の詳細は §12 を参照してください。

---

## 3. データモデル

### 3-1. コレクション関係図

<svg viewBox="0 0 640 320" xmlns="http://www.w3.org/2000/svg" font-family="sans-serif">
  <rect x="20" y="20" width="220" height="110" rx="8" fill="#e8f0fe" stroke="#4285f4" stroke-width="2"/>
  <text x="130" y="42" text-anchor="middle" font-weight="bold" font-size="14">users/{uid}</text>
  <text x="32" y="62" font-size="11">uid, displayName, role</text>
  <text x="32" y="78" font-size="11">pairId, fcmToken</text>
  <text x="32" y="94" font-size="11">fcmTokenUpdatedAt</text>
  <text x="32" y="110" font-size="11">updatedAt</text>

  <rect x="300" y="20" width="220" height="130" rx="8" fill="#fef7e0" stroke="#f9ab00" stroke-width="2"/>
  <text x="410" y="42" text-anchor="middle" font-weight="bold" font-size="14">pairs/{pairId}</text>
  <text x="312" y="62" font-size="11">inviteCode(6桁), active</text>
  <text x="312" y="78" font-size="11">master_uid, member_uid</text>
  <text x="312" y="94" font-size="11">master_role, member_role</text>
  <text x="312" y="110" font-size="11">partners[]</text>
  <text x="312" y="126" font-size="11">created_at, joined_at</text>

  <rect x="300" y="200" width="220" height="110" rx="8" fill="#e6f4ea" stroke="#34a853" stroke-width="2"/>
  <text x="410" y="222" text-anchor="middle" font-weight="bold" font-size="14">pairs/{pairId}/items/{itemId}</text>
  <text x="312" y="242" font-size="11">name, status(open|done)</text>
  <text x="312" y="258" font-size="11">createdBy, createdAt</text>
  <text x="312" y="274" font-size="11">completedAt, reminderAt</text>

  <line x1="240" y1="75" x2="300" y2="75" stroke="#333" stroke-width="2" marker-end="url(#arrow2)"/>
  <text x="245" y="70" font-size="10" fill="#555">pairId参照</text>

  <line x1="410" y1="150" x2="410" y2="200" stroke="#333" stroke-width="2" marker-end="url(#arrow2)"/>
  <text x="415" y="178" font-size="10" fill="#555">サブコレクション</text>

  <defs>
    <marker id="arrow2" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8 z" fill="#333"/>
    </marker>
  </defs>
</svg>

### 3-2. `users/{uid}`

Firebase Auth ユーザー1件につき1ドキュメント。`FirebaseRepository.saveUserProfile()` / `refreshFcmToken()` で作成・更新。

| Field | Type | 必須 | 内容 |
|---|---|---|---|
| `uid` | string | ✅ | Firebase Auth UID（ドキュメントIDと同一） |
| `displayName` | string | ✅ | 表示名 |
| `role` | string | ✅ | `"master"` \| `"member"` |
| `pairId` | string \| null | ✅ | 現在のペアID。未ペアリングなら `null` |
| `fcmToken` | string | — | FCM登録トークン |
| `fcmTokenUpdatedAt` | Timestamp | — | トークン更新日時 |
| `updatedAt` | Timestamp | — | プロフィール最終更新（サーバータイムスタンプ） |

セキュリティ: 読み書きは本人（`request.auth.uid == userId`）のみ。

### 3-3. `pairs/{pairId}`

Master が `generateInviteCode()` で作成する、ペア1組につき1ドキュメント。

| Field | Type | 必須 | 内容 |
|---|---|---|---|
| `pairId` | string | ✅ | ドキュメントIDと同一 |
| `inviteCode` | string | ✅ | Member が参加に使う6桁数字コード |
| `master_uid` | string | ✅ | ペア作成者のUID |
| `master_role` / `member_role` | string | ✅ | 常に `"master"` / `"member"` |
| `member_uid` | string | — | Member 参加時に `joinByCode()` が設定 |
| `partners` | string[] | ✅ | アクセス権を持つUID配列。`[master_uid]` → `[master_uid, member_uid]` |
| `active` | boolean | ✅ | `true`=有効。unpair時に `false` |
| `created_at` | Timestamp | ✅ | 作成日時 |
| `joined_at` | Timestamp | — | Member参加日時 |

セキュリティ: `list`=認証済み全員（招待コード照合に必要）、`get`=`partners`内のみ、`create`=master、`update`=master常時可・memberはjoin時の差分検証付きのみ（§4-3）、`delete`=常に拒否（cascade削除はCloud Functionsが担当）。

### 3-4. `pairs/{pairId}/items/{itemId}`

`FirebaseRepository.addItem()` で作成されるショッピングリストアイテム。

| Field | Type | 必須 | 内容 |
|---|---|---|---|
| `name` | string | ✅ | アイテム名 |
| `status` | string | ✅ | `"open"` \| `"done"` |
| `createdBy` | string | ✅ | 追加したユーザーのUID |
| `createdAt` | Timestamp | ✅ | 作成日時 |
| `completedAt` | Timestamp \| null | — | `done` になった日時。`open` に戻すとクリア |
| `reminderAt` | Timestamp \| null | — | リマインダー予定時刻。`reminderSchedule` CFが送信後クリア |

セキュリティ: `read`=`partners`内のみ、`create`/`update`=`partners`内 かつ `pairs.active==true`、`delete`=常に拒否。

### 3-5. Firestore インデックス

`firestore.indexes.json` に3種の複合インデックスを定義（`items` サブコレクション対象）。

| フィールド構成 | 用途 |
|---|---|
| `status ASC, createdAt DESC` | 未完了アイテム一覧（新しい順） |
| `status ASC, completedAt DESC` | 購入履歴（完了が新しい順） |
| `status ASC, reminderAt ASC` | リマインダースキャン（未完了かつ予定時刻が早い順） |

### 3-6. データライフサイクル

```
ユーザー登録        → users/{uid} 作成
Master がペア作成   → pairs/{pairId} 作成（active=true, partners=[master_uid]）
Member が参加       → pairs.member_uid設定・partners=[master_uid, member_uid]
                      users/{master_uid,member_uid}.pairId 設定
アイテム追加/トグル → pairs/{pairId}/items/{itemId} 作成/更新
Unpair              → pairs.active=false → users.pairId=null(両者)
                      → Cloud Function onPairDeactivated が items を cascade削除
```

---

## 4. 認証・認可設計

### 4-1. 認証方式

Email/Password 認証（v1.0.2〜）を採用しています。UID・ペア情報を再インストール後も保持できる点が、初期実装の Anonymous 認証（再インストールで UID 変わり再ペアリングが必要）からの主要な変更点です。

- 新規登録/ログインタブUI（`SignInScreen` / `SignInViewModel`）
- Anonymous → Email/Password への `linkWithCredential()` 移行パス（既存UID保持。失敗時は新規アカウント作成にフォールバック）
- `SplashViewModel.tryAutoSignIn()` による起動時自動サインイン + `pairId` 復元

### 4-2. Firestore Rules v6.4 概要

`firestore.rules` は全ルールに `request.auth != null` を必須化した上で、コレクションごとに以下の認可を行います。

| コレクション | read/get | write/update | create | delete |
|---|---|---|---|---|
| `users/{userId}` | 本人のみ | 本人のみ | — | — |
| `pairs/{pairId}` | `partners` 内のみ（`list` は認証済み全員） | Master常時可／Member join時のみ差分検証付き許可 | Master（自身が`partners`に含まれること） | 常に拒否 |
| `pairs/{pairId}/items/{itemId}` | `partners` 内のみ | `partners` 内 かつ `pairs.active==true` | 同左 | 常に拒否 |

### 4-3. Master exclusive unpair（権限モデル）

ペア解除（`active=false` への更新）は **Master のみ実行可能** という構造的制約を Firestore Rules レベルで保証しています（v1.0.1〜）。

```
allow update: if request.auth != null && (
  // Branch A: master exclusive — masterは常時full update可
  request.auth.uid == resource.data.master_uid
  || (
    // Branch B: member join — joinByCode()が書く3フィールドのみ許可
    resource.data.get('member_uid', null) == null
    && request.resource.data.member_uid == request.auth.uid
    && request.resource.data.master_uid == resource.data.master_uid
    && request.resource.data.active == resource.data.active
    && request.resource.data.diff(resource.data).affectedKeys()
         .hasOnly(['member_uid', 'joined_at', 'partners'])
    && request.resource.data.partners.hasAll(resource.data.partners)
    && request.auth.uid in request.resource.data.partners
    && request.resource.data.partners.size() == resource.data.partners.size() + 1
  )
);
```

Member 側 Android UI でもペア解除ボタンを非表示にし（disabled ではなく非表示 — 「操作できない」ではなく「操作する場所ではない」を明示）、UI層とルール層の両方で二重に制約しています。この設計は K19 モデル検査（§10）の CTL-7 で構造的に検証済みです。

### 4-4. Member join 時の差分検証（v6.4）

Member が招待コードで参加する際の `pairs` 更新は、`joinByCode()` 実装が実際に書き込む3フィールド（`member_uid` / `joined_at` / `partners`）以外の変更を拒否し、`partners` 配列は「既存要素を保持したまま自UIDを1件のみ追加」であることを4段階で検証します（§9-3 参照）。

---

## 5. 画面遷移図

<svg viewBox="0 0 780 420" xmlns="http://www.w3.org/2000/svg" font-family="sans-serif" font-size="11">
  <rect x="320" y="10" width="140" height="40" rx="6" fill="#e8f0fe" stroke="#4285f4" stroke-width="1.5"/>
  <text x="390" y="34" text-anchor="middle">SplashScreen</text>

  <rect x="320" y="90" width="140" height="40" rx="6" fill="#e8f0fe" stroke="#4285f4" stroke-width="1.5"/>
  <text x="390" y="114" text-anchor="middle">SignInScreen</text>

  <rect x="120" y="170" width="150" height="40" rx="6" fill="#fef7e0" stroke="#f9ab00" stroke-width="1.5"/>
  <text x="195" y="194" text-anchor="middle">PairingScreen(Master)</text>

  <rect x="320" y="170" width="150" height="40" rx="6" fill="#fef7e0" stroke="#f9ab00" stroke-width="1.5"/>
  <text x="395" y="194" text-anchor="middle">PairingInviteScreen</text>

  <rect x="520" y="170" width="150" height="40" rx="6" fill="#fef7e0" stroke="#f9ab00" stroke-width="1.5"/>
  <text x="595" y="194" text-anchor="middle">PairingJoinScreen(Member)</text>

  <rect x="120" y="260" width="150" height="40" rx="6" fill="#e6f4ea" stroke="#34a853" stroke-width="1.5"/>
  <text x="195" y="284" text-anchor="middle">MainScreen(Master)</text>

  <rect x="520" y="260" width="150" height="40" rx="6" fill="#e6f4ea" stroke="#34a853" stroke-width="1.5"/>
  <text x="595" y="284" text-anchor="middle">MainSlaveScreen(Member)</text>

  <rect x="120" y="340" width="150" height="40" rx="6" fill="#fce8e6" stroke="#ea4335" stroke-width="1.5"/>
  <text x="195" y="364" text-anchor="middle">HistoryScreen</text>

  <rect x="320" y="340" width="150" height="40" rx="6" fill="#fce8e6" stroke="#ea4335" stroke-width="1.5"/>
  <text x="395" y="364" text-anchor="middle">SettingsScreen</text>

  <line x1="390" y1="50" x2="390" y2="90" stroke="#333" marker-end="url(#a5)"/>
  <line x1="360" y1="130" x2="220" y2="170" stroke="#333" marker-end="url(#a5)"/>
  <line x1="390" y1="130" x2="390" y2="170" stroke="#333" marker-end="url(#a5)"/>
  <line x1="420" y1="130" x2="580" y2="170" stroke="#333" marker-end="url(#a5)"/>
  <line x1="195" y1="210" x2="195" y2="260" stroke="#333" marker-end="url(#a5)"/>
  <line x1="395" y1="210" x2="250" y2="260" stroke="#333" stroke-dasharray="4,3" marker-end="url(#a5)"/>
  <text x="300" y="230" font-size="9" fill="#555">member join検知</text>
  <line x1="595" y1="210" x2="595" y2="260" stroke="#333" marker-end="url(#a5)"/>
  <line x1="195" y1="300" x2="195" y2="340" stroke="#333" marker-end="url(#a5)"/>
  <line x1="230" y1="300" x2="380" y2="340" stroke="#333" marker-end="url(#a5)"/>
  <line x1="490" y1="340" x2="595" y2="300" stroke="#333" stroke-dasharray="4,3" marker-end="url(#a5)"/>
  <text x="500" y="330" font-size="9" fill="#555">unpair→戻る</text>

  <defs>
    <marker id="a5" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8 z" fill="#333"/>
    </marker>
  </defs>
</svg>

| 画面 | ファイル | 役割 |
|---|---|---|
| SplashScreen | `splash/SplashScreen.kt` | 起動時の認証状態チェック・自動サインイン |
| SignInScreen | `signin/SignInScreen.kt` | Email/Password サインイン・新規登録 |
| PairingScreen | `pairing/PairingScreen.kt` | 招待コード発行（Master） |
| PairingInviteScreen | `pairing/PairingInviteScreen.kt` | Member参加待ち（snapshot listenerでjoin検知） |
| PairingJoinScreen | `pairing/PairingJoinScreen.kt` | 6桁コードで参加（Member） |
| MainScreen | `main/MainScreen.kt` | 買い物リスト表示（Master視点） |
| MainSlaveScreen | `main/MainSlaveScreen.kt` | 買い物リスト表示（Member視点・ねぎらいSnackBar表示元） |
| HistoryScreen | `history/HistoryScreen.kt` | 購入済みアイテム履歴（直近30件） |
| SettingsScreen | `settings/SettingsScreen.kt` | ペア解除（Masterのみ）・サインアウト |

画面遷移は `ui/navigation/AppNavigation.kt`（Jetpack Navigation Compose）で管理しています。

---

## 6. 機能仕様

| 機能 | 内容 | 状態 |
|---|---|---|
| ペアリング | 6桁招待コードで Master/Member を1対1接続 | v1.0.0〜 |
| 買い物リスト共有 | Firestore リアルタイム同期でアイテム追加・完了トグル | v1.0.0〜 |
| プッシュ通知 | アイテム追加/完了時に FCM でパートナーへ通知 | v1.0.0〜 |
| リマインダー | Cloud Scheduler（1分間隔）による未完了アイテムの通知 | v1.0.0〜 |
| Email/Password認証 | UID・ペア情報を再インストール後も保持 | v1.0.2〜 |
| Master exclusive unpair | ペア解除は Master のみ実行可（構造的に権限分離） | v1.0.1〜 |
| ねぎらいSnackBar | Member完了操作時に23種のメッセージをランダム表示（Master非表示） | v1.0.4新規 |
| アイテム編集UI | 作成後の名称修正 | 未実装（v1.1.0送り） |

### 6-1. アイテム CRUD

- 追加: `MainScreen` の FAB タップ → `MainViewModel.addItem(name)` → `FirebaseRepository.addItem()` → `pairs/{pairId}/items` に作成
- 完了トグル: `SlaveTaskItem.onToggle` → `status` を `open`⇄`done` に更新、`completedAt` を設定/クリア
- 削除: UIからの明示削除機能はなし。90日相当の長期保持を前提とし、`HistoryScreen` で直近30件を参照

### 6-2. ペアリング

Master が `generateInviteCode()` で6桁コード付き `pairs` ドキュメントを作成し、`PairingInviteScreen` で待機（snapshot listener）。Member が `PairingJoinScreen` でコード入力 → `joinByCode()` がコード一致する `pairs` を検索し `member_uid`/`partners` を更新。Master 側 listener が発火し双方が `MainScreen`/`MainSlaveScreen` へ遷移する。

### 6-3. 通知

`onItemCreated` / `onItemCompleted` / `reminderSchedule` の3種の Cloud Functions がトリガーとなり FCM でパートナーへ push する（詳細は §7・§8）。

### 6-4. ねぎらいSnackBar（v1.0.4新規機能）

Member（実行者）がアイテムを完了報告した際に、23種類のねぎらいメッセージ（戦国調5/丁寧5/カジュアル5/ポジティブ5/ふざけ3）からランダムに1件を選び、約2.5秒間 SnackBar 表示します。Master側には表示されません。

- 実装: `EncouragementMessages.kt`（新規）+ `MainSlaveScreen.kt`（`SlaveTaskItem.onToggle` 内でトグル前の状態が `"open"` の場合のみ発火）
- サーバー側（Firestore スキーマ・Cloud Functions）変更なし。client-side onlyの実装
- `versionCode`: 3→4 / `versionName`: `"1.0.2"`→`"1.0.4"`

---

## 7. Cloud Functions設計

### 7-1. Function一覧

| Function | トリガー | 動作 |
|---|---|---|
| `onItemCreated` | `pairs/{pairId}/items/{itemId}` create | パートナーへ FCM push |
| `onItemCompleted` | item `status` → `done` | Master へ FCM push |
| `onPairDeactivated` | pair `active` true→false | items サブコレクションを cascade削除 |
| `reminderSchedule` | Cloud Scheduler（1分間隔） | `reminderAt` 経過アイテムへリマインダー push |

実装は `functions/src/index.ts`（トリガー定義）+ `functions/src/notifications.ts`（通知本体ロジック）に分離しています。

### 7-2. トリガーシーケンス図（アイテム追加〜通知）

<svg viewBox="0 0 720 300" xmlns="http://www.w3.org/2000/svg" font-family="sans-serif" font-size="11">
  <text x="90" y="24" text-anchor="middle" font-weight="bold">Master端末</text>
  <text x="360" y="24" text-anchor="middle" font-weight="bold">Firestore</text>
  <text x="530" y="24" text-anchor="middle" font-weight="bold">Cloud Functions</text>
  <text x="650" y="24" text-anchor="middle" font-weight="bold">Member端末</text>

  <line x1="90" y1="35" x2="90" y2="280" stroke="#ccc"/>
  <line x1="360" y1="35" x2="360" y2="280" stroke="#ccc"/>
  <line x1="530" y1="35" x2="530" y2="280" stroke="#ccc"/>
  <line x1="650" y1="35" x2="650" y2="280" stroke="#ccc"/>

  <line x1="90" y1="60" x2="360" y2="60" stroke="#333" marker-end="url(#a7)"/>
  <text x="220" y="54" text-anchor="middle" font-size="10">items.add(name,status=open)</text>

  <line x1="360" y1="90" x2="530" y2="90" stroke="#333" marker-end="url(#a7)"/>
  <text x="445" y="84" text-anchor="middle" font-size="10">onItemCreated発火</text>

  <line x1="530" y1="120" x2="360" y2="120" stroke="#333" marker-end="url(#a7)"/>
  <text x="445" y="114" text-anchor="middle" font-size="10">pairs/{pairId}取得(member_uid)</text>

  <line x1="530" y1="150" x2="360" y2="150" stroke="#333" marker-end="url(#a7)"/>
  <text x="445" y="144" text-anchor="middle" font-size="10">users/{member_uid}取得(fcmToken)</text>

  <line x1="530" y1="180" x2="650" y2="180" stroke="#333" marker-end="url(#a7)"/>
  <text x="590" y="174" text-anchor="middle" font-size="10">FCM push送信</text>

  <line x1="650" y1="210" x2="360" y2="210" stroke="#333" stroke-dasharray="4,3" marker-end="url(#a7)"/>
  <text x="505" y="204" text-anchor="middle" font-size="10">snapshotListener自動反映(並行)</text>

  <text x="360" y="250" text-anchor="middle" font-size="10" fill="#555">completed時は onItemCompleted が同様の経路でMasterへ通知</text>
  <text x="360" y="268" text-anchor="middle" font-size="10" fill="#555">unpair時は onPairDeactivated が items を cascade削除</text>

  <defs>
    <marker id="a7" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8 z" fill="#333"/>
    </marker>
  </defs>
</svg>

### 7-3. 各Functionの要点

- **`onItemCreated`**: `pairs/{pairId}/items/{itemId}` の作成をトリガーに、`pairs` ドキュメントから相手（Member）の UID を取得 → `users/{uid}.fcmToken` を取得 → `android.priority: high` でデータメッセージ送信
- **`onItemCompleted`**: `status` フィールドの変化を検知（`before.status !== after.status` かつ `after.status === "done"`）し、Master へ通知
- **`onPairDeactivated`**: `active` が `true→false` に変化したペアの `items` サブコレクションを全削除。K19 モデル検査の CTL-3（§10）で削除完了を検証済み
- **`reminderSchedule`**: `onSchedule("every 1 minutes")` で `status=="open" && reminderAt<=now` のアイテムを `collectionGroup` クエリで検索し、通知送信後 `reminderAt` を `null` にクリア

---

## 8. 通信プロトコル

### 8-1. クライアント⇔Firestore: snapshotListener

`FirebaseRepository.listenToItems()` が `addSnapshotListener` によって永続的なリアルタイム接続を維持し、`StateFlow<List<Item>>` として ViewModel/UI に流し込みます。アプリがフォアグラウンド/バックグラウンドで生存している間はこの経路で即時反映されます。

### 8-2. Cloud Functions⇒端末: FCM

サーバー側の通知起点は Cloud Functions のみです（クライアントから直接 FCM 送信は行いません）。データメッセージ形式（`data:` フィールドのみ、`notification:` フィールド未使用）で送信し、受信側でアプリ独自の通知表示ロジックを実行します。優先度は `high`（即時性が必要な追加/完了通知）を使用します。

### 8-3. Firestore Rules による通信制御

全ての読み書きリクエストは Firestore Rules（§4-2, §9）を通過します。クライアントは Rules を迂回できないため、実質的な認可レイヤーとして機能します。`pairs` の `list` クエリ（招待コード照合）のみ認証済み全ユーザーに開放しており、これは意図的なトレードオフです（§9-3 T-1）。

---

## 9. セキュリティ設計

### 9-1. 認証形態リスク

| 項目 | 内容 |
|---|---|
| Email/Password のリスク | 弱いパスワード・使い回しによるアカウント乗っ取り |
| 現行対策 | Firebase Authentication 標準ポリシー（最小6文字） |
| 残存リスク | パスワード強度ポリシー未設定。ブルートフォース対策は Firebase 側のみ |
| 推奨mitigation | クライアント側バリデーション強化（最小8文字・英数混在）、メール確認の必須化 |

### 9-2. データ露出リスク

| リスク | 現行対策 | 残存リスク |
|---|---|---|
| `pairs` の `list` 権限で全ペアスキャン可能 | `get`（個別取得）は `partners` 内のみに制限（v6.2） | 認証済み悪意ユーザーによる `whereEqualTo` 全件取得は可能 |
| `users` コレクションへの不正アクセス | `request.auth.uid == userId` で本人のみ | UIDからの推測余地はあるが表示名以外の機密情報なし |

### 9-3. 設計上のトレードオフ（Security Trade-offs）

**T-1: `pairs` の `list` 権限を全認証済みユーザーに開放**

招待コード照合クエリ（`joinByCode` の `whereEqualTo`）に `list` 権限が必須なため採用。露出データはショッピングリストのメタデータのみで財務/医療等の機密データを含まず、攻撃には Firebase Authentication の有効なアカウント取得（障壁）と6桁コード（約90万通り）の突破が必要という前提を踏まえ、家族2名規模の運用に対しては招待コード照合の Cloud Functions 移行はオーバーエンジニアリングと判断しました。

**T-2: Member join 時の pairs 更新は差分検証付きで許可（v6.4）**

`joinByCode()` が書き込む3フィールド（`member_uid` / `joined_at` / `partners`）以外の変更を `affectedKeys().hasOnly()` で拒否し、`partners` の `hasAll` / `in` / `size()+1` の4段階検証で「既存要素を保持したまま自UIDを1件のみ追加」であることを強制します（§4-4）。Firestore REST API 経由の `arrayUnion`（`batchWrite` + `appendMissingElements`）はルール評価時にトランスフォーム後の値が含まれないため `DENIED` となり、Android アプリが使う Firebase SDK（gRPC）経由の join のみが正当な経路です。

### 9-4. リスク優先度サマリ

| 優先度 | リスク | 推奨mitigation |
|---|---|---|
| 🔴 高 | 招待コードブルートフォース（試行回数制限なし） | 有効期限フィールド追加・試行回数制限 |
| 🔴 高 | `pairs` list権限による全ペアスキャン | 招待コード照合の Cloud Functions 移行 |
| 🟡 中 | 課金攻撃（Firestore/CF大量呼出） | App Check導入・Firebase Alerts設定 |
| 🟡 中 | 弱いパスワード | クライアント側バリデーション強化 |
| 🟢 低 | Memberによるunpair試行 | 現行rulesで構造的に解消済み（§4-3） |
| 🟢 低 | deactivatedペアへの書込 | `pairs.active==true`条件で解消済み |
| 🟢 低 | 秘匿情報混入（google-services.json等） | `.gitignore` 二重遮断で解消済み。定期スキャン運用あり（cmd_692でハードコード資格情報除去済み） |

---

## 10. 形式検証（K19 モデル検査手法の適用）

SmartSE K19 講義で扱ったモデル検査（NuSMV/CTL）を実プロジェクトに適用し、状態遷移バグを数学的に検出・修正しました。

### 10-1. モデルの発展過程

| バージョン | 追加した状態変数/性質 | 目的 |
|---|---|---|
| `papazon_dash.smv` | 初版9変数・9イベント・6 CTL式 | unpair/listener周りのバグ検出 |
| `papazon_dash_fixed.smv` | 同上（バグ修正反映） | CTL-2/3/6のFALSE→TRUE確認 |
| `papazon_dash_v2.smv` | `ROLE`変数追加、CTL-7追加 | master exclusive unpairのmember edge case検証 |
| `papazon_dash_v3.smv` | `AUTH_TYPE`/`PAIR_ID_PERSISTED`追加、CTL-8/8b/9追加 | Email/Password認証・UID永続化の検証 |

### 10-2. 最終モデル（v3）の状態遷移図

<svg viewBox="0 0 760 300" xmlns="http://www.w3.org/2000/svg" font-family="sans-serif" font-size="11">
  <rect x="20" y="120" width="150" height="50" rx="8" fill="#fce8e6" stroke="#ea4335" stroke-width="1.5"/>
  <text x="95" y="140" text-anchor="middle" font-weight="bold">AUTH=none</text>
  <text x="95" y="156" text-anchor="middle" font-size="9">PAIR_STATE=unpaired</text>

  <rect x="230" y="30" width="170" height="50" rx="8" fill="#e8f0fe" stroke="#4285f4" stroke-width="1.5"/>
  <text x="315" y="50" text-anchor="middle" font-weight="bold">AUTH=signed_in</text>
  <text x="315" y="66" text-anchor="middle" font-size="9">AUTH_TYPE=anonymous</text>

  <rect x="230" y="210" width="170" height="50" rx="8" fill="#e8f0fe" stroke="#4285f4" stroke-width="1.5"/>
  <text x="315" y="230" text-anchor="middle" font-weight="bold">AUTH=signed_in</text>
  <text x="315" y="246" text-anchor="middle" font-size="9">AUTH_TYPE=email_password</text>

  <rect x="500" y="120" width="200" height="60" rx="8" fill="#e6f4ea" stroke="#34a853" stroke-width="1.5"/>
  <text x="600" y="140" text-anchor="middle" font-weight="bold">PAIR_STATE=paired</text>
  <text x="600" y="156" text-anchor="middle" font-size="9">ITEMS_LISTENER=TRUE</text>
  <text x="600" y="170" text-anchor="middle" font-size="9">PAIR_DOC_ACTIVE=TRUE</text>

  <line x1="170" y1="140" x2="230" y2="60" stroke="#333" marker-end="url(#a10)"/>
  <text x="175" y="95" font-size="9">sign_in_anon</text>

  <line x1="170" y1="150" x2="230" y2="230" stroke="#333" marker-end="url(#a10)"/>
  <text x="175" y="200" font-size="9">sign_in_email</text>

  <line x1="400" y1="60" x2="500" y2="130" stroke="#333" marker-end="url(#a10)"/>
  <line x1="400" y1="235" x2="500" y2="160" stroke="#333" marker-end="url(#a10)"/>
  <text x="420" y="120" font-size="9">create_pair /</text>
  <text x="420" y="132" font-size="9">join_pair</text>

  <line x1="600" y1="120" x2="315" y2="80" stroke="#333" stroke-dasharray="4,3" marker-end="url(#a10)"/>
  <text x="480" y="90" font-size="9">master_unpair</text>

  <text x="380" y="290" text-anchor="middle" font-size="10" fill="#555">ROLE={master,member} は paired 状態下で CTL-7(§10-3) の前提として付随管理</text>

  <defs>
    <marker id="a10" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8 z" fill="#333"/>
    </marker>
  </defs>
</svg>

### 10-3. 最終CTL検証結果（v3、10性質）

| # | CTL式（要約） | 結果 |
|---|---|---|
| 1 | unpair後にlistener残らない | TRUE |
| 2 | unpair後にitemsサブコレクション削除済 | TRUE |
| 3 | unpair後にpair doc非アクティブ | TRUE |
| 4 | signOut後listener未登録 | TRUE |
| 5 | paired時listener起動 | TRUE |
| 6 | AUTH=none時にlistener起動しない | TRUE |
| 7 | member はPAIR_DOC_ACTIVEをFALSEにできない（master exclusive unpair） | TRUE |
| 8 | Email/Password認証時、paired状態はPAIR_ID_PERSISTEDを伴う | TRUE |
| 8b | PAIR_ID_PERSISTED かつ未サインインなら再サインインでpaired復帰可能 | TRUE |
| 9 | Anonymous→Email/Password linking後は孤児user doc化しない | TRUE |
| — | 孤児user doc（`ORPHAN_USER_DOC`）が絶対に発生しない | **FALSE（既知の限界）** |

最後の性質のみ既知の limitation として残存しています。Anonymous 認証のまま sign_out → 別UIDで再 sign_in するケースで発生し、v1.1.0 の Google Sign-In 移行によって Anonymous 認証自体を廃止することで根本解決予定です（Email/Password 認証への完全移行後は発生しない経路であることは性質8/8b/9で確認済み）。

### 10-4. 効果

- 反例（counter-example）trace の自動生成により、実装バグ4件を発見
- 「バグを直す」ではなく「バグが起きない構造にする」設計思想を実践（Android `unpair()` へのリスナー解除処理追加、Cloud Functions `onPairDeactivated` の cascade削除実装、Firestore Rules による master exclusive unpair の構造的強制）
- 公開直前のセキュリティレビューと合わせ、外部レビュー PASS 到達に寄与

---

## 11. 運用・拡張性

### 11-1. 配布・監視

- **配布**: GitHub Releases（public リポジトリ `yudaipe/papazon-dash`）。APK は unsigned release ビルド（keystore未設定のため、開発者向け `adb install` 等での導入を前提）
- **テスト**: bats によるスクリプト検証 + adb 実機E2E（2台構成、Master/Member双方の役割検証）
- **監視**: Firebase Console（Authentication / Firestore 使用量）。App Check 等の課金攻撃対策は将来検討事項（§9-4）
- **既知の運用上の注意**: アプリ削除（reinstall）前に明示的な unpair を実施することを推奨（Firestore側にペア情報が残存するケースがあるため）

### 11-2. 拡張ポイント

- **新規画面追加**: `ui/screens/<feature>/` にComposable+ViewModelを追加 → `AppNavigation.kt` にルート追加
- **新規Cloud Function追加**: `functions/src/index.ts` に `onDocumentCreated`/`onDocumentUpdated`/`onSchedule` トリガーを追加
- **新規Firestoreコレクション追加**: `SCHEMA.md` に定義 → `firestore.rules` にルール追加 → 必要なら `firestore.indexes.json` 追加 → `data/model/Models.kt` にモデル追加 → `FirebaseRepository.kt` にメソッド追加

### 11-3. 今後の計画（v1.1.0以降）

- アイテム編集UI（作成後の名称修正）
- Google Sign-In移行によるUID安定化（孤児user doc問題の根本解決）
- App Check導入・招待コード有効期限追加（課金攻撃/ブルートフォース対策）
- 表示名変更UI（設定画面からの displayName 変更、pair相手UIへの即時反映）
- アプリ内アップデート機構（GitHub Releases APIで最新tag取得→バージョン比較→更新確認ボタン）

### 11-4. 適用範囲外（スコープ外）

大規模同時接続、マルチテナント運用、決済/課金機能、家族2名規模を超えるグループ利用は、本設計のスコープ外です。これらを要件とする場合は Firestore Rules の `partners` 前提設計・招待コード方式・Spark/Blaze前提のコスト試算を再検討する必要があります。

---

## 12. リリースノート

### v1.0.0（2026-06-28）— MVP完遂

ペアリング・買い物リスト共有・FCM通知の基本機能。19フェーズ完遂、機能4（アイテム編集UI）のみ v1.1.0 送りでSKIP。APK SHA256: `bf2220257f6a53a079c28092e0c2d3e7c5ff32b9d259271c67c8d864aabbf021`。

### v1.0.1（2026-07-02）— Master exclusive unpair + CF cascade削除

`onPairDeactivated` デプロイ、CTL-2/3/6 実機実証、master exclusive unpair 構造実装。Minor M1（app→CF E2E未実証）/ M2（CTL-6 member edge case）を完全解消。APK SHA256（unsigned）: `7fae81730dff0a39a63b8ff0483fb988cf34fb070156ecf2ad81ca86b8cd9de4`。

### v1.0.2（2026-07-02）— Email/Password認証実装

Anonymous → Email/Password への完全移行。`linkWithCredential()` によるUID保持、`pairId` のFirestore永続化、`SplashViewModel.tryAutoSignIn()` による自動サインイン。SMV v3（10性質）全てNuSMV検証PASS。実機2台E2E全11ステップ通過。

### v1.0.3（2026-07-02）— 外部レビュー対応

外部レビュー指摘3点（P0/P1/P2）に対応。Firestore Rules v6.4 を本番デプロイ（member join の差分検証強化）、SECURITY.md に設計トレードオフを文書化、`reminderAt` の命名を camelCase に統一。実機E2E 6テスト（DENY確認3件含む）で検証。

### v1.0.4（2026-07-04）— ねぎらいSnackBar機能追加

Member完了操作時、23種メッセージのランダム表示機能を追加。client-side onlyの小feature（サーバー側変更なし）。

### 開発過程で検討し不採用とした設計案（参考）

開発初期（2026-06-17〜06-20）には、Firebase の課金リスクを完全に排除する目的で以下の代替設計を検討した経緯があります。最終的にはユーザー体験（通知の即時性）と実装のシンプルさを優先し、Cloud Functions（Blaze プラン + FCM Admin SDK）を採用しました。

| 検討案 | 内容 | 不採用理由 |
|---|---|---|
| Spark固定・CF回避（`cf_design.md`） | Cloud Functions を一切使わず、WorkManager PeriodicWork（最短15分間隔）+ ローカル通知で代替 | 通知精度が±15〜30分に劣化。アプリ完全終了時の即時通知ができない |
| クライアント完結型通知設計（`notification_design.md`） | FCM を受信インフラのみとして使い、送信経路は Firestore snapshotListener + AlarmManager で代替 | 同上。CF採用によりBudget Alert等の課金管理（§9-4）で十分にリスク制御可能と判断 |
| セルフホスト構成（Pi5 + Cloudflare Tunnel、design v4） | 自前サーバーでの通知トリガー管理 | Pi5停電・ネット切断でサービス停止するリスク、グループ参加者に対するインフラ知識要求が高い |

これらの検討過程で整理した「課金リスク管理（Budget Alert・Firestore APIクォータ）」の考え方は、現行の Blaze プラン運用における監視方針（§11-1）の基礎になっています。

---

## 参考ドキュメント

- `ARCHITECTURE.md` — レイヤー構造・データフロー詳細
- `SCHEMA.md` — Firestoreスキーマ詳細
- `SECURITY.md` — セキュリティリスク登録簿・設計判断（Trade-offs）
- `SETUP.md` — Firebaseプロジェクトのセットアップ手順
- `outbox/20260704+papazon_dash_architecture_1slide.md` — 発表資料用アーキテクチャ1枚スライド
