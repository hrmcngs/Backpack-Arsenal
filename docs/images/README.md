# CurseForge ページ用スクリーンショット配置

CURSEFORGE_PAGE.md から参照する画像を **このディレクトリに** 以下のファイル名で配置してください。

ファイル名は `CURSEFORGE_PAGE.md` の `<img src=>` / `![]()` で固定参照されているので、 同じ名前で配置すれば自動で表示されます。

## 必要な 4 ファイル

| ファイル名 | 写真内容 | サイズ目安 |
|-----------|---------|-----------|
| `01_hero_backpack_on_cube.png` | 設置 Arsenal Backpack が Mekanism Energy Cube ( 黒系 ) の上に乗っている屋外シーン ( 草原 / 木 / 空 ) | 横 1500px 程度 |
| `02_mekanism_input.png` | Energy Cube の FE icon hover tooltip ( "Input: 81.28 MFE/t / Max Output: Infinite/t / Unit: FE" ) | 横 1000px |
| `03_growth_charger_lv4400.png` | Backpack UI で Voltaic Growth Charger Lv 4400 のツールチップ。 4 upgrade slot に charger が並んでる左カラムも見える | 横 2000px |
| `04_anvil_leveling.png` | アンビル UI: 左に Voltaic Growth Charger、 中央にレッドストーンブロック × 64、 右に出力、 "Enchantment Cost: 576" 表示 | 横 1400px |

## 配置手順

```bash
# プロジェクトルートで
cp ~/Desktop/<your_screenshot>.png docs/images/01_hero_backpack_on_cube.png
cp ~/Desktop/<your_screenshot>.png docs/images/02_mekanism_input.png
cp ~/Desktop/<your_screenshot>.png docs/images/03_growth_charger_lv4400.png
cp ~/Desktop/<your_screenshot>.png docs/images/04_anvil_leveling.png

git add docs/images/*.png
git commit -m "docs: add CurseForge page screenshots"
git push origin main
```

push 後すぐに raw URL 経由でアクセス可能:
```
https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/<filename>.png
```

CurseForge 側で description プレビューすればこの URL が画像として描画される。

## 補足

- フォーマットは PNG 推奨 ( JPG も可 )
- 1 ファイル < 2 MB ( CurseForge が遅くなるので大きすぎる画像は避ける )
- 1 つでも欠けた場合は CurseForge ページで「壊れた画像」アイコンが出るので、 公開前に GitHub 上で 4 ファイル揃ってるか確認
