# Backpack Arsenal

**Sophisticated Backpacks** を「鞘 (saya)」として扱い、収納中の武器を充電して
電気属性攻撃を可能にする、**The four primitives and Weapons (MAW)** のアドオン MOD です。

- Minecraft 1.20.1 / Forge 47+
- 前提MOD (必須):
  - [The four primitives and Weapons](https://www.curseforge.com/minecraft/mc-mods/the-four-primitives-and-weapons) — 本体
  - [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) — 充電元
  - [Sophisticated Core](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core) — SB のコアライブラリ
  - [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) — 本体MOD要件
  - [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) — 本体MOD要件
- 任意連携: JEI

---

## 追加要素

### Voltaic Blade（ボルタイック・ブレード／刀）

- 種類: 刀 (`katana` weapon type で登録 → 本体MOD の刀モーション/納刀/JEIが自動で乗る)
- 挙動: 通常の刀より**少しだけ攻撃速度が速い**
- Sophisticated Backpack に入れると充電され、充電量に応じて電気属性攻撃が発生
- 充電中は刀身がエンチャント光沢のように光る + 耐久バーが青〜白に染まる

| ステータス | 値 |
| --- | --- |
| ベースダメージ | 7 (Tier bonus 4 + modifier 3) |
| 攻撃速度 | `-2.1` (通常刀 `-2.4` よりわずかに速い) |
| 耐久 | 無限 |
| Rarity | Rare |
| 最大チャージ | 6000 |
| 1ヒット消費 | 200 |
| バックパック内充電速度 | 2 / tick (= 40 / 秒) |

### 充電 → 電気属性 攻撃の流れ

1. プレイヤーが Sophisticated Backpack を所持し、その中に Voltaic Blade を入れる
2. 10tick 毎にプレイヤーインベントリ内のバックパック (ネスト含む) を走査
   ([`BackpackChargingHandler`](src/main/java/backpackarsenal/event/BackpackChargingHandler.java))
3. Forge `IItemHandler` capability 経由で中身の `voltaic_blade` を見つけ、NBT `BackpackCharge` を +20/tick
4. チャージ閾値に応じて NBT `ElementType=ELECTRIC` / `ElementLevel` 1〜3 を自動付与:
   - `1 ≤ charge < 1000` → Lv1
   - `1000 ≤ charge < 3000` → Lv2
   - `3000 ≤ charge ≤ 6000` → Lv3
5. 取り出して敵を攻撃 → 本体MODの `ElementalDamageEvent` が電気属性ダメージを上乗せ +
   雷パーティクル + 雷音
6. 1 ヒット 200 消費 → 0 まで使い切ったらバックパックへ戻して再充電

---

## バックパック型 鞘 (saya) モデル

`MAW` の納刀システム用カスタム鞘モデルは
[`assets/backpack_arsenal/models/custom/saya/katana/saya_voltaic_blade.json`](src/main/resources/assets/backpack_arsenal/models/custom/saya/katana/saya_voltaic_blade.json)
にあります。

- Sophisticated Backpacks 公開ソース ([1.20.x branch](https://github.com/P3pp3rF1y/SophisticatedBackpacks/tree/1.20.x))
  の `backpack_base.json` / `backpack_straps.json` / `backpack_left_pouch.json` / `backpack_right_pouch.json` /
  `backpack_front_pouch.json` を 1 ファイルに結合した **42要素 / 4テクスチャ** 構成
- テクスチャは `backpack_arsenal:` 名前空間に持ち込み済み
  ([`textures/block/backpack_cloth.png`](src/main/resources/assets/backpack_arsenal/textures/block/backpack_cloth.png) など)
- `katana_mount` という非表示の小さな立方体が含まれており、Blockbench で開いて
  この立方体を移動すると「武器がどこに付くか」を編集できる

### Blockbench での編集
1. Blockbench 起動 → `File → Open Model`
2. `saya_voltaic_blade.json` を選択
3. `katana_mount` element を掴んで、武器の柄を出したい位置へ移動
4. 上書き保存 → ゲームを再起動するか F3+T でリソース再読込

---

## ビルド / 実行

### 必要なもの
- JDK 17 (Forge 1.20.1 要件)
- 本体 MOD `the_four_primitives_and_weapons` の jar
  - `libs/local/the_four_primitives_and_weapons/1.20.1-test/the_four_primitives_and_weapons-1.20.1-test.jar`
  - 自動取得: `scripts/fetch-maw-jar.sh` (本体MODソース or GitHub から取得してビルド)

### コマンド
```sh
./gradlew build              # JAR をビルド (build/libs/backpack-arsenal-1.0.0.jar)
./run_client.sh              # 本体MOD jar を最新化してから dev クライアント起動
./run_quick.sh               # 手元の jar のまま即起動 (ネット不要)
./run_client.sh --offline    # オフラインモード
```

Windows なら同名の `.bat` を使用してください。

### 動作確認の例
```mcfunction
/give @s backpack_arsenal:voltaic_blade
/give @s sophisticatedbackpacks:backpack
```
クリエイティブ等でこの 2 つを取得 →
1. 刀をバックパックに入れる
2. インベントリを開いた状態で待機(または閉じて待機)
3. 数秒で `Charge: ... / 6000` ツールチップが増え、ある閾値で `Electric Lv X` 表示
4. 取り出して敵を殴ると雷パーティクル + 追加ダメ

---

## プロジェクト構成

```
Backpack-Arsenal/
├── build.gradle               # Forge 1.20.1 / MAW を libs/local から / SB を CurseMaven から
├── gradle.properties
├── settings.gradle
├── run_client.sh / .bat       # 本体MOD jar を fetch してから runClient
├── run_quick.sh / .bat        # 手元jarのまま即 runClient
├── scripts/fetch-maw-jar.sh   # MAW jar を本体ソース or GitHub から取得
├── libs/local/                # MAW jar 置き場 (gradle が参照)
└── src/main/
    ├── java/backpackarsenal/
    │   ├── BackpackArsenalMod.java
    │   ├── init/ArsenalItems.java
    │   ├── item/VoltaicBladeItem.java         # 短刀本体 + 充電/属性ロジック
    │   └── event/BackpackChargingHandler.java # SBの中身を走査して充電する Tick イベント
    └── resources/
        ├── META-INF/mods.toml                  # MAW + SB を mandatory
        ├── data/backpack_arsenal/
        │   ├── weapon_types/weapons.json       # MAW に katana として登録
        │   └── maw_saya/saya.jsonc             # 鞘モデル参照 (katana saya)
        └── assets/backpack_arsenal/
            ├── lang/{en_us,ja_jp}.json
            ├── textures/{item,block}/...
            └── models/
                ├── item/voltaic_blade.json
                └── custom/saya/katana/saya_voltaic_blade.json
```

---

## ライセンス / クレジット
- バックパック形状とテクスチャ(`backpack_cloth.png`, `backpack_border.png`, `leather_clips.png`)は
  [Sophisticated Backpacks](https://github.com/P3pp3rF1y/SophisticatedBackpacks) (GNU LGPL v3) 由来
- 本体 MOD ロジックは [The four primitives and Weapons](https://www.curseforge.com/minecraft/mc-mods/the-four-primitives-and-weapons) に依存
- 本アドオンのオリジナルコードは MIT
