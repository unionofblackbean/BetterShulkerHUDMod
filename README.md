# Better Shulker HUD 1.8.20

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是 Minecraft 26.1.x Fabric 客户端物品管理模组。1.8.20 使用 MaLiLib 提供与 Tweakeroo 一致的原生配置界面，并通过服务器端 QuickShulker 完成真实物品转移。

作者：`BF_skt`

HUD 设计与交互方式参考了 zeroowo24 的 BetterBundleHUD；本项目针对潜影盒、QuickShulker 和 Litematica 工作流进行了独立实现与扩展。

### 功能

- 在背包界面汇总背包和快捷栏内所有潜影盒物品。
- 合并物品与组件完全相同的条目并显示总数。
- HUD 在关闭背包时生成下一次的数量排序快照；同一次打开期间取出或存入物品只更新数量，不会实时改变物品位置。
- 使用原版创造模式的建筑方块、染色方块、自然方块、功能方块、红石方块、工具和使用物品、战斗用品、食物与饮品、原材料、刷怪蛋分类及分类内容。
- 分类栏首项提供“总览”，不经过分类筛选即可查看全部潜影盒物品；进入世界后会自动重建原版创造分类内容。
- 当前分类显示在 HUD 底部；支持分类内滚动、中文、物品 ID、全拼和拼音首字母搜索。
- HUD 面板会根据当前 GUI 缩放、窗口宽高和容器位置动态调整列数、行数、左右位置及垂直位置；分类按钮会在低高度下同步缩放。
- 创造模式中的潜影盒按钮会放在容器外侧，避免遮挡搜索栏和滚动条；安装 REI 时，HUD 会注册动态排除区域，防止 REI 物品列表绘制在面板后方。
- 普通箱子等通用容器中的潜影盒按钮优先放在容器左上方外侧，左侧空间不足时自动切换到右侧，不再遮挡容器槽位；REI 同时避让按钮区域。
- HUD 主面板、物品槽、分类按钮、搜索框、滚动条和底部控件全部采用 Minecraft 26.1 原版容器与控件风格；滚动条与物品网格保留清晰间距，背包内潜影盒开关与配方书按钮对齐，并兼容资源包替换的原版 GUI sprite。
- 潜影盒开关、最小化、分类切换和整理按钮使用原版 `ui.button.click` 点击音效。
- 按 `B`、`C` 打开 MaLiLib 配置界面，可调整 HUD 尺寸、圆角和自动补货参数，并管理全部功能开关与快捷键。
- 左键取出一个，`Shift+左键` 取出一组，右键将一组目标物品直接拿到鼠标光标；支持拖入 HUD 自由存放。
- 左键“取出一个”只使用主键与原版快速拖拽序列，不再合成右键；取出末影箱等 QuickShulker 可打开物品时不会误触发内部物品提取。
- 光标已拿着物品时，点击 HUD 只执行存放流程，不会同时触发右键提取或把潜影盒物品错误合并到玩家格子。
- 隐藏 QuickShulker 临时界面时，内部关闭容器不会关闭当前背包 Screen，也不会重新创建背包界面，从而支持连续拿取并减少背景阴影闪烁、布局重置和鼠标跳动；玩家主动关闭背包不受影响。
- QuickShulker 槽位请求使用实际容器列表位置；当 HUD 来源缓存过期时会重新扫描当前背包，从而提升 1-9 快捷栏潜影盒在移动后的识别稳定性。
- 记录取出物品的来源，使用漏斗按钮批量归还；原槽被占用时尝试原盒其他槽位。
- 漏斗归还会把属于同一个潜影盒的记录集中处理：盒子只打开一次，连续归还该盒可接收的所有物品，并在每批转移后等待服务端确认实际数量。
- 漏斗按钮在归还后继续扫描背包：背包物品若与某个随身潜影盒已有内容完全相同，会先补齐半组，再使用该盒空槽继续收纳；不会放入不含该物品的任意空盒。
- 同类整理会在同一个潜影盒界面内持续处理多个背包堆叠；目标槽不足一组时先填满当前槽，再自动使用同盒子的下一个匹配槽或空槽，不再要求玩家重复点击整理。每刻最多执行 32 次受控点击，并在服务端确认目标槽数量后才继续，减少假物品和连续操作延迟。
- 整理黑名单默认包含 `minecraft:firework_rocket`，可在 MaLiLib 的“通用”页追加物品 ID 或当前语言的完整名称；黑名单物品不会通过一键整理或历史归还被移动。
- 主动从 HUD 取出物品但背包已满时，会尝试把一组物品安全存入潜影盒以腾出一格；优先非当前手持的食物，其次工具，再考虑其他物品，并只重试原取出操作一次。若腾位用的正是目标潜影盒，会复用已经打开的容器继续取物，避免重复打开 QuickShulker 造成额外延迟。
- 满背包腾位支持可切换的黑名单/白名单模式。黑名单模式允许移动除黑名单外的物品；白名单模式只允许移动白名单物品。两份名单都为空时忽略模式，恢复食物、工具、其他物品的原始优先顺序。名单可填写当前客户端语言下的完整名称（如 `面包`、`钻石剑`）或 `namespace:item_id`，支持常用中英文分隔符和换行，并采用完整精确匹配。
- 不会自动拾取或转移地面物品。
- 自动归还排除工具、武器、装备和食物。
- 归还后读取来源槽前后差值，只按服务器实际接受的数量扣减记录。
- 切换维度、世界或服务器时清空操作队列、归还记录和 HUD 缓存。
- HUD 使用背包指纹、哈希聚合和搜索结果缓存，背包未变化时不重复解析潜影盒。
- 安装 Litematica 后，轻松放置缺少目标方块时会从随身潜影盒取出可容纳的数量，并自动选中该方块。补块会解析明确的背包目标槽并执行精确移动，不再依赖可能无响应的快速移动。
- Litematica 补块会预先检查背包容量；部分容量只取可容纳数量，背包满载时复用安全腾位逻辑并在后台继续，无法腾位时立即提示而不再等待容器超时。
- 当前主手的可堆叠物品低于设定阈值时，自动从随身潜影盒补充组件完全相同的物品；可设置触发阈值、单次补货量和扫描间隔。

### 诊断日志

在 MaLiLib 配置界面的“通用”页启用“诊断日志”，只复现一次失败的取放操作，然后退出游戏。日志位于游戏实例的 `logs/latest.log`，相关行统一带有 `[Better Shulker HUD Diagnostics]` 前缀和同一次操作的编号。日志会记录物品 ID、数量和槽位，但不会记录服务器地址或玩家名称；提交日志后建议关闭此开关。

### 依赖

客户端：Minecraft 26.1.x、Fabric Loader、Fabric API、MaLiLib 0.28.2、QuickShulker 3.0.0-26.1 或更高版本、Better Shulker HUD。

服务端：Fabric Loader、Fabric API、QuickShulker 3.0.0-26.1 或更高版本。Litematica 为可选客户端依赖。

```powershell
$env:JAVA_HOME='Java 25 路径'
.\gradlew.bat clean build
```

## English

Better Shulker HUD is a client-side inventory manager for every Minecraft 26.1.x patch release on Fabric. Version 1.8.20 uses MaLiLib to provide the same native configuration UI structure as Tweakeroo while QuickShulker performs server-authoritative item transfers.

Author: `BF_skt`

The HUD design and interaction model reference BetterBundleHUD by zeroowo24. This project independently implements and extends that design for shulker boxes, QuickShulker, and Litematica workflows.

### Features

- Aggregates all shulker contents carried in the inventory and hotbar.
- Combines identical items and components and displays their total count.
- Builds the next count-based order snapshot when the inventory closes. During an open screen session, extraction and storage update counts without reordering entries.
- Uses the vanilla Creative inventory contents for Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools & Utilities, Combat, Food & Drinks, Ingredients, and Spawn Eggs.
- Adds an Overview category that always shows every shulker item and rebuilds vanilla Creative category contents after joining a world.
- Shows the active category in the HUD footer and supports in-category localized name, item ID, full-Pinyin, and Pinyin-initial search.
- Adapts HUD columns, rows, side placement, vertical placement, and category icon size to the current GUI scale, window dimensions, and container position.
- Places the shulker toggle outside Creative inventory controls and registers a dynamic exclusion zone when REI is installed, preventing REI entries from rendering behind the HUD.
- Places the shulker toggle outside the upper-left edge of ordinary containers, falling back to the right when needed, so it never covers a container slot. REI also excludes the toggle area.
- Uses the native Minecraft 26.1 container and widget style for the HUD panel, item slots, category buttons, search box, scrollbar, and footer controls. The scrollbar is clearly separated from the item grid, and the inventory shulker toggle aligns with the recipe-book button while respecting resource-pack replacements for vanilla GUI sprites.
- Plays the vanilla `ui.button.click` sound for the shulker toggle, minimize control, category selection, and organize button.
- Press `B`, then `C` to open the native MaLiLib configuration screen for HUD sizing, corner radius, automatic restock settings, feature toggles, and hotkeys.
- Extracts one item with left click, one stack with `Shift+left click`, or moves a stack directly to the mouse cursor with right click; items can also be dropped onto the HUD for storage.
- The left-click single-item path uses only primary clicks and the vanilla quick-craft sequence. It no longer synthesizes a secondary click that could make QuickShulker extract contents from an Ender Chest or another quick-openable item.
- When the cursor already carries an item, HUD clicks are reserved for storage and cannot simultaneously trigger extraction or merge a shulker stack into a player slot.
- When transient QuickShulker screens are hidden, internal container closure neither closes nor recreates the current inventory Screen. Repeated extraction remains available without background-shadow flashes, layout resets, or cursor movement; normal player-initiated inventory closing is unaffected.
- Sends QuickShulker the actual container-list position and rescans the live inventory when a cached HUD source moved, improving hotbar slots 1-9 recognition across inventory layouts.
- Tracks source shulkers and batch-returns items, falling back to another slot in the original box.
- Groups return-history records by source shulker. One open container session returns every compatible record for that box, with server confirmation after each transfer batch.
- After returning tracked items, the hopper action consolidates inventory stacks into carried shulkers that already contain an identical item. It fills partial stacks first, then empty slots in the same matching box, never an unrelated empty box.
- Matching organization keeps one shulker screen open while draining multiple matching inventory stacks. It fills a partial target, then uses another matching or empty slot in the same box for overflow, sends at most 32 controlled clicks per tick, and waits for server-confirmed target counts before continuing.
- The organize blacklist excludes `minecraft:firework_rocket` by default. Add item IDs or exact localized names on the General tab; blacklisted items are excluded from both one-click organize and return-history transfers.
- If an active HUD extraction finds a full inventory, it tries to store one non-selected stack to free a slot, preferring food, then tools, then other items, and retries the original extraction only once. When the clearance target is the same shulker being extracted from, the already-open QuickShulker menu is reused to avoid a second open/close round trip.
- Full-inventory clearance supports selectable blacklist and whitelist modes. Blacklist mode allows every item except blacklist entries; whitelist mode allows only whitelist entries. If both lists are empty, the mode is ignored and the original food, tool, then other-item priority is restored. Lists accept exact names from the current client language (such as `Bread` or `Diamond Sword`) and `namespace:item_id` values, with common English/Chinese separators and new lines.
- Ground-item pickup and automatic ground-item transfer are intentionally not implemented.
- Excludes tools, weapons, equipment, and food from automatic returns.
- Reconciles each return against the actual source-slot count change before updating tracked quantities.
- Clears pending operations, return history, and HUD caches when changing dimensions, worlds, or servers.
- Caches shulker scans, hash-based aggregation, and filtered search results using an inventory fingerprint.
- With Litematica installed, an Easy Place pick that lacks its target block extracts the amount the inventory can accept and selects it. Restocking resolves a concrete player slot and performs an exact transfer instead of relying on a potentially inert quick move.
- Litematica restocking checks capacity before opening the shulker, supports partial capacity, reuses safe full-inventory clearance in the background, and fails immediately when no slot can be freed instead of waiting for a container timeout.
- Automatically refills a low, stackable main-hand item from an identical stack in a carried shulker, with configurable threshold, amount, and scan interval.

### Diagnostic logging

Enable **Diagnostic logging** on the General tab of the MaLiLib config screen, reproduce one failed transfer, and then exit the game. Submit the instance's `logs/latest.log`; relevant entries share the `[Better Shulker HUD Diagnostics]` prefix and an operation ID. The log includes item IDs, counts, and slots, but not player names or server addresses. Disable the option again after collecting the log.

### Requirements

Client: Minecraft 26.1.x, Fabric Loader, Fabric API, MaLiLib 0.28.2, QuickShulker 3.0.0-26.1 or newer, and Better Shulker HUD.

Server: Fabric Loader, Fabric API, and QuickShulker 3.0.0-26.1 or newer. Litematica is an optional client dependency.

```powershell
$env:JAVA_HOME='path to Java 25'
.\gradlew.bat clean build
```

The project is released under CC0-1.0. BetterBundleHUD and its original author zeroowo24 are credited as the HUD design reference. Pinyin search uses PinIn and pinyin4j; see `LICENSE-PinIn`.
