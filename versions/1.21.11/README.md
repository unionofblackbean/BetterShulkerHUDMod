# Better Shulker HUD 2.0.5 for Minecraft 1.21.11

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是一个 Fabric 客户端潜影盒物品管理模组。它会在容器界面旁汇总显示随身潜影盒中的物品，并通过 QuickShulker 或 AxShulkers 完成由服务器确认的真实物品转移。

作者：`BF_skt`

HUD 的设计与交互参考了 zeroowo24 的 BetterBundleHUD。本项目面向潜影盒、QuickShulker、MaLiLib 和 Litematica 工作流进行独立实现与扩展。

### 主要功能

- 汇总随身潜影盒内容，合并同类物品并显示总数量。
- 支持创造模式分类、中文/英文名称、物品 ID 和拼音搜索。
- 左键取一个，`Shift + 左键`取一组，右键取到鼠标；支持拖入 HUD 存放。
- 记录来源潜影盒并批量归还，也可整理背包内同类物品。
- 支持整理黑名单、满背包腾位、自动补货和诊断日志。满背包腾位按潜影盒的总可用容量判断，并可在一次服务器确认的快速移动中分配到多个潜影盒槽位。
- 使用水桶后先把空桶安全移到其他槽位，再从潜影盒补回水桶；不会把空桶当作补货目标。
- 支持主手和副手的不死图腾类单件物品补货，副手补货可以单独关闭。
- 鼠标悬停 HUD 物品时按可配置的 `F` 键可安全拿到副手；副手已有不同物品时会与空背包暂存槽交换。HUD 开关按钮仅在按 `E` 打开的玩家背包中显示。
- 可选兼容 Litematica Easy Place 和 REI。
- 使用 MaLiLib 设置界面，并适配不同 GUI 缩放和窗口尺寸。

### 1.21.x 兼容矩阵

Minecraft 1.21.x 存在多次不兼容的客户端 API 变化，因此本项目提供八个独立 JAR。请按游戏版本选择，不能混用。

| Minecraft | 构建目标 | MaLiLib | QuickShulker |
| --- | --- | --- | --- |
| `1.21` | `mc1.21` | `0.21.10` | `2.0.x / 3.0.x` for `1.21.1` |
| `1.21.1` | `mc1.21.1` | `0.21.10` | `2.0.x / 3.0.x` for `1.21.1` |
| `1.21.2-1.21.3` | `mc1.21.2-1.21.3` | `0.22.8` | `2.1.x-2.2.x / 3.0.x`, matching MC |
| `1.21.4` | `mc1.21.4` | `0.23.5` | `2.3.x / 3.0.x` for `1.21.4` |
| `1.21.5` | `mc1.21.5` | `0.24.3` | `2.4.x / 3.0.x` for `1.21.5` |
| `1.21.6-1.21.8` | `mc1.21.6-1.21.8` | `0.25.7` | `2.5.x-2.7.x / 3.0.x`, matching MC |
| `1.21.9-1.21.10` | `mc1.21.9-1.21.10` | `0.26.7` | `2.8.x-2.9.x / 3.0.x`, matching MC |
| `1.21.11` | `mc1.21.11` | `0.27.16` | `2.10.x / 3.0.x` for `1.21.11` |

所有版本均要求 Java 21、Fabric Loader 0.16.14 或更高版本，以及对应 Minecraft 版本的 Fabric API。QuickShulker 2.x 与 3.0.x 均受支持，但必须使用与当前 Minecraft 小版本匹配的构建。客户端需安装 Better Shulker HUD、Fabric API、MaLiLib 和 QuickShulker；服务器需安装 Fabric API 与 QuickShulker 才能执行真实取放操作。Litematica 和 REI 是可选客户端依赖。

### 构建

```powershell
$env:JAVA_HOME='path to Java 21'
.\gradlew.bat clean build
```

## English

Better Shulker HUD is a Fabric client mod for managing items stored in carried shulker boxes. It displays their combined contents beside container screens and uses QuickShulker or AxShulkers for server-authoritative item transfers.

Author: `BF_skt`

The HUD design and interaction model reference BetterBundleHUD by zeroowo24. This project independently implements and extends that design for shulker boxes, QuickShulker, MaLiLib, and Litematica workflows.

### Features

- Aggregates carried shulker contents, merges identical items, and shows total counts.
- Supports Creative inventory categories, localized names, item IDs, and Pinyin search.
- Extracts one item with left click, one stack with `Shift + left click`, or items to the cursor with right click.
- Tracks source shulkers, batch-returns items, and organizes matching inventory stacks.
- Supports organize exclusions, full-inventory clearance, automatic restocking, and diagnostic logs.
- After a water bucket is used, safely relocates the empty bucket and restores a water bucket from a carried shulker.
- Restocks consumed death-protection items such as Totems of Undying in the main hand and offhand; offhand restocking has a separate toggle.
- Press configurable `F` over a HUD item to move it safely to the offhand. A different existing offhand item is exchanged into an empty inventory staging slot. The HUD toggle button appears only in the player inventory opened with `E`.
- Optionally integrates with Litematica Easy Place and REI.
- Uses a MaLiLib configuration screen and adapts to GUI scale and window size.

### 1.21.x Compatibility

Minecraft 1.21.x contains several incompatible client API boundaries, so this project ships eight independent JARs. Select the JAR matching your exact game version; the artifacts are not interchangeable.

See the compatibility matrix above for the matching MaLiLib and QuickShulker versions. Every build requires Java 21, Fabric Loader 0.16.14 or newer, and the matching Fabric API. Install Better Shulker HUD, Fabric API, MaLiLib, and QuickShulker on the client. Install Fabric API and QuickShulker on the server for actual item transfers. Litematica and REI are optional client dependencies.

### Build

```powershell
$env:JAVA_HOME='path to Java 21'
.\gradlew.bat clean build
```

Released under CC0-1.0. PinIn and pinyin4j are used for Pinyin search; see `LICENSE-PinIn`.
