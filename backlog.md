# papazon-dash バックログ

- 作成日: 2026-06-28
- 最終更新日: 2026-07-02
- 参照: design_v6.md / design_v6.1.md

## v1.0.0(MVP完遂・2026-06-28 22:57)

cmd_594i 19フェーズ完遂・全機能PASS(機能4 アイテム編集UIのみSKIP=v1.1.0送り)・APK SHA256:bf2220257f6a53a079c28092e0c2d3e7c5ff32b9d259271c67c8d864aabbf021。

QC verdict PASS (confidence 0.96・Critical/Major/Minor 0件)。3軸厳守=設計書ベースライン照合+手動回避ボタン除外+スクショ実体検証。

## v1.0.1(master exclusive unpair + CF cascade削除・2026-07-02)

cmd_663z/cmd_663z2 完遂: CF onPairDeactivated deploy + CTL-2/3/6実機実証 + master exclusive unpair構造実装。Minor M1/M2 完全解消。

QC: PASS_WITH_MINOR (Opus・Critical/Major 0件 → M1/M2完全解消・Minor 0件)
APK SHA256 (v1.0.1 unsigned): `7fae81730dff0a39a63b8ff0483fb988cf34fb070156ecf2ad81ca86b8cd9de4`

| 項目 | 内容 | 状態 | 出典 |
|------|------|------|------|
| master exclusive unpair (Firestore rules) | pairs/{id} update: master_uidのみactive=false更新可。member joinはmember_uid未設定時のみ許可 | ✅ 完了 | cmd_663z2 Phase2 |
| master exclusive unpair (Android UI) | MEMBER時ペア解除ボタン非表示+「解除はmasterに依頼」表示 | ✅ 完了 | cmd_663z2 Phase3 |
| SMV再モデル化 (papazon_dash_v2.smv) | ROLE変数追加・CTL-7 member edge case TRUE検証 | ✅ 完了 | cmd_663z2 Phase5 |
| CF onPairDeactivated deploy | unpair時items cascade削除(CTL-3)+pair doc active=false(CTL-6) CF実装・asia-northeast1 deploy済 | ✅ 完了 | cmd_663z Phase4 |
| CTL-2 ITEMS_LISTENER_CLEANED_UP | unpair後snapshotListener解除+再sign_in時listener未登録 実機実証 | ✅ 完了 | cmd_663z Phase2 |
| CTL-3 ITEMS_SUBCOL_DELETED | CF cascade削除3件→0件実機実証(Cloud Logging証跡: ~10秒以内) | ✅ 完了 | cmd_663z Phase4 |
| CTL-6 PAIR_DOC_DEACTIVATED | active=false CF発火確認(~10秒) | ✅ 完了 | cmd_663z Phase4 |
| CTL-7 MEMBER_CANNOT_DEACTIVATE | AG(ROLE=member & PAIR_STATE=paired → PAIR_DOC_ACTIVE=TRUE) NuSMV TRUE確認 | ✅ 完了 | cmd_663z2 Phase5 |
| 運用ドキュメント整備 | Google Sign-In未実装によるユーザー混乱回避(再install後再ペアリング必要)+操作手順注意喚起 | 未着手 | QC verdict(p14b) C2 |

### Minor M1/M2 解消記録

| Minor | 解消方法 | 判定 |
|-------|---------|------|
| M1: app→CF E2E未実証 | master exclusive unpair設計で構造的解消+Firestore rules deploy済 | ✅ v1.0.1解消 |
| M2: CTL-6 member edge case | Firestore rules: master_uidのみactive=false可 → 構造的ブロック | ✅ v1.0.1解消 |

## v1.0.2(Email/Password Auth実装・2026-07-02 完全完了)

cmd_665 全フェーズ完遂。Firebase Console Email/Password Auth有効化(manual admin step)完了済み。実機2台E2E全11ステップ通過。

| 項目 | 内容 | 状態 | 出典 |
|------|------|------|------|
| Email/Password Auth 実装 | SignInScreen(新規登録/ログインタブUI) + SignInViewModel(AuthUiState/AuthMode) | ✅ 完了 | cmd_665 Phase3 |
| Anonymous→Email/Password linking | linkWithCredential()でUID保持+既存data引き継ぎ。失敗時はfreshアカウント作成フォールバック | ✅ 完了 | cmd_665 Phase3 |
| pairId Firestore永続化 | setPaired()でusers/{uid}.pairId書込 / unpair()でnullクリア → 再ログイン後pair復元可 | ✅ 完了 | cmd_665 Phase3 |
| signOut バグ修正 | AppNavigation Settings: appViewModel.signOut()呼出追加(従来はnavigateのみでFirebase auth未サインアウト) | ✅ 完了 | cmd_665 Phase3 |
| 自動サインイン(SplashScreen) | SplashViewModel.tryAutoSignIn() → 既存email/password userは起動時自動ログイン+pairId復元→メイン画面直遷移 | ✅ 完了 | cmd_665 Phase3 |
| SMV v3(papazon_dash_v3.smv) | AUTH_TYPE/PAIR_ID_PERSISTED追加 + CTL-8/CTL-8b/CTL-9追加。10性質全てNuSMV検証PASS | ✅ 完了 | cmd_665 Phase7 |
| Firebase Console Email/Password Auth有効化 | Authentication > Sign-in method > Email/Password 有効化 (manual admin step completed) | ✅ 完了 | cmd_665 Phase2 |
| 実機E2E(2台) | 端末1(office@dummy.test/okusan/MASTER)/端末2(home@dummy.test/tono/MEMBER) 全11ステップ通過 | ✅ 完了 | cmd_665 Phase6 |
| Firestoreゴミデータcleanup | E2Eテスト後 users/pairs コレクション削除済み | ✅ 完了 | cmd_665 Phase6 |

### versionCode/Name更新

versionCode: 1 → 3 / versionName: "1.0.0-v6-poc" → "1.0.2"

### 実機E2E証跡 (2026-07-02)

- 端末1 新規登録 → ペアリング → アイテム追加(milk) → SignOut → 再ログイン → pairId復元 ✅
- 端末2 新規登録 → ペアリング → milkリアルタイム反映 → MEMBER設定「ペア解除はマスターに依頼してください」(master exclusive) ✅

### v1.0.1からの移行メモ

- Anonymous Auth UI (匿名sign-in) を完全廃止 → Email/Password UIに置換
- 既存Anonymous userが残っていれば linkWithCredential()で継続使用可

## v1.1.0(追加機能・将来予定)

| 項目 | 内容 | 出典 |
|------|------|------|
| 機能4 アイテム編集UI | FirebaseRepository.updateItemName+EditItemScreen追加・v6設計書記載分の最後の1項目 | cmd_594 p12b/p13e |
| 表示名変更UI(編集系機能束ね) | 設定画面から displayName いつでも変更可能。FirebaseRepository.updateDisplayName+編集ダイアログ+snapshotListener経由でpair相手UI即時反映。機能4アイテム編集UIと合わせて「編集系機能セット」で1リリース | 仕様要求2026-07-02 |
| 基盤A Google Sign-In | Anonymous Auth代用→正式Google Sign-In実装・データ永続化+ペアリング再現性確保 | QC p13c/p13f継承 |
| **アプリ内アップデート機構(追加Phase)** | GitHub Releases APIで最新tag取得→BuildConfig.VERSION_NAMEと比較→設定画面「更新確認」ボタン→ダウンロードURL提示。**クラス共有ベース側はshared APK不成立(google-services.json焼込)ゆえ対象外・READMEで git pull手順明記で代替** | 仕様要求2026-07-01 |

## 既知制約(v1.0.0時点)

- Anonymous Auth ベース=再install後 uid変更ゆえ再ペアリング必要(運用ドキュメントで注意喚起・v1.0.1)
- アイテム編集UI未実装=作成後の名前修正不可(完了後再作成で代替・v1.1.0)

## リリース直前バグ修正履歴(cmd_594i内で解消)

| 修正 | フェーズ | 内容 |
|------|---------|------|
| joinByCode PERMISSION_DENIED | p13e | Firestore rule resource.data.get('member_uid',null)==null 修正 |
| listenToItems FAILED_PRECONDITION | p13e | Firestoreインデックス snake_case+COLLECTION_GROUP → camelCase+COLLECTION 修正 |
| snapshotListener未登録(手動回避ボタン依存) | p14 | FirebaseRepository.startListeningForMemberJoin()+PairingInviteScreen LaunchedEffect(isPaired)追加 |

## 関連成果物

- 設計書: projects/smartse/K01/papazon-dash/20260620+cmd_594_design_v6.md
- テストレポート(P14版): reports/20260628+cmd_594i_test_report.md
- QC verdict(p14b): reports/20260628+cmd_594i_p14b_re_re_qc.md
- 全機能スクショ: outbox/20260628+cmd_594i_test_report/screenshots/p14_*.png(18枚)
- APK: outbox/20260627+cmd_594i_papazon_dash_v6_poc.apk(SHA256:bf222025)
