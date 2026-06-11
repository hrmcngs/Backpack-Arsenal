# Backpack Arsenal

> *Sheathe your blade. Charge it up. Power your factory.*
> *納刀する。チャージする。工場を動かす。*

📖 [English](#english) · [日本語](#日本語)

---

## English

**Backpack Arsenal** turns [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) into **charging sheaths** for a brand-new electric katana — the **Voltaic Blade** — and into **placeable Forge Energy generators** that power [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism) machines or any FE device.

Designed as an addon for [The four primitives and Weapons (MAW)](https://www.curseforge.com/minecraft/mc-mods).

![Placed Arsenal Backpack on a Mekanism Energy Cube](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/01_hero_backpack_on_cube.png)

### ⚡ Highlight: Forge Energy Generation

Place an Arsenal Backpack with a Voltaic Blade inside and it becomes a Forge Energy generator that outputs at multipliers scaling with the upgrades you stack. Mekanism's universal cables connect to it directly via the native `IStrictEnergyHandler` API — **no FE↔J conversion loss**.

![Mekanism Energy Cube receiving 81.28 MFE/t](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/02_mekanism_input.png)

The backpack inventory UI shows a small FE icon at the bottom; hover for the current per-tick output rate. Numbers scale automatically (FE → kFE → MFE → GFE → TFE).

### 🌱 Voltaic Growth Charger — Infinite Upgrade Scaling

The standout upgrade: a charger that starts at Level 0 (no boost) and can be **infinitely leveled on an anvil** with redstone. Flat cost per level — no escalating ramp.

| Material | Levels |
|---|---|
| 1 redstone dust | +1 level |
| 1 redstone block | +9 levels |
| 1 stack of redstone in Survival | up to +39 levels (anvil XP cap) |
| 1 stack in Creative | up to +1000 levels per click |

![Voltaic Growth Charger Lv 4400 with full charger upgrade slots](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/03_growth_charger_lv4400.png)

Multiplier contribution = Level. Stack 4 Lv-10000 chargers in a single backpack and you've got a multi-GFE/t reactor in a single block.

![Anvil leveling: Voltaic Growth Charger + redstone block](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/04_anvil_leveling.png)

### 🗡️ Voltaic Blade

A katana built on MAW's weapon framework with an Electric elemental damage layer that scales with stored charge.

- Stores charge in NBT, raising elemental level (Lv 1 → Lv 3) as it fills up
- Sneak + right-click for a **lightning slam-down** that calls a simulated bolt at the AOE center
- Right-click slot wired to **Voltaic Dodge** — a backward dash with 1-second invulnerability + electric sparks
- Charge bar shown directly in the tooltip
- Apply **Voltaic Capacitor Upgrades (I/II/III)** on an anvil to boost max charge capacity (+256 / +512 / +1024 per stage, up to 5 stages)

### 🎒 Arsenal Backpack (Electron)

A custom Sophisticated Backpacks tier built around the Voltaic Blade.

- **Custom 3D model** when placed in the world
- Wears via the **Curios "back"** slot
- Stores blades in normal inventory slots — anything inside auto-charges over time
- **Placed as a block, the backpack becomes a FE generator** (see Highlight above)
- Compatible with all Sophisticated Backpacks **upgrades** (Stack / Pickup / Magnet / Refill / ...)

### 🔌 Voltaic Charger Upgrade (Tier 0 → 5)

Slot it into the backpack's upgrade column. Multiplier contribution = (tier + 1)²:

| Tier | Multiplier contribution |
|------|------------------------|
| 0 (base) | +1 |
| I | +4 |
| II | +9 |
| III | +16 |
| IV | +25 |
| V | +36 |

Final multiplier = 1 + sum of all installed chargers (Growth Charger contributions add to this sum).

### Single-button Draw & Sheath

Tap **R** (MAW's weapon-wheel key) once to pull the highest-charged Voltaic Blade out of any equipped backpack. Tap again to slide it back in. Hold to open the regular wheel and pick a specific blade.

### 📜 Crafting

| Item | How |
|---|---|
| Voltaic Blade | Vanilla crafting table — *or* MAW Rarity Forge (copper / amethyst / iron vertical) |
| Arsenal Backpack (Electron) | Vanilla crafting table — *or* MAW Rarity Forge (8 leather + 1 amethyst block) |
| Voltaic Charger Upgrade tier N | Crafting table |
| Voltaic Growth Charger | Tier 0 charger + 4 oak saplings + 4 redstone (plus pattern) |
| Voltaic Capacitor Upgrade I/II/III | Crafting table |

All recipes appear in **JEI** with detailed info pages, including anvil-leveling for the Growth Charger.

### 📦 Required / Optional Mods

| Mod | Required |
|---|---|
| [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | ✅ Hard dependency |
| [Sophisticated Core](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core) | ✅ Hard dependency |
| [The four primitives and Weapons](https://www.curseforge.com/minecraft/mc-mods) | ✅ Hard dependency |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | ✅ Hard dependency |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | ✅ Hard dependency |
| [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism) | ✅ Hard dependency — FE generation core |
| [Just Enough Items (JEI)](https://www.curseforge.com/minecraft/mc-mods/jei) | Optional — recipe & info pages |

### 🛠 Versions

- **Minecraft:** 1.20.1
- **Forge:** 47.x or newer

### ⚙ Configuration

`config/backpack_arsenal.json` (auto-generated on first launch):

```json
{
  "inventorySlots": 9,
  "upgradeSlots": 4,
  "feCapacity": 2147483647,
  "feGenPerTick": 2000,
  "feMaxExtract": 2147483647
}
```

Hot-reload with `/backpack_arsenal reload` (OP only).

### 💬 Issues & Feedback

Found a bug? Open an issue on the [GitHub tracker](https://github.com/hrmcngs/Backpack-Arsenal/issues).

### 📄 License

MIT — see [LICENSE](https://github.com/hrmcngs/Backpack-Arsenal/blob/main/LICENSE).

---

*Backpack Arsenal is a fan-made addon. "The four primitives and Weapons", "Sophisticated Backpacks", and "Mekanism" are trademarks of their respective authors.*

---

## 日本語

**Backpack Arsenal** は [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) を「**充電用の鞘**」として扱い、 さらに **設置時は Forge Energy 発電機** にもなるアドオン MOD です。 新しい電気属性の刀 — **Voltaic Blade (ボルタイック・ブレード)** に電力を貯めて雷で敵を斬り、 同時に [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism) の機械を回したり他の FE 機械に給電できます。

[The four primitives and Weapons (MAW)](https://www.curseforge.com/minecraft/mc-mods) のアドオンとして設計されています。

![設置 Arsenal Backpack を Mekanism Energy Cube に直結](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/01_hero_backpack_on_cube.png)

### ⚡ メイン機能: Forge Energy 発電

Voltaic Blade を入れた Arsenal Backpack を設置すると Forge Energy 発電機になります。 upgrade を盛るほど発電倍率が上がり、 Mekanism universal cable は native `IStrictEnergyHandler` 経由で直結 — **FE↔J 変換ロス無し**。

![Mekanism Energy Cube が 81.28 MFE/t 受電中](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/02_mekanism_input.png)

backpack を開くと下に小さな FE アイコン。 ホバーで現在の per-tick 発電量を表示 ( FE → kFE → MFE → GFE → TFE 自動切換 )。

### 🌱 Voltaic Growth Charger — 無限強化

目玉アップグレード。 Lv 0 では効果無し、 アンビルでレッドストーンを喰わせて **無限にレベル up** 可能。 **どのレベルでもフラットコスト**:

| 素材 | レベル up 量 |
|---|---|
| レッドストーン 1 個 | +1 lv |
| レッドストーンブロック 1 個 | +9 lv |
| サバイバル: 1 スタック | 経験値 39 lv 上限まで 一気に +39 lv |
| クリエイティブ: 1 スタック | 1 クリックで最大 +1000 lv |

![Lv 4400 の Growth Charger ( 全 4 upgrade slot 装着済 )](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/03_growth_charger_lv4400.png)

寄与 = Level。 4 スロットに Lv 10000 を 4 枚挿せば、 1 ブロックで数 GFE/t の発電機。

![アンビルでレベル up — レッドストーンブロック投入時](https://raw.githubusercontent.com/hrmcngs/Backpack-Arsenal/main/docs/images/04_anvil_leveling.png)

### 🗡️ Voltaic Blade

MAW 武器フレームワークの上に作られた刀。 充電量に応じて **電気属性ダメージ** がレベルアップします。

- NBT に充電量を保存 — チャージが満ちると elemental level が Lv1 → Lv3 に上昇
- **Sneak + 右クリック** で「**雷振り下ろし**」 — AOE 中心に模擬落雷を呼び込む
- **右クリックスロット = 雷影回避** — 後方へクイックダッシュ + 1 秒間無敵 + 電気スパーク
- 充電バーがツールチップにそのまま表示される
- **Voltaic Capacitor Upgrade (I/II/III)** をアンビルで合成すると最大充電量が増加 ( +256 / +512 / +1024、 最大 5 段 )

### 🎒 Arsenal Backpack (Electron)

Voltaic Blade と一緒に使う想定で作った独自グレードのバックパック。

- 設置時は **カスタム 3D モデル** で描画
- **Curios の back スロット** で背負える
- 通常スロットに入れた刀は時間経過で自動充電
- **設置すると Forge Energy 発電機にもなる** ( 上記参照 )
- Sophisticated Backpacks の **アップグレード** ( Stack / Pickup / Magnet / Refill ... ) と完全互換

### 🔌 Voltaic Charger Upgrade ( tier 0 ~ 5 )

upgrade スロットに挿すと **刀の充電速度** と **設置 backpack の FE 発電量** の倍率が上がります。 寄与 = (tier + 1)²:

| Tier | 倍率寄与 |
|------|---------|
| 0 (base) | +1 |
| I | +4 |
| II | +9 |
| III | +16 |
| IV | +25 |
| V | +36 |

最終倍率 = 1 + 全 charger ( Growth 含む ) 寄与の合計。

### R キー 1 つで抜刀・納刀

MAW のウェポンウィール用キー ( **R** ) を **短押し** するだけで、 背負ったバックパック内の最も充電量が多い Voltaic Blade を引き抜きます。 もう一度押すと納刀。 **長押し** で通常のウィールが開いて、 複数の刀を選べます。

### 📜 クラフト

| アイテム | レシピ |
|---|---|
| Voltaic Blade | 通常作業台 — または MAW レアリティ作業台 ( 銅 / アメジスト / 鉄 縦並び ) |
| Arsenal Backpack (Electron) | 通常作業台 — または MAW レアリティ作業台 ( 革 × 8 + アメジストブロック × 1 ) |
| Voltaic Charger Upgrade tier N | 作業台 |
| Voltaic Growth Charger | tier 0 charger + 苗木 × 4 + レッドストーン × 4 ( + 字 ) |
| Voltaic Capacitor Upgrade I/II/III | 作業台 |

**JEI** が入っていれば、 すべてのレシピと anvil 強化の info ページが自動表示されます。

### 📦 必須 / 任意 MOD

| MOD | 必須 |
|---|---|
| [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | ✅ 必須 |
| [Sophisticated Core](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core) | ✅ 必須 |
| [The four primitives and Weapons](https://www.curseforge.com/minecraft/mc-mods) | ✅ 必須 |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | ✅ 必須 |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | ✅ 必須 |
| [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism) | ✅ 必須 ( FE 発電の核 ) |
| [Just Enough Items (JEI)](https://www.curseforge.com/minecraft/mc-mods/jei) | 任意 ( レシピ / info 表示 ) |

### 🛠 対応バージョン

- **Minecraft:** 1.20.1
- **Forge:** 47.x 以降

### ⚙ コンフィグ

`config/backpack_arsenal.json` ( 初回起動時に自動生成 ):

```json
{
  "inventorySlots": 9,
  "upgradeSlots": 4,
  "feCapacity": 2147483647,
  "feGenPerTick": 2000,
  "feMaxExtract": 2147483647
}
```

`/backpack_arsenal reload` でホットリロード ( OP 限定 )。

### 💬 バグ報告 / 要望

[GitHub Issues](https://github.com/hrmcngs/Backpack-Arsenal/issues) でお願いします。

### 📄 ライセンス

MIT — [LICENSE](https://github.com/hrmcngs/Backpack-Arsenal/blob/main/LICENSE) を参照。
