# papazon-dash Cloud Functions 設計調査書

作成日: 2026-06-17
cmd_id: cmd_594c (subtask: subtask_594c_2_2_cf_design)
参照: 20260613+cmd_594_spec.md (v2), 20260613+cmd_594_design.md (v3), cmd_594d Firebase調査報告書

---

## 1. 目的

design v3 が Cloud Functions（CF）に依存する設計を採用しているが、cmd_594d Firebase調査の**ゼロ課金防衛ライン**（Spark固定・CF回避）との整合を取ることが本文書の目的である。  
CFが必要な機能を特定し、各機能についてCF回避代替設計を提示する。Blaze前提となる場合は課金リスク管理設計を併せて記載する。

---

## 2. CF依存機能の棚卸し

design v3 で Cloud Functions に依存している箇所を以下に列挙する。

| # | 機能 | 現設計のCF依存箇所 | CF役割 |
|---|------|------------------|--------|
| 業務機能6 | 指令送信時リアルタイム通知（通知層） | `onDocumentCreated` トリガー → FCM Admin SDK 送信 | アイテム追加イベントを検知しスレーブへpush通知 |
| 業務機能3 | スレーブ側リマインド | `onSchedule`（1分間隔）→ reminder_at 比較 → FCM 再送信 | 未完了アイテムを定期チェックして再通知 |
| 業務機能5 | 完了履歴の自動削除（90日TTL） | `onSchedule`（日次）→ 90日経過ドキュメント削除 | Firestore TTLの代替 |

---

## 3. CF回避可能性評価

### 3-1. 前提

cmd_594d 調査が確認した事実（2026-06-16 取得）：

- **Spark プランでは Cloud Functions をデプロイできない**（Blaze必須）
- Spark 固定によりどんなバグ・攻撃が発生しても**物理的に課金 = 0 円**が保証される
- Firebase はハードキャップ機能を持たない。Blaze 移行後は無限課金リスクが残存する

### 3-2. 評価結果

| 機能 | CF回避可否 | 代替手段 | PoC品質影響 |
|------|-----------|---------|-----------|
| 業務機能6（新規依頼通知） | **可能** | WorkManager OneTimeWork + ローカル通知 | 通知到達遅延あり（Doze時 最大15分）。即時性は下がるが PoC では許容可能 |
| 業務機能3（自動リマインド） | **可能** | WorkManager PeriodicWork（最短15分間隔） | Doze Mode制約により正確性 ±15分。サイレント時間帯は WorkManager の Constraints で制御 |
| 業務機能5（90日自動削除） | **可能** | WorkManager PeriodicWork（日次）またはmanual admin | 削除タイミングにズレが生じるが PoC では許容可能 |

**結論: PoC スコープでは Cloud Functions は回避可能。Spark 固定（ゼロ課金防衛）を最優先として採用する。**

---

## 4. CF回避設計（Spark固定・防衛ライン準拠）

### 4-1. 業務機能6: 新規依頼通知 → WorkManager + ローカル通知

#### 設計概要

CF を除去し、以下の2層で新規依頼通知を実現する。

| 層 | 手段 | 動作条件 | 役割 |
|----|------|---------|------|
| データ層（既存） | Firestore `addSnapshotListener` | アプリがフォアグラウンド | リスト即時更新（機能2と共通） |
| 通知層（新設） | WorkManager OneTimeWork + Android NotificationManager | アプリがバックグラウンド / 未起動 | 「依頼が来た」ローカル通知 |

#### 実装パターン

```kotlin
// マスター側: アイテム追加後にスレーブ通知用Workをエンキュー
fun addErrand(pairId: String, item: Item) {
    // Firestore write（機能1）
    firestore.collection("pairs/$pairId/items").add(item)
    // WorkManager でスレーブ通知をキック（自端末の定期ポーリングで受信）
    // ※ WorkManager はスレーブ端末側で動く想定
}

// スレーブ端末側: Firestore差分ポーリング Worker
class ErrandCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val lastCheckedAt = prefs.getLong("last_checked_at", 0L)
        val newItems = firestoreRepo.getNewErrands(pairId, lastCheckedAt)
        if (newItems.isNotEmpty()) {
            showLocalNotification("新しいお使い依頼", newItems.first().name)
        }
        prefs.edit { putLong("last_checked_at", System.currentTimeMillis()) }
        return Result.success()
    }
}

// アプリ起動時に登録（PeriodicWork: 最短15分間隔）
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "errand_check",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<ErrandCheckWorker>(15, TimeUnit.MINUTES).build()
)
```

#### 注意事項

- WorkManager の PeriodicWork 最短間隔は Android OSが **15分** を保証。Doze Mode 中はバッテリー最適化でさらに遅延する場合がある。
- **PoC許容範囲**: 「夫が依頼から最大15〜30分以内に気づく」はPoC品質として許容可能（design v3の「5秒以内」から大きく後退するが、学習用PoCとしては問題なし）。
- 本番リリース（≠PoC）を想定する場合は Blaze + CF 採用が必要となるため、その場合は §5 の設計を参照すること。

---

### 4-2. 業務機能3: 自動リマインド → WorkManager PeriodicWork

#### 設計概要

```kotlin
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val openErrands = firestoreRepo.getOpenErrands(pairId)
        val now = System.currentTimeMillis()
        val reminderInterval = settingsRepo.getReminderIntervalMs()
        val silentStart = settingsRepo.getSilentStartHour()   // 例: 22
        val silentEnd   = settingsRepo.getSilentEndHour()     // 例: 7

        // サイレント時間帯チェック（WorkManager Constraints でも設定可能）
        if (isInSilentHours(silentStart, silentEnd)) return Result.success()

        openErrands
            .filter { it.lastRemindAt + reminderInterval <= now }
            .filter { it.remindCount < settingsRepo.getMaxRemindCount() }
            .forEach { errand ->
                showLocalNotification(
                    title = "お使いリマインド（${errand.remindCount + 1}回目）",
                    body  = "${errand.name} を買いましたか？"
                )
                firestoreRepo.incrementRemindCount(errand.id)
            }
        return Result.success()
    }
}
```

**WorkManager 登録**:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "reminder_worker",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()
)
```

- **精度**: ±15分（CF版 ±5分 より低精度だが PoC 許容範囲）
- スヌーズ（15分後）は `enqueueUniqueWork(OneTimeWork, delay=15min)` で実装

---

### 4-3. 業務機能5: 90日自動削除 → WorkManager 日次 + manual admin

```kotlin
class CleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        firestoreRepo.deleteCompletedBefore(cutoff)
        return Result.success()
    }
}

// 日次1回（1440分間隔）
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "cleanup_worker",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<CleanupWorker>(1440, TimeUnit.MINUTES).build()
)
```

- PoCではマスターが起動するたびに実行されれば十分。manual admin削除でも許容。

---

## 5. Blaze 前提 CF 設計（参考：将来本番化時）

PoCを超えた本番リリース（Doze突破・秒単位の即時通知が必要な場合）に備え、CF採用時の設計と課金リスク管理を記録する。

### 5-1. CF機能設計

#### 業務機能6（onDocumentCreated トリガー）

```javascript
// functions/index.js
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

exports.notifyOnItemCreate = onDocumentCreated(
  "pairs/{pairId}/items/{itemId}",
  async (event) => {
    const item = event.data.data();
    const db = admin.firestore();
    const pairSnap = await db.doc(`pairs/${event.params.pairId}`).get();
    const slaveUid = pairSnap.data().slave_uid;
    const userSnap = await db.doc(`users/${slaveUid}`).get();
    const token = userSnap.data().fcmToken;
    if (!token) return;

    await admin.messaging().send({
      token,
      data: {
        type: "item_created",
        itemId: event.params.itemId,
        itemName: item.name,
        pairId: event.params.pairId,
      },
      android: { priority: "high" },
    });
  }
);
```

#### 業務機能3（onSchedule リマインド）

```javascript
const { onSchedule } = require("firebase-functions/v2/scheduler");

exports.sendReminders = onSchedule("every 5 minutes", async () => {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();
  // 省略: pairId別にopen errandをクエリ → reminder_at超過分をFCM送信
});
```

### 5-2. 課金アラート設計（Blaze採用時の必須安全装置）

Blaze に移行する場合、以下の **3段防護** を必ず実施する。

| 防護 | 手段 | 効果 |
|------|------|------|
| **①予算アラート** | GCP Billing → Budget & Alerts: $1/月 でメール通知 | 課金発生を即時把握 |
| **②Firestore セキュリティルール強化** | `allow read, write: if request.auth != null` + partners検証 | 第三者からのクローラー・DOS攻撃による Reads 爆発を防止 |
| **③CF無限ループ防止** | onDocumentCreated トリガー内では同一ドキュメントへの write を禁止 | 再帰トリガーによる無限課金ループを防止 |

> **注意**: Firebase にはハードキャップ機能がない（予算アラートは通知のみで停止しない）。PoC 期間中は Blaze 移行を行わないことを強く推奨する。

---

## 6. 推奨設計まとめ

| 採用方針 | 構成 | 課金リスク | PoC即時通知品質 |
|---------|------|-----------|---------------|
| **Spark固定（推奨）** | CF不使用・WorkManager代替 | **ゼロ（物理的保証）** | 最大15〜30分遅延 |
| Blaze + CF | CF採用・FCM即時push | 管理次第で数万円〜 | 5秒以内（Doze突破） |

**PoC用途（学習・課題提出）においては Spark 固定・WorkManager 代替設計を採用する。**  
本番リリースを判断する際に §5 の Blaze 設計へ切り替えること。

---

## 7. design v3 との差分サマリ

design v3 で CF 依存していた箇所を以下の通り置き換える。

| design v3 記載 | cf_design（本文書）の差分 |
|---------------|--------------------------|
| 業務機能3: `Cloud Functions の onSchedule（1分おき）` | WorkManager PeriodicWork（15分間隔）に変更 |
| 業務機能6: `Cloud Functions の onDocumentCreated` | WorkManager + Firestore差分ポーリングによるローカル通知に変更 |
| システム構成図の `CF["Cloud Functions"]` ブロック | Spark固定では削除。WorkManagerブロックに置換 |
| PoC実装段取り P5「機能3: Cloud Functions scheduled」 | WorkManager PeriodicWork に変更 |

次フェーズ（MS1実装）でこれらの変更を design v3 に反映すること。

---

## Self-QC

| 類型 | 確認結果 |
|------|--------|
| 1 網羅性 | CF依存3機能（機能6/機能3/機能5）全評価。coverage 3/3 |
| 2 件数整合 | 評価→CF回避設計→Blaze参考設計の3層構成、矛盾なし |
| 3 cmd_594d防衛ライン反映 | Spark固定・CF回避を最優先採用。Blaze設計は参考記載のみ |
| 4 社内機密 | 本文書はSmartSE学外提出物想定。社名・本番構成・業務システム名称不含 |
| 5 データ実在 | 本文書自体が成果物。ls -l で確認予定 |
| 6 spec/design整合 | 機能名は spec v2 統一名称（機能6/機能3/機能5）準拠 |
