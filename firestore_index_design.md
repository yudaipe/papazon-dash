# papazon-dash Firestore インデックス設計書

作成日: 2026-06-17
cmd_id: cmd_594c（WBS 2.3）
参照: `20260613+cmd_594_spec.md` (v2), `20260613+cmd_594_design.md` (v3), `20260616+cmd_594d_firebase_server_research.md`

---

## 1. データモデル概要（design v3準拠）

```
/users/{userId}
  uid, displayName, pairId, role, fcmToken, createdAt

/pairs/{pairId}
  pairId, master_uid, slave_uid, created_at, invite_code, partners[]
  .settings: { reminderInterval, maxRemindCount, silentStart, silentEnd }

/pairs/{pairId}/items/{itemId}       ← 主要インデックス設計対象
  itemId, name, status("open"|"done"), created_by, created_at,
  completed_at(Timestamp|null), reminder_at(Timestamp|null),
  partners[] (セキュリティルール評価コスト削減用複製)
```

> `items` をサブコレクションに置くことで、pairId パスによる自然なスコープ分離を実現。  
> 全ユーザー横断クエリが不要となり、インデックスとセキュリティルールを最小化できる。

---

## 2. 複合インデックス一覧

### 2-1. 必須インデックス（手動定義）

Firestore は `where` + `orderBy` の組み合わせクエリに複合インデックスが必要。  
単一フィールドの `where` や `orderBy` は自動生成インデックスで対応できるが、以下は**手動定義必須**。

| # | コレクション | クエリ用途 | フィールド1 | フィールド2 | 自動生成 |
|---|---|---|---|---|---|
| IDX-01 | `items` | 未完了一覧取得（機能1/2） | `status` ASC | `created_at` DESC | 不可・**手動必須** |
| IDX-02 | `items` | 完了履歴取得（機能5） | `status` ASC | `completed_at` DESC | 不可・**手動必須** |
| IDX-03 | `items` | リマインダー対象クエリ（機能3） | `status` ASC | `reminder_at` ASC | 不可・**手動必須** |

### 2-2. 自動生成インデックスで対応可能なもの

| # | コレクション | クエリ | 備考 |
|---|---|---|---|
| AUTO-01 | `pairs` | `where invite_code == "XXXXXX"` | 単一フィールド。ペアリング照合用 |
| AUTO-02 | `users` | `users/{uid}` 直接読み取り | インデックス不要（UID直アクセス） |
| AUTO-03 | `pairs` | `pairs/{pairId}` 直接読み取り | インデックス不要（パス直アクセス） |
| AUTO-04 | `items` | `items/{itemId}` 直接読み取り | インデックス不要（単件更新・完了取り消し用） |

> **判断基準**: `where 単一条件のみ` または `orderBy 単一フィールドのみ` は Firestore が自動生成する単一フィールドインデックスで対応可能。複合インデックスは2フィールド以上の組み合わせ時のみ手動定義する。

---

## 3. firestore.indexes.json 雛形

Firebase CLIデプロイ用の設定ファイル。`firebase deploy --only firestore:indexes` で反映。

```json
{
  "indexes": [
    {
      "comment": "IDX-01: 未完了アイテム一覧（機能1/2 - onSnapshot・メイン画面）",
      "collectionGroup": "items",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "created_at", "order": "DESCENDING" }
      ]
    },
    {
      "comment": "IDX-02: 完了履歴取得（機能5 - HistoryScreen・最新30件）",
      "collectionGroup": "items",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "completed_at", "order": "DESCENDING" }
      ]
    },
    {
      "comment": "IDX-03: リマインダー対象クエリ（機能3 - WorkManager スケジューラ）",
      "collectionGroup": "items",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "reminder_at", "order": "ASCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

> **注意**: `comment` フィールドは JSON 標準外。Firebase CLI は無視するが可読性のために記載。  
> 実際の `firebase.json` では削除して運用する。

---

## 4. Reads 最悪値 108% 対策設計（cmd_594d 防衛ライン反映）

### 4-1. 問題整理

cmd_594d 調査によると、キャッシュを一切使わず起動ごとに全履歴を取得した場合:

```
2ユーザー × 10回起動/日 × 2,700件 = 54,000 Reads/日
Spark無料枠 = 50,000 Reads/日 → 108%オーバー → 当日リクエスト遮断
```

### 4-2. 対策: Source.CACHE first 実装

アプリ起動時にキャッシュから先に UI を描画し、Firestore への通信を抑制する。

```kotlin
// Repository: ItemRepository.kt
fun getOpenItemsCacheFirst(pairId: String): Flow<List<Item>> = flow {
    // Step 1: キャッシュ優先で即時表示（通信なし・Reads = 0）
    try {
        val cached = db.collection("pairs/$pairId/items")
            .whereEqualTo("status", "open")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get(Source.CACHE)  // ← キャッシュ専用読み込み
            .await()
        emit(cached.toObjects(Item::class.java))
    } catch (e: Exception) {
        // キャッシュ未存在（初回起動等）は無視してサーバー取得へ
    }

    // Step 2: バックグラウンドで差分のみサーバー同期（Reads = 変更件数のみ）
    emitAll(getOpenItemsFlow(pairId))
}

fun getOpenItemsFlow(pairId: String): Flow<List<Item>> = callbackFlow {
    val listener = db.collection("pairs/$pairId/items")
        .whereEqualTo("status", "open")
        .orderBy("created_at", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            snapshot?.let { trySend(it.toObjects(Item::class.java)) }
        }
    awaitClose { listener.remove() }
}
```

> **Reads節約原理**: `addSnapshotListener` は初回のみ全件取得（Reads = 件数分）。  
> 以降は変更されたドキュメントのみ差分配信されるため、アクティブセッション中の追加Readsは最小化される。  
> `Source.CACHE` 読み込みは **Reads = 0**（サーバー通信なし）。

### 4-3. snapshotListener スコープ限定

リスナーは**必要なコレクションのみ**に絞る。不要なリスナーを残存させない。

```kotlin
// ViewModel: MainViewModel.kt
private var itemsListenerJob: Job? = null

fun startItemsListener(pairId: String) {
    itemsListenerJob?.cancel()  // 既存リスナー確実に解除
    itemsListenerJob = viewModelScope.launch {
        repository.getOpenItemsCacheFirst(pairId).collect { items ->
            _items.value = items
        }
    }
}

override fun onCleared() {
    itemsListenerJob?.cancel()  // ViewModel破棄時に解除（メモリリーク・余分なReads防止）
    super.onCleared()
}
```

**監視対象コレクション（PoC最小セット）**:

| 画面 | 監視コレクション | 解除タイミング |
|---|---|---|
| MainScreen | `pairs/{pairId}/items` (status=open) | 画面離脱・ViewModel破棄 |
| SettingsScreen | `pairs/{pairId}.settings` | 画面離脱 |
| HistoryScreen | **リスナー不使用**（静的フェッチのみ） | — |

> HistoryScreen は完了済み履歴のため更新頻度が低い。静的 `.get()` で十分。  
> snapshotListener を貼らないことで、不要なリアルタイム接続を削減する。

### 4-4. 完了履歴は limit(30) で件数上限

```kotlin
// HistoryScreen 用静的フェッチ（Source.DEFAULT = キャッシュ→サーバー自動選択）
suspend fun getHistoryItems(pairId: String): List<Item> =
    db.collection("pairs/$pairId/items")
        .whereEqualTo("status", "done")
        .orderBy("completed_at", Query.Direction.DESCENDING)
        .limit(30)                     // ← 最大30件に制限（spec F-HIST-02準拠）
        .get()
        .await()
        .toObjects(Item::class.java)
```

---

## 5. クエリパターン例示（10件）

| # | 機能 | クエリ | 使用インデックス | Reads概算 |
|---|---|---|---|---|
| Q-01 | 機能2: 未完了一覧（snapshotListener） | `items where status=="open" orderBy created_at DESC` | IDX-01 | 初回: 件数分 / 差分: 変更件数のみ |
| Q-02 | 機能5: 完了履歴取得 | `items where status=="done" orderBy completed_at DESC limit 30` | IDX-02 | 最大30 |
| Q-03 | 機能3: リマインダー対象取得 | `items where status=="open" AND reminder_at <= now` | IDX-03 | 対象件数 |
| Q-04 | 基盤B: ペアリング照合 | `pairs where invite_code == "ABC123"` | AUTO-01 | 1〜2 |
| Q-05 | 基盤A: FCMトークン取得 | `users/{slaveUid}` 直接読み取り | なし | 1 |
| Q-06 | 基盤A/B: ペア情報参照 | `pairs/{pairId}` 直接読み取り | なし | 1 |
| Q-07 | 機能4: 完了マーク | `items/{itemId}` update (status="done", completed_at=now) | なし | Write: 1 |
| Q-08 | 機能4: 完了取り消し（5分以内） | `items/{itemId}` update (status="open", completed_at=null) | なし | Write: 1 |
| Q-09 | 起動時キャッシュ読み込み | `Source.CACHE` by IDX-01 | IDX-01（ローカル） | 0（サーバー通信なし） |
| Q-10 | 機能3: スヌーズ更新 | `items/{itemId}` update (reminder_at = now + 15min) | なし | Write: 1 |

---

## 6. インデックス予算試算（Spark プラン）

### 前提: papazon-dash PoC 利用想定

- 利用者: 2人（1ペア）
- 1日あたり操作: 約30件（アイテム追加・完了・確認）
- アプリ起動: 各20回/日（計40回）

### Reads 試算（キャッシュ徹底後）

| クエリ | 頻度 | 1回あたりReads | 日次合計 |
|---|---|---|---|
| Q-01: onSnapshot 初回 | 起動ごと40回 | **1〜2件（差分のみ）** | ~60 |
| Q-02: 完了履歴（静的フェッチ） | 5回/日 | 最大30件 | ~150 |
| Q-03: リマインダークエリ（WorkManager） | 定期 1回/3h = 8回/日 | 対象2〜5件 | ~40 |
| Q-04: ペアリング照合 | PoC全体で数回のみ | 1〜2件 | ~0 |
| Q-05〜Q-06: 直接読み取り | 必要時のみ | 1件 | ~10 |
| **合計** | | | **約260 Reads/日** |

```
Spark無料枠: 50,000 Reads/日
推奨シナリオ実績: 約260 Reads/日 → 消化率 0.52%（完全安全）
最悪値(キャッシュなし): 54,000 Reads/日 → 108% ← 絶対に避ける
```

### Writes 試算

| 操作 | 頻度 | 日次 |
|---|---|---|
| アイテム追加 | 10件/日 | 10 |
| 完了マーク | 10件/日 | 10 |
| FCMトークン更新 | 起動時 | 2 |
| リマインダー更新 | 数件/日 | 5 |
| **合計** | | **約27 Writes/日** |

```
Spark無料枠: 20,000 Writes/日 → 消化率 0.14%（完全安全）
```

### Storage 試算

- 90日保持 × 30件/日 = 2,700件
- 1件あたり ~0.5KB（テキストのみ）= 1.35 MB
- Spark枠: 1 GiB → 消化率 0.13%（完全安全）

---

## 7. 実装チェックリスト

- [ ] `firestore.indexes.json` に IDX-01〜IDX-03 を定義
- [ ] `firebase deploy --only firestore:indexes` で反映確認
- [ ] `ItemRepository.getOpenItemsCacheFirst()` に `Source.CACHE` 先読み実装
- [ ] `ViewModel.onCleared()` で snapshotListener 解除
- [ ] HistoryScreen は `addSnapshotListener` **未使用**（静的フェッチ）
- [ ] 完了履歴クエリに `.limit(30)` 設定
- [ ] リマインダークエリ（WorkManager内）に IDX-03 対応クエリ実装
- [ ] Firestore Emulator でインデックス動作確認（開発環境）

---

## Self-QC

| 類型 | 確認結果 |
|---|---|
| 1 網羅性 | 必須インデックス3件 + 自動生成4件を全機能から網羅。クエリパターン10件記載。coverage 3/3 |
| 2 件数整合 | IDX-01〜IDX-03の3件が手動定義対象。Reads試算内訳の和=260/日。整合確認済 |
| 3 Reads108%対策 | Source.CACHE first実装・snapshotListenerスコープ限定・limit(30)の3段階で防衛 |
| 4 社内機密 | 社内固有情報（会社名・DL380・本番構成）なし。汎用アプリ設計として記述 |
| 5 spec/design整合 | コレクション名/フィールド名はdesign v3に準拠。機能番号はspec v2に準拠 |
| 6 Spark制約 | Cloud Functionsリマインダー → WorkManager代替を明記（Sparkプランの制約反映） |

---

*papazon-dash Firestore インデックス設計書 v1.0（2026-06-17）*
