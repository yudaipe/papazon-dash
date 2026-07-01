# papazon-dash 通知設計書

作成日: 2026-06-17
cmd_id: cmd_594c (WBS 2.4)
作成者: 足軽2号 (Claude Sonnet)

---

## 1. 設計方針サマリー

### 1-1. CF回避防衛ライン（cmd_594d準拠）

Firebase Sparkプランはクレジットカード未登録固定とし、**Cloud Functions を一切使用しない**。
これにより FCM 送信をサーバーサイドからトリガーする経路が消えるため、通知アーキテクチャを
**クライアント主体（Androidアプリ完結）** に再設計する。

| 経路 | CF有（設計v3） | CF回避（本設計） |
|------|--------------|----------------|
| 新規依頼通知 | CF `onDocumentCreated` → FCM Admin SDK | Firestore snapshotListener → ローカル通知 |
| リマインド通知 | CF `onSchedule`（1分おき） | WorkManager + ローカル通知スケジューラ |
| 完了通知 | CF → FCM Admin SDK → Master | Firestore snapshotListener → ローカル通知 |
| アプリ完全終了時 | FCM high-priority Data message | WorkManager 15分おきポーリング（準リアルタイム） |

**FCM の役割変更**:
- CF有設計: サーバー（CF）が送信元 → FCM → 端末に配信
- CF回避設計: FCM は**受信インフラのみ**として保持。送信経路をFirestore+WorkManagerで代替。
  FCM Spark枠（完全無料・無制限）は消費するが送信量 = 0 のため課金リスクゼロ。

---

## 2. Android Notification Channel 設計

3チャネル以内の制約を満たし、通知の重要度を意味のある粒度で分類する。

### チャネル一覧

| # | Channel ID | Channel 名称 | importance | 用途 |
|---|-----------|------------|-----------|------|
| 1 | `ch_errand_new` | 新規依頼 | `IMPORTANCE_HIGH` | Master→Slave: 新規お使い依頼 |
| 2 | `ch_reminder` | リマインド | `IMPORTANCE_HIGH` | Slave宛: 未完了リマインド・スヌーズ |
| 3 | `ch_completion` | 完了報告 | `IMPORTANCE_DEFAULT` | Master宛: スレーブ完了通知 |

### 各チャネル詳細

#### ch_errand_new — 新規依頼

```kotlin
NotificationChannel(
    "ch_errand_new",
    "新規依頼",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "パートナーからの新しいお使い依頼"
    enableVibration(true)
    vibrationPattern = longArrayOf(0, 200, 100, 200)  // 2バイブ
    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
    setShowBadge(true)
}
```

**設計根拠**: お使いを頼む側（Master）の「頼んだのに気づかれなかった」フラストレーション解消が最優先。
高優先度でヘッドアップ通知（画面最上部ポップアップ）として表示される。

#### ch_reminder — リマインド

```kotlin
NotificationChannel(
    "ch_reminder",
    "リマインド",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "未完了のお使いのリマインド"
    enableVibration(true)
    vibrationPattern = longArrayOf(0, 100, 100, 100)  // 短め3バイブ
    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
    setShowBadge(true)
}
```

**設計根拠**: リマインドは新規依頼と同等の重要度で届ける（「忘れているから」届ける）。
ただし振動パターンを短くし「新規依頼ではなくリマインドだ」とユーザーが区別できるようにする。

#### ch_completion — 完了報告

```kotlin
NotificationChannel(
    "ch_completion",
    "完了報告",
    NotificationManager.IMPORTANCE_DEFAULT
).apply {
    description = "パートナーがお使いを完了しました"
    enableVibration(false)
    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
    setShowBadge(true)
}
```

**設計根拠**: 完了通知は「嬉しい確認」であり緊急性がない。バイブなし・標準重要度で静かに届ける。
ヘッドアップ表示はしない（通知センターに蓄積）。

### なぜ3チャネルか

- `ch_errand_new` と `ch_reminder` を統合すると「依頼と催促を区別したい」ユーザー要求に応えられない
- `ch_completion` を分離することでMasterが完了通知だけ一時ミュートできる（設定→アプリ→通知）
- 4チャネル目候補（「ペアリング通知」等）は使用頻度が極低。既存3chのいずれかに分類可能

---

## 3. FCM Token 管理

### 3-1. トークン保存設計

CF回避設計では **FCM Tokenを相手に届ける経路としてFirestoreを使用する**。

```
Firestore: users/{uid}/
  fcmToken: String       // 自端末のFCMトークン（アプリ起動時に更新）
  fcmTokenUpdatedAt: Timestamp
```

**保存タイミング**:

```kotlin
// FirebaseMessagingService.onNewToken() でトークン更新を検知
override fun onNewToken(token: String) {
    // Firestore の自分のドキュメントにトークンを保存
    firestore.collection("users")
        .document(auth.currentUser!!.uid)
        .update("fcmToken", token, "fcmTokenUpdatedAt", FieldValue.serverTimestamp())
}

// アプリ起動時にも確認・更新（初回起動・再インストール対応）
FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
    saveTokenToFirestore(token)
}
```

### 3-2. Topic 非採用の理由

FCM Topic（`/topics/pair_{pairId}` 等）は**採用しない**。

| 比較軸 | Token方式（採用） | Topic方式（不採用） |
|--------|----------------|----------------|
| 対象精度 | 特定端末1台 | 購読全端末（制御が粗い） |
| CF必要性 | 送信がないので関係なし | CF（Admin SDK）が必要 |
| PoC規模 | 2人1ペア = 相手トークン1本で十分 | 過剰設計 |
| セキュリティ | Firestoreルールで相手トークン読取を制御可能 | Topic登録管理が必要 |

PoC規模（2人固定ペア）においてTopicは過剰。相手のFCM Tokenを直接Firestoreから取得して**ローカル通知生成**に使用する（FCM Push送信には使わない）。

### 3-3. CF回避時のFCM Token活用

CF回避設計では FCM Token はプッシュ配信の宛先ではなく、**「相手がアクティブかどうか判断する補助情報」** として用いる。
実際の通知送信はFirestore Listener経由のローカル通知で完結する。

---

## 4. CF回避時の通知 Push 経路

### 4-1. アーキテクチャ全体像

```
Master端末                       Slave端末
────────────────────────────────────────────────────────────
  [1] アイテム追加
   └─► Firestore errands/{id} 書込
           │
           ├─────────────────────────────────────────────────►
           │                                 [2a] snapshotListener
           │                                      └─► ローカル通知表示
           │                                          (アプリ起動中/BG時)
           │
           │                                 [2b] アプリ終了時
           │                                      └─► WorkManager Periodic
           │                                           (15分おきFirestore取得)
           │                                           └─► ローカル通知表示
           │
  [3] 完了操作                    Slave
   └─► errands/{id}.status=done
           │
           ├─────────────────────────────────────────────────►
           │                                 Master
           │                                 snapshotListener
           │                                 └─► 完了ローカル通知
```

### 4-2. Primary経路: Firestore snapshotListener + ローカル通知

**対象**: アプリがフォアグラウンド or バックグラウンド（OSにkillされていない状態）

Firestoreは`onSnapshot`で永続WebSocket接続を維持するため、アプリが生きている限りリアルタイムで変更を受信できる。

```kotlin
// Repository.kt — errands の変化を監視して通知を発火
fun observeErrandsForNotification(pairId: String): Flow<List<Errand>> =
    callbackFlow {
        val listener = firestore.collection("errands")
            .whereEqualTo("pairId", pairId)
            .whereEqualTo("status", "open")
            .addSnapshotListener { snapshots, e ->
                snapshots?.documentChanges?.forEach { change ->
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            // 自分がSlaveの場合のみ新規依頼通知を表示
                            if (myRole == Role.SLAVE) {
                                notificationHelper.showErrandNotification(
                                    errand = change.document.toObject(Errand::class.java),
                                    channelId = "ch_errand_new"
                                )
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            val errand = change.document.toObject(Errand::class.java)
                            // 自分がMasterかつstatus=doneになった場合
                            if (myRole == Role.MASTER && errand.status == "done") {
                                notificationHelper.showCompletionNotification(
                                    errand = errand,
                                    channelId = "ch_completion"
                                )
                            }
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }
```

### 4-3. Secondary経路: WorkManager 定期ポーリング

**対象**: アプリが完全終了（OSにkillされた）状態

WorkManager の `PeriodicWorkRequest` (最小間隔15分) でFirestoreをポーリングし、
前回確認時刻より新しいerrands/completionsを検出してローカル通知を表示する。

```kotlin
class ErrandSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check_ts", 0L)

        // 前回チェック以降の新規/完了変化を取得
        val newErrands = repository.getErrandsSince(Timestamp(lastCheck / 1000, 0))

        newErrands.forEach { errand ->
            when {
                errand.createdAt.toDate().time > lastCheck && myRole == Role.SLAVE ->
                    notificationHelper.showErrandNotification(errand, "ch_errand_new")
                errand.completedAt != null && errand.completedAt.toDate().time > lastCheck && myRole == Role.MASTER ->
                    notificationHelper.showCompletionNotification(errand, "ch_completion")
            }
        }
        prefs.edit().putLong("last_check_ts", System.currentTimeMillis()).apply()
        return Result.success()
    }
}

// アプリ起動時に登録
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "errand_sync",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<ErrandSyncWorker>(15, TimeUnit.MINUTES).build()
)
```

**精度**: ±15分（WorkManagerの最小間隔制約）
CF有設計（±5分）より精度は落ちるが、PoC段階では許容範囲内。

### 4-4. リマインド通知の経路（CF回避版）

CF有設計ではCFスケジューラが1分おきにFirestoreをポーリングしてリマインドを送信していたが、
CF回避版では **Slaveデバイス自身がリマインドタイマーを管理する**。

```kotlin
// リマインドアイテム受信時にAlarmManagerでローカルアラームをセット
fun scheduleReminder(errand: Errand, intervalHours: Int) {
    val triggerTime = System.currentTimeMillis() + intervalHours * 3600 * 1000L
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        errand.id.hashCode(),
        Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("errand_id", errand.id)
            putExtra("errand_name", errand.name)
            putExtra("remind_count", errand.remindCount + 1)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    // Android 12以降は USE_EXACT_ALARM 権限が必要
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
}

// ReminderBroadcastReceiver がローカル通知を表示
class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val errandId = intent.getStringExtra("errand_id") ?: return
        // Firestoreでまだstatus=openか確認してから通知
        CoroutineScope(Dispatchers.IO).launch {
            val stillOpen = repository.isErrandOpen(errandId)
            if (stillOpen) {
                notificationHelper.showReminderNotification(
                    errandName = intent.getStringExtra("errand_name") ?: "",
                    remindCount = intent.getIntExtra("remind_count", 1),
                    channelId = "ch_reminder"
                )
            }
        }
    }
}
```

**設計制約**:
- `USE_EXACT_ALARM` 権限: Android 12+では`SCHEDULE_EXACT_ALARM`を要求。PoC段階では許容。
- 端末再起動時: `BOOT_COMPLETED` BroadcastReceiverでリマインドを再スケジュールする処理が必要。
- 精度: AlarmManagerのexactアラームのため±1分以内（CF有版の±5分より高精度）。

---

## 5. 通知 UX 設計

### 5-1. 通知コンテンツ設計

| 通知種別 | Channel | タイトル | 本文 | アクション |
|---------|---------|---------|------|----------|
| 新規依頼 | ch_errand_new | `{Masterの表示名}からの依頼` | `{アイテム名}を買ってきて！` | ✅ 了解 / 🔔 あとで(15分) / ❌ 無理 |
| リマインド | ch_reminder | `まだ買ってないですよ？（{N}回目）` | `{アイテム名}のリマインドです` | ✅ 今買った / 🔔 あとで(15分) |
| 完了報告 | ch_completion | `{Slaveの表示名}が完了しました` | `{アイテム名}を買ってきました！` | （アクションなし） |

### 5-2. インタラクティブアクション実装

通知アクションはアプリ起動なしで応答可能。`BroadcastReceiver` で処理する。

```kotlin
// 通知ビルダー（新規依頼）
fun buildErrandNotification(errand: Errand, channelId: String): Notification {
    val okAction = NotificationCompat.Action.Builder(
        R.drawable.ic_check,
        "了解",
        getPendingIntent(ACTION_ACKNOWLEDGE, errand.id)
    ).build()

    val snoozeAction = NotificationCompat.Action.Builder(
        R.drawable.ic_snooze,
        "あとで(15分)",
        getPendingIntent(ACTION_SNOOZE, errand.id)
    ).build()

    val cancelAction = NotificationCompat.Action.Builder(
        R.drawable.ic_cancel,
        "無理",
        getPendingIntent(ACTION_DECLINE, errand.id)
    ).build()

    return NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_shopping_cart)
        .setContentTitle("${errand.masterName}からの依頼")
        .setContentText("${errand.name}を買ってきて！")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .addAction(okAction)
        .addAction(snoozeAction)
        .addAction(cancelAction)
        .setAutoCancel(true)
        .build()
}
```

### 5-3. アクション処理（BroadcastReceiver）

```kotlin
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val errandId = intent.getStringExtra("errand_id") ?: return
        when (intent.action) {
            ACTION_ACKNOWLEDGE -> {
                // status を acknowledged に更新（完了ではなく「見た」確認）
                repository.acknowledgeErrand(errandId)
            }
            ACTION_SNOOZE -> {
                // 15分後にリマインドを再スケジュール
                repository.snoozeErrand(errandId, snoozeMinutes = 15)
                notificationHelper.scheduleReminder(errandId, delayMinutes = 15)
            }
            ACTION_DECLINE -> {
                // status を declined に更新（Masterに「無理」通知）
                repository.declineErrand(errandId)
            }
        }
        // 通知を閉じる
        NotificationManagerCompat.from(context).cancel(errandId.hashCode())
    }
}
```

### 5-4. スヌーズ設計

- スヌーズ時間: **15分固定**（spec F-REMIND-05準拠）
- スヌーズ実装: AlarmManager.setExactAndAllowWhileIdle で15分後に再スケジュール
- スヌーズ後再通知: 同じchannel・同じnotification IDで再表示（置き換え）

---

## 6. サイレント時間帯

**仕様（F-REMIND-06準拠）**: 22:00〜07:00は自動リマインドを停止。

```kotlin
fun isInSilentHours(): Boolean {
    val prefs = userPreferences.value
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val silentStart = prefs.silentStart  // デフォルト 22
    val silentEnd = prefs.silentEnd      // デフォルト 7

    return if (silentStart > silentEnd) {
        // 日をまたぐ場合（22〜翌7）
        hour >= silentStart || hour < silentEnd
    } else {
        hour in silentStart until silentEnd
    }
}

// AlarmManager セット時にサイレント時間を避けてスケジュール
fun scheduleReminderSkippingSilent(errand: Errand, intervalHours: Int) {
    var triggerTime = System.currentTimeMillis() + intervalHours * 3600 * 1000L
    val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }

    if (isInSilentHours(cal)) {
        // 翌07:00にずらす
        cal.set(Calendar.HOUR_OF_DAY, silentEnd)
        cal.set(Calendar.MINUTE, 0)
        if (cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        triggerTime = cal.timeInMillis
    }
    scheduleAlarm(errand, triggerTime)
}
```

---

## 7. CF回避設計のトレードオフと制限事項

| 項目 | CF有（設計v3） | CF回避（本設計） | 差分 |
|------|--------------|----------------|------|
| アプリ完全終了時の通知精度 | ±0分（FCM即時） | ±15分（WorkManager） | **劣化** |
| リマインド精度 | ±5分（CFスケジューラ） | ±1分（AlarmManager） | **改善** |
| バックグラウンド通知 | FCM high-priority（常時） | Firestore永続接続（生存時） | 同等 |
| 課金リスク | CF実行回数が課金対象 | **ゼロ（Spark固定）** | **改善** |
| バッテリー影響 | サーバー側で完結 | WorkManager + AlarmManager | やや増加 |
| 実装複雑度 | CF（Node.js）+ Android | Android完結 | **簡素化** |

**PoC段階での許容判断**:
- アプリ完全終了時±15分の精度低下は、PoC評価において許容範囲とする
- 「夫が無視できない通知UX」はフォアグラウンド/バックグラウンド（snapshotListener）で十分担保される
- Blaze移行時にCF1関数を追加すれば即時FCM配信に切り替え可能（拡張ポイント）

---

## 8. Manifest 設定

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

<!-- FCMサービス（受信のみ） -->
<service
    android:name=".notification.PapazonMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>

<!-- 通知アクションレシーバー -->
<receiver
    android:name=".notification.NotificationActionReceiver"
    android:exported="false" />

<!-- リマインダーレシーバー -->
<receiver
    android:name=".notification.ReminderBroadcastReceiver"
    android:exported="false" />

<!-- 再起動時リマインド復元 -->
<receiver
    android:name=".notification.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

## 9. spec/design v3 との差分対照

| spec/design v3 記述 | 本設計（CF回避） | 変更理由 |
|--------------------|----------------|---------|
| `Cloud Functions onDocumentCreated → FCM Admin SDK` | `Firestore snapshotListener → ローカル通知` | Spark CF不可 |
| `Cloud Functions onSchedule（1分おき）` | `AlarmManager + WorkManager` | Spark CF不可 |
| `FCM Data Message（priority: high）送信` | FCM受信インフラのみ保持・送信なし | Admin SDKはCF必須 |
| `リマインド精度 ±5分` | `±1分（AlarmManager）` | 改善 |
| `アプリ終了時即時通知` | `±15分（WorkManager）` | 劣化・PoC許容 |
