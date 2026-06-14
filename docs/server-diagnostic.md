# Arsenal Backpack 右クリック不発 — サーバー診断手順

サバ/クライアント環境で「arsenal_backpack を 手持ち右クリックしても UI が 開かない」
問題を 切り分けるための一時診断ビルドの使い方。

## 経緯

- 症状: arsenal_backpack を 手持ち右クリックしても `ArsenalBackpackContainer`
  の UI が 開かない。 設置 ( ブロック化 ) は 動く。
- 仮説: Curios / MAW など 上流の `PlayerInteractEvent.RightClickItem` listener
  が arsenal_backpack を 「back カーリオ自動装着 候補」 として早期に
  `setCanceled(true)` してしまい、 `ArsenalBackpackItem.use()` まで届かない。
- 対策: `ArsenalBackpackRightClickHandler` を `HIGHEST` priority で 先回り登録し、
  自前で `NetworkHooks.openScreen` してから cancel して 上流を抑える。

## 診断ビルド

```
./gradlew build -Pmod_version_override=1.0.2-serverdiag --no-daemon
```

成果物: [build/libs/backpack-arsenal-1.0.2-serverdiag.jar](../build/libs/backpack-arsenal-1.0.2-serverdiag.jar)

このビルドは [ArsenalBackpackRightClickHandler.java](../src/main/java/backpackarsenal/event/ArsenalBackpackRightClickHandler.java) に
診断ログを 仕込んだ版。 通常ビルドより [arsenal_rc] タグ付きの log が 出る。

## 配置

サーバー / 参加クライアント の **両方** の `mods/` に `backpack-arsenal-1.0.2-serverdiag.jar`
を 入れる。 古い `backpack-arsenal-*.jar` は **全削除** ( forge が 重複 mod を
読もうとして 衝突する )。

## 再現

1. サーバー起動
2. クライアントから 接続
3. arsenal_backpack ( ElectronTier ) を 手持ちにして 右クリック
4. サーバー側 `logs/latest.log` を 確認

## 期待 log

正常に handler が 走った場合:

```
[arsenal_rc] RightClickItem fire side=CLIENT hand=MAIN_HAND sneaking=false canceled=false player=<name>
[arsenal_rc] RightClickItem fire side=SERVER hand=MAIN_HAND sneaking=false canceled=false player=<name>
[arsenal_rc] openScreen called for slot=N ctx=main
```

## 切り分け表

| log の出方 | 原因 | 対策 |
|---|---|---|
| 3 行ぜんぶ出るが UI 開かない | handler は走ってるので openScreen 後の client 側問題 | MenuType / Screen 登録、 BackpackContext.fromBuffer の dispatch、 ClientSetupEvent の MenuScreens.register を 確認 |
| SERVER 側だけ出ない | server で 上流 listener が 全部 cancel | MAW / Curios の RightClickItem subscriber を 探して priority / receiveCanceled を 見直す |
| CLIENT 側 `canceled=true` で 来る | Curios が 予測 cancel ( HIGHEST より 早い handler ) | `receiveCanceled = true` で listen するか、 EventBus 登録順を 早める |
| 何も出ない | そもそも RightClickItem に 到達してない | RightClickEmpty / RightClickBlock を 同時に log して どの event flow に 入ってるか確認 |

## diagnostic を 剥がす

問題が 特定できて 修正完了したら ArsenalBackpackRightClickHandler 内の
`LOGGER.info("[arsenal_rc] ...")` 行 ( 2 箇所 ) を 削除する。 中身の cancel /
openScreen ロジックは そのまま 残す ( これが 本番の 修正本体 )。
