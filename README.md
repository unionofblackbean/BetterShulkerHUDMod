# Better Shulker HUD 1.5.1

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是 Minecraft 26.1.x Fabric 客户端物品管理模组。1.5.1 以 MaLiLib 的初始化、客户端 tick 和世界加载生命周期为基础，并通过服务器端 QuickShulker 完成真实物品转移。

作者：`BF_skt`

HUD 设计与交互方式参考了 zeroowo24 的 BetterBundleHUD；本项目针对潜影盒、QuickShulker 和 Litematica 工作流进行了独立实现与扩展。

### 功能

- 在背包界面汇总背包和快捷栏内所有潜影盒物品。
- 合并物品与组件完全相同的条目并显示总数。
- 支持分类、滚动、中文、物品 ID、全拼和拼音首字母搜索。
- 左键取出一组，右键取出一个；支持拖入 HUD 自由存放。
- 记录取出物品的来源，使用漏斗按钮批量归还；原槽被占用时尝试原盒其他槽位。
- 自动归还排除工具、武器、装备和食物。
- 归还后读取来源槽前后差值，只按服务器实际接受的数量扣减记录。
- 切换维度、世界或服务器时清空操作队列、归还记录和 HUD 缓存。
- HUD 使用背包指纹、哈希聚合和搜索结果缓存，背包未变化时不重复解析潜影盒。
- 安装 Litematica 后，轻松放置缺少目标方块时会从随身潜影盒取出一组，并自动选中该方块。第一次操作补充方块，下一次操作放置。

### 依赖

客户端：Minecraft 26.1.x、Fabric Loader、Fabric API、MaLiLib 0.28.2、QuickShulker 3.0.0-26.1 或更高版本、Better Shulker HUD。

服务端：Fabric Loader、Fabric API、QuickShulker 3.0.0-26.1 或更高版本。Litematica 为可选客户端依赖。

```powershell
$env:JAVA_HOME='Java 25 路径'
.\gradlew.bat clean build
```

## English

Better Shulker HUD is a client-side inventory manager for every Minecraft 26.1.x patch release on Fabric. Version 1.5.1 uses MaLiLib for initialization, client ticks, and world lifecycle handling while QuickShulker performs server-authoritative item transfers.

Author: `BF_skt`

The HUD design and interaction model reference BetterBundleHUD by zeroowo24. This project independently implements and extends that design for shulker boxes, QuickShulker, and Litematica workflows.

### Features

- Aggregates all shulker contents carried in the inventory and hotbar.
- Combines identical items and components and displays their total count.
- Supports categories and localized name, item ID, full-pinyin, and pinyin-initial search.
- Extracts a stack with left click or one item with right click; items can be dropped onto the HUD for storage.
- Tracks source shulkers and batch-returns items, falling back to another slot in the original box.
- Excludes tools, weapons, equipment, and food from automatic returns.
- Reconciles each return against the actual source-slot count change before updating tracked quantities.
- Clears pending operations, return history, and HUD caches when changing dimensions, worlds, or servers.
- Caches shulker scans, hash-based aggregation, and filtered search results using an inventory fingerprint.
- With Litematica installed, an easy-place pick that lacks its target block extracts one stack from a carried shulker and selects it. The first action restocks; the next places the block.

### Requirements

Client: Minecraft 26.1.x, Fabric Loader, Fabric API, MaLiLib 0.28.2, QuickShulker 3.0.0-26.1 or newer, and Better Shulker HUD.

Server: Fabric Loader, Fabric API, and QuickShulker 3.0.0-26.1 or newer. Litematica is an optional client dependency.

```powershell
$env:JAVA_HOME='path to Java 25'
.\gradlew.bat clean build
```

The project is released under CC0-1.0. BetterBundleHUD and its original author zeroowo24 are credited as the HUD design reference. Pinyin search uses PinIn and pinyin4j; see `LICENSE-PinIn`.
