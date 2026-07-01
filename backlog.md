# papazon-dash バックログ

- 作成日: 2026-06-28
- 最終更新日: 2026-07-02
- cmd_id: cmd_594i v1.0.0 GO殿正式承認後の正式バックログ登録(PO requirement:)
- 参照: memory project_papazon_dash_versioning_strategy

## v1.0.0(MVP完遂・殿正式承認2026-06-28 22:57)

cmd_594i 19フェーズ完遂・全機能PASS(機能4 アイテム編集UIのみSKIP=v1.1.0送り)・APK SHA256:bf2220257f6a53a079c28092e0c2d3e7c5ff32b9d259271c67c8d864aabbf021。

軍師正式QC verdict PASS(p14b・confidence 0.96・Critical/Major/Minor 0件)。memory 3軸厳守=設計書ベースライン照合+手動回避ボタン除外+スクショ実体検証。

## v1.0.1(CF cascade削除deploy完遂・2026-07-02)

cmd_663z Phase 1-4完遂: CF onPairDeactivated deploy + CTL-2/3/6実機実証 (Minor M1解消)。
軍師Opus QC PASS_WITH_MINOR (2026-07-02・Critical/Major 0件・Minor 2件継続)。

| 項目 | 内容 | 状態 | 出典 |
|------|------|------|------|
| CF onPairDeactivated deploy | unpair時items cascade削除(CTL-3)+pair doc active=false(CTL-6) CF実装・asia-northeast1 deploy済 | ✅ 完了 | cmd_663z Phase4 |
| CTL-2 ITEMS_LISTENER_CLEANED_UP | unpair後snapshotListener解除+再sign_in時listener未登録 実機実証 | ✅ 完了 | cmd_663z Phase2 |
| CTL-3 ITEMS_SUBCOL_DELETED | CF cascade削除3件→0件実機実証(Cloud Logging証跡: ~10秒以内) | ✅ 完了 | cmd_663z Phase4 |
| CTL-6 PAIR_DOC_DEACTIVATED | active=false CF発火確認(~10秒) | ✅ 完了 | cmd_663z Phase4 |
| 運用ドキュメント整備 | Google Sign-In未実装によるユーザー混乱回避(再install後再ペアリング必要)+操作手順注意喚起 | 未着手 | 軍師p14b指摘C2 |

### 継続Minor(v1.1.0以降で要対応)

| Minor | 内容 | 判定 |
|-------|------|------|
| app→CF E2E未実証 | unpair()→Firestore write→CF発火の完全アプリフロー未実証(Admin SDK代替) | v1.1.0以降 |
| CTL-6 member edge case | member側unpair時のpair doc ownership確認(ownerのみactive=false可) | v1.1.0以降 |

## v1.1.0(追加機能・将来予定)

| 項目 | 内容 | 出典 |
|------|------|------|
| 機能4 アイテム編集UI | FirebaseRepository.updateItemName+EditItemScreen追加・v6設計書記載分の最後の1項目 | ash6 p12b調査+ash1 p13e計画 |
| 表示名変更UI(編集系機能束ね) | 設定画面から displayName いつでも変更可能。FirebaseRepository.updateDisplayName+編集ダイアログ+snapshotListener経由でpair相手UI即時反映。機能4アイテム編集UIと合わせて「編集系機能セット」で1リリース | 殿指示2026-07-02 |
| 基盤A Google Sign-In | Anonymous Auth代用→正式Google Sign-In実装・データ永続化+ペアリング再現性確保 | 軍師p13c/p13f継承 |
| **アプリ内アップデート機構(追加Phase)** | GitHub Releases APIで最新tag取得→BuildConfig.VERSION_NAMEと比較→設定画面「更新確認」ボタン→ダウンロードURL提示。speedtest型 ota_deploy.sh 方式を殿+奥様運用向けに転用。**クラス共有ベース側はshared APK不成立(google-services.json焼込)ゆえ対象外・READMEで git pull手順明記で代替** | 殿指示2026-07-01定例後・memory feedback_speedtest_apk_archive参照 |

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
- 軍師QC verdict(p14b): reports/20260628+cmd_594i_p14b_re_re_qc.md
- 全機能スクショ: outbox/20260628+cmd_594i_test_report/screenshots/p14_*.png(18枚)
- APK: outbox/20260627+cmd_594i_papazon_dash_v6_poc.apk(SHA256:bf222025)
