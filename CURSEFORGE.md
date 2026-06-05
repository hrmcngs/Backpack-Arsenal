# Backpack Arsenal

> *Sheathe your blade. Charge it up. Strike with lightning.*
> *納刀する。チャージする。雷で斬る。*

📖 [English](#english) · [日本語](#日本語)

---
## English

**Backpack Arsenal** is an addon for [The four primitives and Weapons (MAW)](https://www.curseforge.com/minecraft/mc-mods) that turns [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) into **charging sheaths** for a brand-new electric katana, the **Voltaic Blade**.

Carry a backpack. Drop the blade inside. The longer it sits sheathed, the harder it hits when you draw it again.

### ⚡ Features

#### Voltaic Blade
A katana built on MAW's weapon framework, with an **Electric** elemental damage layer that scales with stored charge.

- Stores **charge** in NBT, raising elemental level (Lv 1 → Lv 3) as it fills up
- Sneak + right-click for a **lightning slam-down** that calls a simulated bolt at the AOE center
- Right-click slot wired to **Voltaic Dodge** — a backward dash with 1-second invulnerability and electric sparks
- Charge bar shown directly in the tooltip

#### Arsenal Backpack (Electron)
A custom Sophisticated Backpacks tier built around the Voltaic Blade.

- **Custom 3D model** when placed in the world (collision shape traces the model)
- Wears like a normal backpack via **Curios "back"** slot
- Stores blades in the regular inventory — anything inside auto-charges over time
- Compatible with all Sophisticated Backpacks **upgrades** (Stack / Pickup / Magnet / Refill / ...)

#### Voltaic Charger Upgrade
Slot it into the backpack's upgrade column to multiply charge speed.

| Upgrades installed | Charge rate |
|---|---|
| 1 | ×2 |
| 2 | ×3 |
| 3 | ×4 (max) |

#### Basic Backpack
A no-frills backpack without the charging mechanic — just a clean custom skin for those who don't want the Voltaic integration.

#### Single-button Draw & Sheath
Tap **R** (MAW's weapon-wheel key) once to pull the highest-charged Voltaic Blade out of any equipped Backpack. Tap again to slide it back in. Hold to open the regular wheel and pick a specific blade.

### 📜 Crafting

| Item | Pattern |
|---|---|
| Voltaic Blade | Amethyst Shard / Iron Ingot / Stick (vertical, sword shape) |
| Arsenal Backpack (Electron) | SB Leather Backpack + Amethyst Shard + Redstone Block + Iron Ingot |
| Voltaic Charger Upgrade | Amethyst Shard + Copper Ingot + Redstone + Leather |

All recipes appear in **JEI** if you have it installed.

### 📦 Required Mods

| Mod | Required |
|---|---|
| [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | ✅ Hard dependency |
| [Sophisticated Core](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core) | ✅ Hard dependency |
| [The four primitives and Weapons](https://www.curseforge.com/minecraft/mc-mods) | ✅ Hard dependency |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | ✅ Hard dependency |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | ✅ Hard dependency |
| [Just Enough Items (JEI)](https://www.curseforge.com/minecraft/mc-mods/jei) | Optional (for recipe display) |

### 🛠 Versions

- **Minecraft:** 1.20.1
- **Forge:** 47.x or newer

### ⚙ Configuration

Open `config/backpack_arsenal.json` after first launch.

```json
{
  "inventorySlots": 9,
  "upgradeSlots": 4
}
```

Reload on the fly with `/backpack_arsenal reload` (OP only).

### 🎮 Tips & Tricks

- The Voltaic Blade only charges while **inside** a backpack you're carrying — drop it in, fight with something else, come back to a fully charged katana.
- Stacking **Voltaic Charger Upgrades** is the fastest way to refill between fights.
- Wear the Arsenal Backpack in the **Curios back slot** for the cleanest weapon-wheel experience — Draw/Sheath then prefers the worn backpack as the source.
- The Sneak + Right-click lightning slam scales with current charge; save it for tough mobs.

### ❓ Known Limitations

- Placed Arsenal Backpack blocks use a **static custom 3D model** — Sophisticated Backpacks' dynamic visuals (tank / battery indicators, open animation, item-on-top display) are not rendered. Functionality (inventory, upgrades, charge) is fully preserved.
- Existing world saves predating the rename from `arsenal_backpack` to `arsenal_backpack_electron` will see the old item as unknown. Re-craft from the new recipe.

### 📷 Showcase

*(Add screenshots / GIFs here on the CurseForge page)*

### 💬 Issues & Feedback

Found a bug? Have a feature request? Open an issue on the [GitHub tracker](https://github.com/hrmcngs/Backpack-Arsenal/issues).

### 📄 License

MIT — see [LICENSE](https://github.com/hrmcngs/Backpack-Arsenal/blob/main/LICENSE).

---

*Backpack Arsenal is a fan-made addon. "The four primitives and Weapons" and "Sophisticated Backpacks" are trademarks of their respective authors.*

---

## 日本語

**Backpack Arsenal** は [The four primitives and Weapons (MAW)](https://www.curseforge.com/minecraft/mc-mods) のアドオン MOD です。[Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) を「**充電用の鞘**」として扱い、新しい電気属性の刀 — **Voltaic Blade (ボルタイック・ブレード)** に電力を貯めて雷で敵を斬ります。

バックパックを背負う。中に刀を入れる。長く納刀しているほど、抜いたときの威力が上がる — そういう MOD です。

### ⚡ 主な機能

#### Voltaic Blade
MAW 武器フレームワークの上に作られた刀。充電量に応じて **電気属性ダメージ** がレベルアップします。

- **NBT に充電量を保存** — チャージが満ちると elemental level が Lv1 → Lv3 に上昇
- **Sneak + 右クリック** で「**雷振り下ろし**」 — AOE 中心に模擬落雷を呼び込む
- **右クリックスロット = 雷影回避** — 後方へクイックダッシュ + 1 秒間無敵 + 電気スパーク
- 充電バーがツールチップにそのまま表示される

#### Arsenal Backpack (Electron)
Voltaic Blade と一緒に使う想定で作った独自グレードのバックパック。

- 設置時は **カスタム 3D モデル** で描画 (当たり判定も 3D モデルをなぞる)
- **Curios の back スロット** で背負える
- 通常スロットに入れた刀は時間経過で自動充電
- Sophisticated Backpacks の **アップグレード** (Stack / Pickup / Magnet / Refill ...) と完全互換

#### Voltaic Charger Upgrade
バックパックのアップグレード列に挿すと充電速度が倍率アップします。

| 装着数 | 充電倍率 |
|---|---|
| 1 | ×2 |
| 2 | ×3 |
| 3 | ×4 (最大) |

#### Basic Backpack
充電機能なしの普通のバックパック。電気要素は要らないけど見た目だけ流用したい人向け。

#### R キー 1 つで抜刀・納刀
MAW のウェポンウィール用キー (**R**) を**短押し**するだけで、背負ったバックパック内の最も充電量が多い Voltaic Blade を引き抜きます。もう一度押すと納刀。**長押し**で通常のウィールが開いて、複数の刀を選べます。

### 📜 クラフト

| アイテム | レシピ |
|---|---|
| Voltaic Blade | アメジストの欠片 / 鉄インゴット / 棒 (縦並びの剣形) |
| Arsenal Backpack (Electron) | SB の革バックパック + アメジスト + レッドストーンブロック + 鉄インゴット |
| Voltaic Charger Upgrade | アメジスト + 銅インゴット + レッドストーン + 革 |

**JEI** が入っていればすべてのレシピが自動表示されます。

### 📦 必須 / 任意 MOD

| MOD | 必須 |
|---|---|
| [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | ✅ 必須 |
| [Sophisticated Core](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core) | ✅ 必須 |
| [The four primitives and Weapons](https://www.curseforge.com/minecraft/mc-mods) | ✅ 必須 |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | ✅ 必須 |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | ✅ 必須 |
| [Just Enough Items (JEI)](https://www.curseforge.com/minecraft/mc-mods/jei) | 任意 (レシピ表示用) |

### 🛠 対応バージョン

- **Minecraft:** 1.20.1
- **Forge:** 47.x 以降

### ⚙ コンフィグ

初回起動後に `config/backpack_arsenal.json` を編集できます。

```json
{
  "inventorySlots": 9,
  "upgradeSlots": 4
}
```

`/backpack_arsenal reload` でホットリロード (OP 限定)。

### 🎮 Tips

- Voltaic Blade はあなたが**所持しているバックパック内**でのみ充電されます。落としたバックパックの中身は充電されません。
- 戦闘の合間に Voltaic Charger Upgrade を重ねれば、最短で再充電できます。
- Arsenal Backpack を **Curios の back スロット** に装備すると、ウィール経由の Draw/Sheath が背負いバックパックを優先します。
- Sneak + 右クリックの雷振り下ろしは現在の充電量に比例。強敵戦のために温存しておくと有効です。

### ❓ 既知の制限

- 設置した Arsenal Backpack は **静的なカスタム 3D モデル**で描画されます。Sophisticated Backpacks の動的演出 (tank / battery バー、open アニメ、上に乗せた表示アイテム) は失われます。インベントリ / アップグレード / 充電は完全動作。
- リネーム前 (`arsenal_backpack`) のワールドではアイテムが "unknown" になります。新レシピで作り直してください。

### 📷 スクリーンショット

*(CurseForge ページ側で画像 / GIF を追加してください)*

### 💬 バグ報告 / 要望

[GitHub Issues](https://github.com/hrmcngs/Backpack-Arsenal/issues) でお願いします。

### 📄 ライセンス

MIT — [LICENSE](https://github.com/hrmcngs/Backpack-Arsenal/blob/main/LICENSE) を参照。

---
