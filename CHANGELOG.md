# Changelog

## 1.0.5

### ⚔️ Voltaic Blade — elemental overhaul
- **Element switching (Electric ⇄ Thunder).** New item **Voltaic Element Core** — anvil-combine it with a Voltaic Blade to toggle the element. Charge, capacitors, enchantments and the element-level enhancement all carry over.
- **Thunder mode balance.** Thunder **charges ~2× slower** in a backpack and **costs ~2× charge per hit** (fewer hits) than Electric — stronger but harder to sustain.
- **Element Level enhancement (anvil).** Raise/lower the blade's element level yourself:
  - Glowstone dust → +1 (stackable for a batch), Glowstone block → jump toward the cap.
  - Redstone → −1, Redstone block → back to 0.
- **Elemental damage now scales with element level.** The **physical/base damage is unchanged** — only the elemental portion grows.
- **Element level is now stable** — it no longer drifts with max-charge/charge-ratio; it's a flat base + your enhancement.
- **Skills consume charge.** Voltaic Slam-down and Voltaic Dodge now draw charge on use.
- **Tooltip:** shows **hits-when-full** (max charge ÷ per-hit cost) so capacitor/element changes are immediately visible; charged element line is unified with MAW's and tagged "(charged)".

### ⚡ Forge Energy
- **Fixed: FE not being extracted** via cables / direct connection. The placed backpack now reliably exposes its energy (self-healing against Sophisticated Backpacks' cap invalidation).
- **Uncapped generation & export.** Internal FE buffer and generation are now 64-bit; high-multiplier setups no longer overflow, and Mekanism export is no longer clamped to the 32-bit FE limit.

### 🎒 Backpack / Blade handling
- **Fixed: could sheathe a Voltaic Blade into a backpack but not draw it back out** (draw detection now reads the synced count instead of the empty client-side inventory).

### 🧩 JEI
- Anvil recipes for element switching and element-level enhancement (dust/block, up/down), plus a Voltaic Element Core info page.

---

*Older releases: see the commit history on [GitHub](https://github.com/hrmcngs/Backpack-Arsenal/commits/main).*
