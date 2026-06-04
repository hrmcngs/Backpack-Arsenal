## Summary

<!-- 変更内容の概要を 1-3 行で -->

## Release type (CurseForge アップロード時の release type)

マージ後 CurseForge へアップロードする際の **Release Type** を 1 つチェックしてください。
チェック無しの場合は **Alpha** がデフォルト適用されます。

- [ ] **Alpha** — 既知バグあり / 実験的 / WIP
- [ ] **Beta** — 機能完成、広めのテスト段階
- [ ] **Release** — 安定版、一般推奨

> 注意: CurseForge への実際のアップロードは、main にマージ後に
> **`curseforge` 環境の Required Reviewers による承認** が入った時点で実行されます。
> 承認しない限りアップロードされません (build artifact は常に作成される)。

## Test plan

- [ ] ローカル dev 環境でビルド成功
- [ ] dev サーバ起動・該当機能の動作確認
- [ ] 既存ユースケースのリグレッション確認
