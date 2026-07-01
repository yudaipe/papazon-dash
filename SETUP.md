# papazon-dash — セットアップ手順

このリポジトリをフォークした各自が、自分の Firebase プロジェクトでビルド・動作させるための手順書です。

## 前提条件

- Android Studio Hedgehog 以降
- Java 17
- Node.js 18+
- Firebase CLI (`npm install -g firebase-tools`)

---

## Step 1: Firebase プロジェクト作成

1. [Firebase Console](https://console.firebase.google.com/) で新規プロジェクトを作成
2. プロジェクト ID をメモ（例: `my-papazon-dash`）
3. **Authentication** → Sign-in method → **匿名** を有効化
4. **Firestore Database** → 本番モードで作成（リージョン: `asia-northeast1` 推奨）
5. **Cloud Messaging** を有効化（FCM プッシュ通知用）
6. プロジェクトを **Blaze（従量課金）プラン** にアップグレード
   - Cloud Functions のデプロイに必須

---

## Step 2: google-services.json の取得・配置

1. Firebase Console → プロジェクト設定 → マイアプリ → **Android アプリ追加**
   - パッケージ名: `com.smartse.papazon_dash`
2. `google-services.json` をダウンロード
3. 以下のパスに配置:
   ```
   android/app/google-services.json
   ```
   ※ `android/app/google-services.json.example` を参考にフォーマット確認可

> **注意**: `google-services.json` は `.gitignore` により追跡対象外です。絶対にコミットしないでください。

---

## Step 3: .firebaserc の設定

```bash
cp .firebaserc.example .firebaserc
```

`.firebaserc` を編集し、`<YOUR_FIREBASE_PROJECT_ID>` を実際のプロジェクト ID に置き換え:

```json
{
  "projects": {
    "default": "my-papazon-dash"
  }
}
```

> **注意**: `.firebaserc` は `.gitignore` により追跡対象外です。

---

## Step 4: Firestore セキュリティルールのデプロイ

```bash
firebase login
firebase use <YOUR_FIREBASE_PROJECT_ID>
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

---

## Step 5: Cloud Functions のデプロイ

```bash
cd functions
npm install
npm run build
cd ..
firebase deploy --only functions
```

---

## Step 6: Android ビルド

```bash
cd android
./gradlew assembleDebug
```

ビルド成功後、`android/app/build/outputs/apk/debug/app-debug.apk` が生成されます。

Android Studio で直接開く場合:
1. `android/` フォルダを Android Studio で開く
2. 「Run app」ボタンで実機/エミュレータにインストール

---

## Step 7: 管理スクリプトの実行（任意）

`functions/src/` 配下の管理スクリプト（`create_item.js`、`dump_firestore.js` 等）は、
環境変数 `FIREBASE_PROJECT_ID` を設定してから実行してください:

```bash
export FIREBASE_PROJECT_ID=my-papazon-dash
node functions/src/create_item.js
```

---

## 既知制約

| 制約 | 内容 | 回避策 |
|------|------|--------|
| Anonymous Auth | 再インストール後に uid が変わるためペアリングが必要 | 再インストール後は招待コードで再ペアリング |
| アイテム編集 UI 未実装 | アイテム名作成後の修正不可 | 削除して再作成 |
| リマインダー UI 未実装 | CF 側は実装済み、Android 側 UI は未実装 | v1.1.0 予定 |

詳細は [backlog.md](backlog.md) を参照。

---

## トラブルシューティング

**`google-services.json` が見つからないエラー**
→ `android/app/google-services.json` が存在するか確認。example ファイルではなく本物を配置すること。

**Cloud Functions デプロイエラー**
→ Firebase プロジェクトが Blaze プランか確認。`firebase login --reauth` で再認証。

**Firestore PERMISSION_DENIED**
→ `firebase deploy --only firestore:rules` が完了しているか確認。

**管理スクリプト実行時に `YOUR_FIREBASE_PROJECT_ID` エラー**
→ `export FIREBASE_PROJECT_ID=<実際のプロジェクトID>` を設定してから実行すること。
