# papazon-dash

カップル向けショッピングリスト共有 Android アプリ（SmartSE K01 コンペ優勝作品）。

パートナーとリアルタイムで買い物リストを共有し、購入済みアイテムを FCM プッシュ通知で同期できます。

---

## 機能

- **ペアリング**: 招待コードでパートナーと 1 対 1 接続
- **買い物リスト共有**: アイテムの追加・削除をリアルタイム同期（Firestore）
- **購入済みマーク**: タップで購入済み状態をトグル
- **プッシュ通知**: パートナーがアイテムを追加した際に FCM 通知
- **リマインダー**: 指定時刻に未完了アイテムを通知

## 技術スタック

- **Android**: Kotlin / Jetpack Compose / Hilt / Firebase SDK
- **Backend**: Firebase Cloud Functions (TypeScript)
- **Database**: Cloud Firestore
- **Auth**: Firebase Anonymous Authentication
- **Notifications**: Firebase Cloud Messaging (FCM)

---

## セットアップ

→ [SETUP.md](SETUP.md) を参照してください。

各自の Firebase プロジェクトでビルドするためのすべての手順が記載されています。

---

## 既知制約

- **Anonymous Auth ベース**: 再インストール後に uid が変わるため再ペアリングが必要
  （将来: Google Sign-In 実装予定）
- **アイテム編集 UI 未実装**: 作成後の名前修正は削除・再作成で代替
  （v1.1.0 予定）
- **機能4（リマインダー送信）未実装**: Cloud Functions のリマインダー送信ロジックは実装済みだが Android 側 UI は未実装

詳細 → [backlog.md](backlog.md)

---

## ライセンス

TBD — awaiting decision（[LICENSE](LICENSE) 参照）

## コントリビュートポリシー

TBD
