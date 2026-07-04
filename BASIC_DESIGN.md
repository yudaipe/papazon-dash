# papazon-dash — Basic Design Document (v1.0.4)

作成日: 2026-07-04
バージョン: v1.0.4
対象リポジトリ: [github.com/yudaipe/papazon-dash](https://github.com/yudaipe/papazon-dash) (public)

本書は papazon-dash の基本設計をまとめたものです。既存の `ARCHITECTURE.md` / `SCHEMA.md` / `SECURITY.md` / `SETUP.md` を統合し、SmartSE K19 講義（モデル検査）の実プロジェクト適用結果を含めた総合設計書として作成しました。

---

## 1. 目的

papazon-dash は、カップル/家族向けの買い物リスト共有 Android アプリです。

- 依頼者（Master）がアイテムを追加すると、実行者（Member）にリアルタイムで通知される
- Member がアイテムを完了にすると、Master にリアルタイムで通知される
- 1対1のペアリングによってプライベートな共有空間を実現する

個人の学習成果物として GitHub 上で公開・保守しています。

---

## 2. 構成（アーキテクチャ）

3層クライアント・サーバー構成です。

```
[Android App] ⇄ [Firebase (Auth / Firestore / FCM)] ⇄ [Cloud Functions (TypeScript)]
   Kotlin                                                  cascade削除・通知trigger
   Jetpack Compose
```

レイヤー構造は UI → ViewModel → Repository → Firestore/Cloud Functions の順に責務分離しています。

| レイヤー | 内容 |
|---|---|
| UI Layer (`ui/screens/`) | 9画面（後述）。各画面は `@Composable` 関数で ViewModel の `StateFlow` を購読 |
| ViewModel Layer (Hilt) | `AppViewModel`（全体状態）+ 画面別 ViewModel |
| Data Layer (`data/repository/FirebaseRepository.kt`) | 全 Firebase 操作の単一窓口（Auth/Firestore CRUD/リアルタイムリスナー/FCM トークン管理） |
| Backend (`functions/src/index.ts`) | Cloud Functions 4種（後述） |

依存性注入は Hilt（`PapazonApp.kt` / `di/AppModule.kt`）で構成し、`FirebaseAuth` / `FirebaseFirestore` / `FirebaseMessaging` をシングルトン提供しています。

詳細は `ARCHITECTURE.md` を参照。

---

## 3. 機能

| 機能 | 内容 | 状態 |
|---|---|---|
| ペアリング | 6桁招待コードで Master/Member を1対1接続 | v1.0.0〜 |
| 買い物リスト共有 | Firestore リアルタイム同期でアイテム追加・完了トグル | v1.0.0〜 |
| プッシュ通知 | アイテム追加/完了時に FCM でパートナーへ通知 | v1.0.0〜 |
| リマインダー | Cloud Scheduler(1分間隔)による未完了アイテムの通知 | v1.0.0〜 |
| Email/Password認証 | UID・ペア情報を再インストール後も保持 | v1.0.2〜 |
| Master exclusive unpair | ペア解除は Master のみ実行可（構造的に権限分離） | v1.0.1〜 |
| ねぎらいSnackBar | Member完了操作時に23種のメッセージをランダム表示（Master非表示） | **v1.0.4新規** |
| アイテム編集UI | 作成後の名称修正 | 未実装（v1.1.0送り） |

### v1.0.4 新規機能: ねぎらいSnackBar

Member（実行者）がアイテムを完了報告した際に、23種類のねぎらいメッセージ（戦国調5/丁寧5/カジュアル5/ポジティブ5/ふざけ3）からランダムに1件を選び、約2.5秒間 SnackBar 表示します。Master側には表示されません。

- 実装: `EncouragementMessages.kt`（新規）+ `MainSlaveScreen.kt`（`SlaveTaskItem.onToggle` 内でトグル前の状態が `"open"` の場合のみ発火）
- サーバー側（Firestore スキーマ・Cloud Functions）変更なし。client-side onlyの実装
- `versionCode`: 3→4 / `versionName`: `"1.0.2"`→`"1.0.4"`

---

## 4. 画面

| 画面 | ファイル | 役割 |
|---|---|---|
| SplashScreen | `splash/SplashScreen.kt` | 起動時の認証状態チェック・自動サインイン |
| SignInScreen | `signin/SignInScreen.kt` | Email/Password サインイン・新規登録 |
| PairingScreen | `pairing/PairingScreen.kt` | 招待コード発行（Master） |
| PairingInviteScreen | `pairing/PairingInviteScreen.kt` | Member参加待ち |
| PairingJoinScreen | `pairing/PairingJoinScreen.kt` | 6桁コードで参加（Member） |
| MainScreen | `main/MainScreen.kt` | 買い物リスト表示（Master視点） |
| MainSlaveScreen | `main/MainSlaveScreen.kt` | 買い物リスト表示（Member視点・ねぎらいSnackBar表示元） |
| HistoryScreen | `history/HistoryScreen.kt` | 購入済みアイテム履歴（直近30件） |
| SettingsScreen | `settings/SettingsScreen.kt` | ペア解除・サインアウト |

画面遷移は `ui/navigation/AppNavigation.kt`（Jetpack Navigation Compose）で管理しています。

---

## 5. データ（Firestore スキーマ概要）

詳細は `SCHEMA.md` を参照。以下は要約です。

### `users/{uid}`

`uid` / `displayName` / `role`(master|member) / `pairId` / `fcmToken` / `fcmTokenUpdatedAt` / `updatedAt`

### `pairs/{pairId}`

`pairId` / `inviteCode`(6桁) / `master_uid` / `member_uid` / `partners`(UID配列) / `active`(bool) / `created_at` / `joined_at`

### `pairs/{pairId}/items/{itemId}`

`name` / `status`(open|done) / `createdBy` / `createdAt` / `completedAt` / `reminderAt`

### データライフサイクル

```
ユーザー登録 → users/{uid}作成
Master がペア作成 → pairs/{pairId}作成(active=true)
Member が参加 → partners追加・users両方にpairId設定
アイテム追加/トグル → items サブコレクション更新
Unpair → pairs.active=false → CF onPairDeactivated が items を cascade削除
```

Firestore インデックスは3種（`status+createdAt` / `status+completedAt` / `status+reminderAt`）を `firestore.indexes.json` に定義しています。

---

## 6. 通信（Cloud Functions / FCM）

| Function | トリガー | 動作 |
|---|---|---|
| `onItemCreated` | `pairs/{pairId}/items/{itemId}` create | パートナーへ FCM push |
| `onItemCompleted` | item `status` → `done` | Master へ FCM push |
| `onPairDeactivated` | pair `active` true→false | items サブコレクションを cascade削除 |
| `reminderSchedule` | Cloud Scheduler（1分間隔） | `reminderAt` 経過アイテムへリマインダー push |

クライアント⇔Firestore間はスナップショットリスナー（`listenToItems` → `StateFlow<List<Item>>`）によるリアルタイム同期で、UIはFirestore更新を検知して自動再描画します。

---

## 7. セキュリティ設計

詳細は `SECURITY.md`（Security Risk Register v1.3）を参照。要点は以下の通りです。

- **Firestore Rules v6.4**: `request.auth != null` を全ルールに必須化。`pairs` の `get` は `partners` 内ユーザーのみ、`update` は Master優先+Member参加時のみ多段検証（`affectedKeys().hasOnly()` + `partners` の `hasAll`/`in`/`size()+1` 検証）
- **Master exclusive unpair**: ペア解除操作は Master の Firestore Rules レベルで構造的に権限分離（Member はUI上もボタン非表示）
- **既知の残存リスク（意図的トレードオフ）**:
  - `pairs` コレクションの `list` 権限を認証済み全ユーザーに開放（招待コード照合クエリのため必要。家族2名規模での運用を前提にオーバーエンジニアリング回避と判断）
  - 招待コードに有効期限・試行回数制限なし（6桁・約90万通り、認証済みアカウント必須という障壁あり）
- **秘匿情報管理**: `google-services.json` / `.firebaserc` は `.gitignore` 二重遮断。ハードコード資格情報の定期スキャン運用あり（cmd_692でConstants.kt平文default除去済み）

---

## 8. K19「モデル検査」手法の適用（形式検証）

SmartSE K19講義で扱ったモデル検査（NuSMV/CTL）を実プロジェクトに適用し、状態遷移バグを数学的に検出・修正しました（cmd_663）。

### 8.1 手法

- **状態変数（9変数）**: `AUTH` / `PAIR_STATE` / `ITEMS_LISTENER` / `JOIN_LISTENER` / `FCM_SYNCED` / `PREV_SIGN_IN` / `ORPHAN_USER_DOC` / `PAIR_DOC_ACTIVE` / `ITEMS_SUBCOL_DATA`
- **イベント（9種）**: `sign_in` / `sign_out` / `reinstall` / `create_pair` / `member_joined` / `join_pair` / `unpair` / `add_item` / `noop`
- SMVモデルを `qa/papazon_dash.smv` に定義し、NuSMV 2.6.0 で6本の CTL 式を検証

### 8.2 検証結果

| CTL式 | 修正前 | 修正後 |
|---|---|---|
| unpair後にlistener残らない | FALSE（バグ） | **TRUE**（修正済） |
| unpair後にitemsサブコレクション削除済 | FALSE（バグ） | 主要経路でTRUE（reinstall特殊経路は別問題として文書化） |
| unpair後にpair doc非アクティブ | FALSE（バグ） | 主要経路でTRUE（同上） |
| signOut後listener未登録 | TRUE | TRUE |
| paired時listener起動 | TRUE | TRUE |
| 孤児user doc非存在 | FALSE | v1.1.0（Google Sign-In移行）で根本解決予定 |

### 8.3 効果

- 反例（counter-example）trace の自動生成により、実装バグ4件を発見
- 「バグを直す」ではなく「バグが起きない構造にする」設計思想を実践（Android `unpair()` へのリスナー解除処理追加、Cloud Functions `onPairDeactivated` の cascade削除実装）
- 公開直前のセキュリティレビューと合わせ、外部レビュー PASS 到達に寄与

---

## 9. 運用

- **配布**: GitHub Releases（public リポジトリ `yudaipe/papazon-dash`）。APK は unsigned release ビルド（keystore未設定のため、開発者向け `adb install` 等での導入を前提）
- **テスト**: bats によるスクリプト検証 + adb 実機E2E（2台構成、Master/Member双方の役割検証）
- **監視**: Firebase Console（Authentication / Firestore 使用量）。App Check 等の課金攻撃対策は将来検討事項として `SECURITY.md` に記録
- **既知の運用上の注意**: アプリ削除（reinstall）前に明示的な unpair を実施することを推奨（Firestore側にペア情報が残存するケースがあるため）

---

## 10. 拡張

`ARCHITECTURE.md` の Extension Points を踏襲します。

- **新規画面追加**: `ui/screens/<feature>/` にComposable+ViewModelを追加 → `AppNavigation.kt` にルート追加
- **新規Cloud Function追加**: `functions/src/index.ts` に `onDocumentCreated`/`onDocumentUpdated`/`onSchedule` トリガーを追加
- **新規Firestoreコレクション追加**: `SCHEMA.md` に定義 → `firestore.rules` にルール追加 → 必要なら `firestore.indexes.json` 追加 → `data/model/Models.kt` にモデル追加 → `FirebaseRepository.kt` にメソッド追加

### 今後の計画（v1.1.0以降）

- アイテム編集UI（作成後の名称修正）
- Google Sign-In移行によるUID安定化（孤児user doc問題の根本解決）
- App Check導入・招待コード有効期限追加（課金攻撃/ブルートフォース対策）

---

## 11. リリースノート

| バージョン | 日付 | 概要 |
|---|---|---|
| v1.0.0 | 2026-06-28 | MVP完遂。ペアリング・買い物リスト共有・FCM通知の基本機能 |
| v1.0.1 | 2026-07-02 | Master exclusive unpair + Cloud Functions cascade削除実装（K19 CTL-2/3/6対応） |
| v1.0.2 | 2026-07-02 | Email/Password認証実装。再インストール後もUID・ペア情報を保持 |
| v1.0.3 | 2026-07-02 | 外部レビュー対応。Firestore Rules v6.4（差分検証強化）、SECURITY.md設計判断文書化 |
| **v1.0.4** | **2026-07-04** | **ねぎらいSnackBar機能追加（Member完了操作時、23種メッセージのランダム表示）。client-side onlyの小feature** |

---

## 参考ドキュメント

- `ARCHITECTURE.md` — レイヤー構造・データフロー詳細
- `SCHEMA.md` — Firestoreスキーマ詳細
- `SECURITY.md` — セキュリティリスク登録簿・設計判断（Trade-offs）
- `SETUP.md` — Firebaseプロジェクトのセットアップ手順
- `outbox/20260704+papazon_dash_architecture_1slide.md` — 発表資料用アーキテクチャ1枚スライド
