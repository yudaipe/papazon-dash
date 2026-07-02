# Security Risk Register — papazon-dash

作成日: 2026-07-02
バージョン: v1.0 (cmd_667)

本ドキュメントは papazon-dash のセキュリティリスクを一覧化し、
現行対策・残存リスク・推奨 mitigation を記載します。

---

## 1. 認証形態リスク

### 1-1. Anonymous 認証

| 項目 | 内容 |
|------|------|
| **リスク** | 未認証ユーザーが自動的に匿名ユーザーとして Firestore に書き込む可能性 |
| **現行対策** | `request.auth != null` を全ルールに必須化。匿名 UID でも認証済みと見なされる点に留意 |
| **残存リスク** | 匿名ユーザーが大量のペア作成 → Firestore 書込上限・課金増加 |
| **推奨 mitigation** | App Check (Play Integrity / DeviceCheck) 導入。匿名ユーザーのペア作成をレート制限する Cloud Functions を追加 |

### 1-2. Email/Password 認証

| 項目 | 内容 |
|------|------|
| **リスク** | 弱いパスワード・使い回しによるアカウント乗っ取り |
| **現行対策** | Firebase Authentication の標準ポリシー（最小6文字）。メール確認オプション利用可 |
| **残存リスク** | パスワード強度ポリシーが未設定。ブルートフォース対策は Firebase 側のみ |
| **推奨 mitigation** | パスワード強度バリデーションをクライアント側に追加（最小8文字・英数混在）。メール確認を必須化 |

---

## 2. データ露出リスク

### 2-1. pairs コレクションの list 権限

| 項目 | 内容 |
|------|------|
| **リスク** | 認証済みユーザーが全ペアに対して list クエリを発行できる。inviteCode をスキャンされる可能性 |
| **現行対策** | `allow list: if request.auth != null`（招待コード照合クエリ用に必要）。個別取得 (`get`) は `partners` 内ユーザーのみに制限（v6.2） |
| **残存リスク** | 認証済みの悪意あるユーザーが全ペアを `whereEqualTo("active", true)` 等でリスト取得できる |
| **推奨 mitigation** | 招待コード照合を Cloud Functions に移行し、pairs の list 権限を廃止。Cloud Functions 側でレート制限・有効期限を実装 |

### 2-2. users コレクション

| 項目 | 内容 |
|------|------|
| **リスク** | 他ユーザーのプロフィール（displayName 等）への不正アクセス |
| **現行対策** | `request.auth.uid == userId` で本人のみ読み書き可 |
| **残存リスク** | ペアのパートナー情報（displayName）は pair doc や items doc には含まれないが、UID から推測可能 |
| **推奨 mitigation** | 現行対策で十分。displayName を別コレクション化して公開用フィールドのみ開示する設計も選択肢 |

---

## 3. Firestore Rules Bypass リスク

### 3-1. member による unpair 試行

| 項目 | 内容 |
|------|------|
| **リスク** | member ロールのユーザーが Firebase SDK を直接呼び出して `active=false` に更新しようとする |
| **現行対策** | v6.1 rules: `request.auth.uid == resource.data.master_uid` でのみ update 許可。member は join 時のみ update 可（`member_uid == null` 条件） |
| **残存リスク** | rules の抜け穴が存在した場合の影響は限定的（自分が参加しているペアの解除のみ） |
| **推奨 mitigation** | 定期的な rules ユニットテスト（Firebase Emulator Suite）の実施 |

### 3-2. deactivated pair への書込

| 項目 | 内容 |
|------|------|
| **リスク** | `active=false` になったペアに対してアイテムを追加・更新しようとする |
| **現行対策** | v6.2 rules: items の create/update に `pair.active == true` 条件を追加 |
| **残存リスク** | get() 呼び出しのコストが増加（Firestore read 課金）。ただし低頻度なため許容範囲 |
| **推奨 mitigation** | 現行対策で十分 |

---

## 4. 課金攻撃リスク

### 4-1. Firestore 読み取り爆撃

| 項目 | 内容 |
|------|------|
| **リスク** | 認証済みアカウントを大量取得し、pairs・items を高頻度で読み取ることで Firestore 課金を増加させる |
| **現行対策** | Firebase Authentication の基本的なレート制限のみ |
| **残存リスク** | App Check なし・IP レート制限なし。小規模アプリのため現時点では許容範囲 |
| **推奨 mitigation** | App Check 導入（最優先）。Firebase Alerts で Firestore 利用量の異常通知を設定 |

### 4-2. Cloud Functions 爆撃

| 項目 | 内容 |
|------|------|
| **リスク** | `onItemCreated` / `reminderSchedule` を大量トリガーして FCM 送信コストを増加させる |
| **現行対策** | Firestore rules で items 書込を partners ユーザーのみに制限 |
| **残存リスク** | 正規ユーザーによる意図的な大量アイテム作成は防げない |
| **推奨 mitigation** | Cloud Functions に items 件数上限チェックを追加（例: 1ペアあたり最大500件）|

---

## 5. 招待コードブルートフォースリスク

| 項目 | 内容 |
|------|------|
| **リスク** | 6桁数字の inviteCode（範囲: 100000〜999999 ≈ 900,000通り）を総当たりで試行 |
| **現行対策** | Firebase Authentication でのアカウント取得自体の障壁（メール/パスワード必須）。`active=true` のペアのみマッチ |
| **残存リスク** | 認証済みユーザーによるクエリは無制限。有効期限なし・試行回数制限なし |
| **推奨 mitigation** | inviteCode に有効期限フィールド（`expires_at`）を追加（推奨: 24時間）。Cloud Functions 側で試行回数を Redis/Firestore で管理しレート制限（例: 10回/分） |

---

## 6. 秘匿情報漏洩リスク

### 6-1. google-services.json / .firebaserc

| 項目 | 内容 |
|------|------|
| **リスク** | Firebase API キー・プロジェクト ID が Git リポジトリに混入 |
| **現行対策** | papazon-dash/.gitignore および project-level .gitignore の両方で明示除外（cmd_667 二重遮断） |
| **残存リスク** | `git add -f` による強制追加は防げない。CI/CD パイプライン上での漏洩 |
| **推奨 mitigation** | gitleaks を CI に組み込む（project-level .gitleaks.toml は既存）。Secrets Manager への移行 |

### 6-2. Firebase API キーの性質

| 項目 | 内容 |
|------|------|
| **リスク** | Firebase Web API キーはクライアント側に公開される設計だが、悪用されると Firebase サービスへの不正アクセスの足がかりになる |
| **現行対策** | Firestore rules による認証・認可で実際のデータアクセスを制御 |
| **残存リスク** | API キーが公開状態でも Firestore rules が堅牢なら実害は限定的 |
| **推奨 mitigation** | Firebase Console で API キーの使用制限（アプリ制限・API 制限）を設定 |

---

## 7. リスク優先度サマリ

| 優先度 | リスク | 推奨 mitigation |
|--------|--------|----------------|
| 🔴 高 | 招待コードブルートフォース | 有効期限追加・試行回数制限 |
| 🔴 高 | pairs list 権限による全ペアスキャン | 招待コード照合を Cloud Functions へ移行 |
| 🟡 中 | 課金攻撃 (Firestore/CF) | App Check 導入・Firebase Alerts 設定 |
| 🟡 中 | 弱いパスワード | クライアント側バリデーション強化 |
| 🟢 低 | member による unpair 試行 | 現行 rules で構造的に解消済み |
| 🟢 低 | deactivated pair への書込 | v6.2 rules で解消済み |
| 🟢 低 | google-services.json 混入 | .gitignore 二重遮断で解消済み |

---

---

## 設計上の判断（Security Trade-offs）

本セクションでは、意図的にリスクを残した設計決定とその根拠を記録します。

### T-1. pairs コレクションの list 権限を全認証済みユーザーに開放

**判断**: `allow list: if request.auth != null` — 招待コード照合クエリ（`joinByCode whereEqualTo`）のために必要。

**根拠**:
- pairs コレクションへの `whereEqualTo("inviteCode", code)` クエリは、Firestore 上で `list` 権限なしには実行できない。
- 代替案（招待コード照合を Cloud Functions に移行）はバックエンド複雑性が増大し、現フェーズの規模（家族2名利用）に対してオーバーエンジニアリングと判断。

**影響範囲の評価**:
- 露出するデータ: ショッピングリスト管理用のペアメタデータのみ（shopping items は非機密・財務/医療データ非含有）。
- 攻撃者の前提条件: Firebase Authentication による有効なアカウント取得が必要（障壁あり）。
- 招待コード総当たり: 6桁数字（約 90 万通り）・認証済みアカウントからのクエリが必要。

**見送った対策**:
- 招待コード照合の Cloud Functions 移行（将来候補）
- inviteCode に有効期限フィールド追加（将来候補）
- Cloud Functions 側でのレート制限実装（将来候補）

**高機密データを扱う場合**: 上記対策を実装すること。現行アーキテクチャのまま機密性の高いデータを追加することは推奨しない。

### T-2. member join 時の pairs update は差分検証付きで許可（v6.4）

**判断**: member join は `joinByCode()` 実装が書き込む3フィールド（`member_uid` / `joined_at` / `partners`）のみ変更を許可。`master_uid` / `active` / `inviteCode` は不変条件として Firestore Rules レベルで強制。

**根拠**:
- v6.2 以前: `member_uid == null` の確認のみで、書き込み内容を検証していなかった（外部レビューで指摘）。
- v6.3: `affectedKeys().hasOnly([...])` を導入したが、フィールドリストが実装（`FirebaseRepository.kt joinByCode()`）と乖離していた（`member_joined_at` ≠ 実際の `joined_at`、`partners` arrayUnion を未検証）。
- v6.4（本バージョン）: 実装コードのフィールドリストと rules を突合し、`partners` の `hasAll` / `hasAny` / `size()+1` 多段検証を追加。

**差分検証の多段構造（v6.4）**:
1. `affectedKeys().hasOnly(['member_uid', 'joined_at', 'partners'])` — それ以外のフィールド変更を拒否
2. `partners.hasAll(resource.data.partners)` — 既存 partners を削除できない
3. `request.auth.uid in request.resource.data.partners` — 自分の UID が追加されていること
4. `partners.size() == resource.data.partners.size() + 1` — 追加は自 UID の1件のみ

**Firebase SDK 互換性注記**: Firestore REST API (`batchWrite` + `appendMissingElements`) 経由の `arrayUnion` は rules 評価時に `request.resource.data.partners` にトランスフォーム後の値が含まれないため DENIED となる。Android アプリが使用する Firebase SDK (gRPC プロトコル) 経由のみが正当な join 経路となる（REST 直叩きによる join は rules レベルで拒否される）。

---

## 更新履歴

| バージョン | 日付 | 変更内容 |
|-----------|------|--------|
| v1.3 | 2026-07-02 | T-2 Firebase SDK互換性注記追加: REST batchWrite経由joinはDENIED・SDKパスのみ正当 (cmd_673 gunshi INF-1) |
| v1.2 | 2026-07-02 | T-2 v6.4対応: 実装フィールドとの乖離修正・partners多段検証追加 (cmd_673 P1) |
| v1.1 | 2026-07-02 | Security Trade-offs セクション追加 (cmd_673): P0 rules差分検証・P1 設計判断文書化 |
| v1.0 | 2026-07-02 | 初版作成 (cmd_667) |
