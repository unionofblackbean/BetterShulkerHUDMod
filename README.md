# Better Shulker HUD 1.7.0

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是 Minecraft 26.1.x Fabric 客户端物品管理模组。1.7.0 使用 MaLiLib 提供与 Tweakeroo 一致的原生配置界面，并通过服务器端 QuickShulker 完成真实物品转移。

作者：`BF_skt`

HUD 设计与交互方式参考了 zeroowo24 的 BetterBundleHUD；本项目针对潜影盒、QuickShulker 和 Litematica 工作流进行了独立实现与扩展。

### 功能

- 在背包界面汇总背包和快捷栏内所有潜影盒物品。
- 合并物品与组件完全相同的条目并显示总数。
- 使用原版创造模式的建筑方块、染色方块、自然方块、功能方块、红石方块、工具和使用物品、战斗用品、食物与饮品、原材料、刷怪蛋分类及分类内容。
- 当前分类显示在 HUD 底部；支持分类内滚动、中文、物品 ID、全拼和拼音首字母搜索。
- HUD 面板、分类按钮和控件支持可调像素圆角。
- 按 `B`、`C` 打开 MaLiLib 配置界面，可调整 HUD 尺寸、圆角、自动拾取范围、扫描间隔和跟踪超时，并管理全部功能开关与快捷键。
- 左键取出一组，右键取出一个；支持拖入 HUD 自由存放。
- 记录取出物品的来源，使用漏斗按钮批量归还；原槽被占用时尝试原盒其他槽位。
- 自动归还排除工具、武器、装备和食物。
- 归还后读取来源槽前后差值，只按服务器实际接受的数量扣减记录。
- 切换维度、世界或服务器时清空操作队列、归还记录和 HUD 缓存。
- HUD 使用背包指纹、哈希聚合和搜索结果缓存，背包未变化时不重复解析潜影盒。
- 普通背包没有剩余容量时，临时将合适的可堆叠物品转入随身潜影盒；实际拾到的任意物品会立即存入潜影盒，原物品随后精确返回原背包格，并继续处理附近下一组掉落物。
- 安装 Litematica 后，轻松放置缺少目标方块时会从随身潜影盒取出一组，并自动选中该方块。第一次操作补充方块，下一次操作放置。

### 依赖

客户端：Minecraft 26.1.x、Fabric Loader、Fabric API、MaLiLib 0.28.2、QuickShulker 3.0.0-26.1 或更高版本、Better Shulker HUD。

服务端：Fabric Loader、Fabric API、QuickShulker 3.0.0-26.1 或更高版本。Litematica 为可选客户端依赖。

```powershell
$env:JAVA_HOME='Java 25 路径'
.\gradlew.bat clean build
```

## English

Better Shulker HUD is a client-side inventory manager for every Minecraft 26.1.x patch release on Fabric. Version 1.7.0 uses MaLiLib to provide the same native configuration UI structure as Tweakeroo while QuickShulker performs server-authoritative item transfers.

Author: `BF_skt`

The HUD design and interaction model reference BetterBundleHUD by zeroowo24. This project independently implements and extends that design for shulker boxes, QuickShulker, and Litematica workflows.

### Features

- Aggregates all shulker contents carried in the inventory and hotbar.
- Combines identical items and components and displays their total count.
- Uses the vanilla Creative inventory contents for Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools & Utilities, Combat, Food & Drinks, Ingredients, and Spawn Eggs.
- Shows the active category in the HUD footer and supports in-category localized name, item ID, full-Pinyin, and Pinyin-initial search.
- Adds configurable pixel-rounded corners to HUD panels, category tabs, and controls.
- Press `B`, then `C` to open the native MaLiLib configuration screen for HUD sizing, corner radius, pickup range, scan interval, tracking timeout, feature toggles, and hotkeys.
- Extracts a stack with left click or one item with right click; items can be dropped onto the HUD for storage.
- Tracks source shulkers and batch-returns items, falling back to another slot in the original box.
- Excludes tools, weapons, equipment, and food from automatic returns.
- Reconciles each return against the actual source-slot count change before updating tracked quantities.
- Clears pending operations, return history, and HUD caches when changing dimensions, worlds, or servers.
- Caches shulker scans, hash-based aggregation, and filtered search results using an inventory fingerprint.
- When the regular inventory is full, temporarily moves a suitable stack into a carried shulker, stores whichever nearby item actually occupies the freed slot, restores the original stack to its exact inventory slot, and continues with the next nearby item group.
- With Litematica installed, an easy-place pick that lacks its target block extracts one stack from a carried shulker and selects it. The first action restocks; the next places the block.

### Requirements

Client: Minecraft 26.1.x, Fabric Loader, Fabric API, MaLiLib 0.28.2, QuickShulker 3.0.0-26.1 or newer, and Better Shulker HUD.

Server: Fabric Loader, Fabric API, and QuickShulker 3.0.0-26.1 or newer. Litematica is an optional client dependency.

```powershell
$env:JAVA_HOME='path to Java 25'
.\gradlew.bat clean build
```

The project is released under CC0-1.0. BetterBundleHUD and its original author zeroowo24 are credited as the HUD design reference. Pinyin search uses PinIn and pinyin4j; see `LICENSE-PinIn`.
