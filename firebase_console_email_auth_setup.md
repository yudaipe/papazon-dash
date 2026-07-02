# Firebase Console — Email/Password Auth 有効化手順

作成日: 2026-07-02  
対象バージョン: papazon-dash v1.0.2  
所要時間: 約5分

---

## 手順

### 1. Firebase Console を開く

https://console.firebase.google.com にアクセスし、**papazon-dash** プロジェクトを選択する。

### 2. Authentication を開く

左サイドバー → **「構築」** → **「Authentication」** をクリック。

### 3. Sign-in method タブを選択

「Authentication」ページ上部の **「Sign-in method」** タブをクリック。

### 4. Email/Password を有効化

1. 「ネイティブプロバイダ」セクションの **「メール / パスワード」** をクリック
2. 「メール / パスワード」の右にあるトグルを **「有効」** に切り替え
3. 「メールリンク（パスワードなしのログイン）** は **無効のまま** でよい（不要）
4. **「保存」** ボタンをクリック

### 5. 確認

Sign-in method 一覧に「メール / パスワード」が **「有効」** と表示されていることを確認。

---

## 有効化後の動作確認 (実機E2E手順)

Firebase Console有効化完了後、以下の順序で実機テストを実施する。

### 前提

- 端末1: `adb.exe connect 100.119.21.32:40313`
- 端末2: `adb.exe connect 100.90.149.9:43649`
- APK v1.0.2 インストール済み（install済み・再installは不要）

### シナリオ A — 端末1: 新規登録 + ペアリング招待

1. 端末1でアプリ起動 → SignInScreen(新規登録タブ)表示を確認
2. メールアドレス入力: `test-master@example.com`  パスワード: `test1234`  名前: `テスト奥様`  役割: **奥様（マスター）**
3. 「登録する」タップ
4. PairingScreen 遷移を確認
5. 「招待コードを生成する」タップ → 6桁コードを控える

### シナリオ B — 端末2: 新規登録 + ペアリング参加

1. 端末2でアプリ起動 → SignInScreen(新規登録タブ)表示を確認
2. メールアドレス入力: `test-member@example.com`  パスワード: `test1234`  名前: `テスト旦那`  役割: **旦那（メンバー）**
3. 「登録する」タップ
4. PairingScreen 遷移を確認
5. 「コードを入力してペアに参加する」タップ → シナリオAのコードを入力
6. 端末1・端末2ともにメイン画面遷移を確認

### シナリオ C — ログアウト + 再ログイン（pairId復元確認）

1. 端末1で設定画面 → 「サインアウト」タップ → SignInScreen遷移を確認
2. 「ログイン」タブをタップ
3. `test-master@example.com` / `test1234` でログイン
4. **ペアリング画面を経由せずにメイン画面へ直接遷移** することを確認（pairId Firestore復元成功）

### シナリオ D — アイテム追加確認

1. 端末1(master)でアイテム追加 → 端末2(member)のメイン画面にリアルタイム反映を確認

---

## テスト後のFirestoreクリーンアップ

Firebase Console → **Firestore Database** で以下を手動削除:

- `users/` コレクション内のテストUID ドキュメント
- `pairs/` コレクション内のテストpairドキュメント（active=false確認後に削除）
- Firebase Authentication → 「ユーザー」タブ → `test-master@example.com` / `test-member@example.com` を削除

---

## 注意事項

- ダミーメールアドレスで問題ない（実在メール不要・メール認証なし）
- パスワードは6文字以上であれば何でも可
- Firebase Console での有効化は **一度行えば恒久的** に有効（毎回の操作不要）
