# Better Shulker HUD 2.2.2 for Minecraft 26.2

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是 Minecraft 26.2 Fabric 客户端物品管理模组。它集中显示玩家随身潜影盒中的物品，并通过 QuickShulker 或 AxShulkers 后端执行真实取放操作。

### 主要功能

- 汇总背包和快捷栏内所有潜影盒内容，合并组件相同的物品并显示总数。
- 支持原版创造模式分类、总览、滚动、物品 ID、中文、全拼和拼音首字母搜索。
- 支持单个或整组取出、拿到鼠标、拖入存放、连续取出和连续存放。
- 支持来源记忆、批量归还、同类物品整理、满背包安全腾位和可配置黑白名单。
- 支持主手和副手自动补货、水桶补水桶、单件不死图腾补货，以及副手物品安全交换。
- 支持 QuickShulker `3.0.2-26.2` 和 AxShulkers 服务端插件后端。
- 可选兼容 Litematica 轻松放置补货、REI、JEI、ModernUI 和 CozyUI+ 资源包。
- 当背包和目标潜影盒都没有空位时，Litematica 补块会在常规腾位失败后，把当前手中整组物品与目标潜影盒槽位直接交换；不修改投影识别和 Easy Place 根本判定。
- HUD 开关只在玩家背包显示，可与配方书共存，并支持首次按住直接拖动、自动避让槽位、完成和重置位置。
- 使用 MaLiLib 原生设置界面配置功能、快捷键、HUD 布局和诊断日志。

### 26.2 适配内容

- 迁移到 Minecraft `26.2` 的 `Minecraft.gui.screen()` 和 `Gui.setScreen(...)` 界面 API。
- 迁移到 26.2 的物品族 API，例如彩色羊毛分类图标。
- 更新 Fabric API、MaLiLib、QuickShulker、REI 和 JEI 编译接口。
- 修复旧 26.2 构建注入已删除的 `Minecraft.setScreen` 而导致的启动崩溃。
- 修复自动取放物品时潜影盒界面短暂闪现的问题。
- HUD 数量在取放过程中直接跟随活动潜影盒菜单更新，不再等待容器关闭。
- 优化 HUD 内容缓存，避免每帧深度扫描所有潜影盒内容造成动画卡顿。
- 兼容 Item Scroller `0.32.1`：右键 HUD 物品直接显示并最终确认到鼠标光标，不再显示中间背包槽跳转。
- Item Scroller 在 HUD 物品上滚轮一次取出一个；连续滚动会安全排队，按住“转移整组”修饰键（默认左 Shift）可一次取出当前整组。
- 遵循 Item Scroller 的单件/整组开关、反向滚动和位置感知方向设置，物品耗尽后的多余滚轮请求会静默丢弃。
- 光标转移期间隐藏并锁定内部临时槽，避免滚轮或拖动操作干扰服务端事务。
- 补齐配方书界面的拖动事件和玩家背包的松开事件，避免 HUD 按钮无法保存位置。
- QuickShulker 建议版本明确为 `3.0.2-26.2`，避免旧网络 API 被错误识别为兼容。
- 2.2.2 修复满背包且满潜影盒时轻松放置无法替换手中方块的问题；整组交换会校验两侧物品、数量、光标和服务端最终状态。

### 依赖

客户端必需：

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.3` 或更高版本
- Fabric API `0.156.0+26.2`
- MaLiLib `0.29.2`

使用 QuickShulker 后端时，客户端和服务端应使用 QuickShulker `3.0.2-26.2`。也可以选择兼容的 AxShulkers 服务端插件后端。Litematica、REI、JEI 和 ModernUI 均为可选项。

### 构建与测试

```powershell
$env:JAVA_HOME='path to Java 25'
.\gradlew.bat clean build clientGameTestClasses
.\gradlew.bat runClientGameTest
```

客户端 GameTest 覆盖水桶补货、主手和副手补货、物品移动不误补货、满背包/满潜影盒整组交换、手动存入、副手交换、HUD 按钮拖动和配方书共存。

## English

Better Shulker HUD is a Fabric client inventory manager for Minecraft 26.2. It aggregates items stored in carried shulker boxes and performs authoritative transfers through QuickShulker or an AxShulkers server backend.

### Highlights

- Aggregated shulker contents with vanilla creative categories and Pinyin-aware search.
- Single-item, stack, cursor, queued extraction, drag-store, source return, and matching-item organization workflows.
- Main-hand and offhand restocking, water-bucket replacement, single-item Totem restocking, and safe offhand swapping.
- Optional Litematica Easy Place restocking, Item Scroller 0.32.1 HUD wheel transfers, REI, JEI, ModernUI, and CozyUI+ resource-pack compatibility.
- When both the inventory and source shulker are full, version 2.2.2 can exchange the complete selected hotbar stack with the requested shulker slot after normal clearance fails, without altering Litematica projection matching or core Easy Place rules.
- A player-inventory-only HUD toggle that coexists with the recipe book and supports immediate drag positioning.
- Native MaLiLib configuration, hotkeys, layout controls, and diagnostic logging.

### Requirements

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.3` or newer
- Fabric API `0.156.0+26.2`
- MaLiLib `0.29.2`
- QuickShulker `3.0.2-26.2` on client and server when using the QuickShulker backend

Litematica, REI, JEI, ModernUI, and the AxShulkers backend are optional integrations.

The project is released under CC0-1.0. BetterBundleHUD and its original author zeroowo24 are credited as the HUD design reference. Pinyin search uses PinIn and pinyin4j; see `LICENSE-PinIn`.
