# Better Shulker HUD 1.8.8

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是 Minecraft 26.1.x Fabric 客户端物品管理模组。1.8.8 使用 MaLiLib 提供与 Tweakeroo 一致的原生配置界面，并通过服务器端 QuickShulker 完成真实物品转移。

作者：`BF_skt`

HUD 设计与交互方式参考了 zeroowo24 的 BetterBundleHUD；本项目针对潜影盒、QuickShulker 和 Litematica 工作流进行了独立实现与扩展。

### 功能

- 在背包界面汇总背包和快捷栏内所有潜影盒物品。
- 合并物品与组件完全相同的条目并显示总数。
- 使用原版创造模式的建筑方块、染色方块、自然方块、功能方块、红石方块、工具和使用物品、战斗用品、食物与饮品、原材料、刷怪蛋分类及分类内容。
- 分类栏首项提供“总览”，不经过分类筛选即可查看全部潜影盒物品；进入世界后会自动重建原版创造分类内容。
- 当前分类显示在 HUD 底部；支持分类内滚动、中文、物品 ID、全拼和拼音首字母搜索。
- 背包界面提供可配置显示的末影箱按钮；携带末影箱时可通过 QuickShulker 打开真实末影箱，并使用相同的分类、搜索、数量合并、排序和 HUD 取放交互。
- 点击背包中的末影箱按钮后，背包界面保持正常显示和交互，末影箱 HUD 显示在背包左侧。QuickShulker 的真实末影箱容器仅在后台短暂打开以同步或执行取放，不再同时显示第二个末影箱窗口。
- HUD 面板会根据当前 GUI 缩放、窗口宽高和容器位置动态调整列数、行数、左右位置及垂直位置；分类按钮会在低高度下同步缩放。
- HUD 面板、分类按钮和控件支持可调像素圆角。
- HUD 主面板、页签、搜索框、滚动区及底部控件使用统一的双层阴影，增强界面层次。
- 按 `B`、`C` 打开 MaLiLib 配置界面，可调整 HUD 尺寸、圆角和自动补货参数，并管理全部功能开关与快捷键。
- 左键取出一个，`Shift+左键` 取出一组，右键将一组目标物品直接拿到鼠标光标；支持拖入 HUD 自由存放。
- 光标已拿着物品时，点击 HUD 只执行存放流程，不会同时触发右键提取或把潜影盒物品错误合并到玩家格子。
- QuickShulker 槽位请求使用实际容器列表位置；当 HUD 来源缓存过期时会重新扫描当前背包，从而提升 1-9 快捷栏潜影盒在移动后的识别稳定性。
- 记录取出物品的来源，使用漏斗按钮批量归还；原槽被占用时尝试原盒其他槽位。
- 漏斗按钮在归还后继续扫描背包：背包物品若与某个随身潜影盒已有内容完全相同，会先补齐半组，再使用该盒空槽继续收纳；不会放入不含该物品的任意空盒。
- 同类整理每次只处理一组，半组转移每刻最多执行 8 次点击，并在服务端确认目标槽数量后才继续，减少假物品和连续操作延迟。
- 主动从 HUD 取出物品但背包已满时，会尝试把一组物品安全存入潜影盒以腾出一格；优先非当前手持的食物，其次工具，再考虑其他物品，并只重试原取出操作一次。
- 满背包腾位支持物品 ID 黑白名单。黑名单物品绝不自动移走；白名单优先于食物、工具和其他物品；同一 ID 同时出现时黑名单优先。设置项接受逗号、空格或换行分隔的 `namespace:item_id`。
- 不会自动拾取或转移地面物品。
- 自动归还排除工具、武器、装备和食物。
- 归还后读取来源槽前后差值，只按服务器实际接受的数量扣减记录。
- 切换维度、世界或服务器时清空操作队列、归还记录和 HUD 缓存。
- HUD 使用背包指纹、哈希聚合和搜索结果缓存，背包未变化时不重复解析潜影盒。
- 安装 Litematica 后，轻松放置缺少目标方块时会从随身潜影盒取出一组，并自动选中该方块。第一次操作补充方块，下一次操作放置。
- 当前主手的可堆叠物品低于设定阈值时，自动从随身潜影盒补充组件完全相同的物品；可设置触发阈值、单次补货量和扫描间隔。

### 依赖

客户端：Minecraft 26.1.x、Fabric Loader、Fabric API、MaLiLib 0.28.2、QuickShulker 3.0.0-26.1 或更高版本、Better Shulker HUD。

服务端：Fabric Loader、Fabric API、QuickShulker 3.0.0-26.1 或更高版本。Litematica 为可选客户端依赖。

```powershell
$env:JAVA_HOME='Java 25 路径'
.\gradlew.bat clean build
```

## English

Better Shulker HUD is a client-side inventory manager for every Minecraft 26.1.x patch release on Fabric. Version 1.8.8 uses MaLiLib to provide the same native configuration UI structure as Tweakeroo while QuickShulker performs server-authoritative item transfers.

Author: `BF_skt`

The HUD design and interaction model reference BetterBundleHUD by zeroowo24. This project independently implements and extends that design for shulker boxes, QuickShulker, and Litematica workflows.

### Features

- Aggregates all shulker contents carried in the inventory and hotbar.
- Combines identical items and components and displays their total count.
- Uses the vanilla Creative inventory contents for Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools & Utilities, Combat, Food & Drinks, Ingredients, and Spawn Eggs.
- Adds an Overview category that always shows every shulker item and rebuilds vanilla Creative category contents after joining a world.
- Shows the active category in the HUD footer and supports in-category localized name, item ID, full-Pinyin, and Pinyin-initial search.
- Adds a configurable Ender Chest button to the inventory. Carrying an Ender Chest allows QuickShulker to open the real server-authoritative inventory with the same categories, search, aggregation, sorting, and HUD transfer interactions.
- Clicking the Ender Chest button keeps the normal inventory screen visible and interactive while showing the Ender Chest HUD on its left. The real QuickShulker Ender Chest container opens only briefly in the background for synchronization and transfers, so a second container window is never rendered.
- Adapts HUD columns, rows, side placement, vertical placement, and category icon size to the current GUI scale, window dimensions, and container position.
- Adds configurable pixel-rounded corners to HUD panels, category tabs, and controls.
- Uses consistent two-level shadows for the panel, tabs, search field, scrolling region, and footer controls.
- Press `B`, then `C` to open the native MaLiLib configuration screen for HUD sizing, corner radius, automatic restock settings, feature toggles, and hotkeys.
- Extracts one item with left click, one stack with `Shift+left click`, or moves a stack directly to the mouse cursor with right click; items can also be dropped onto the HUD for storage.
- When the cursor already carries an item, HUD clicks are reserved for storage and cannot simultaneously trigger extraction or merge a shulker stack into a player slot.
- Sends QuickShulker the actual container-list position and rescans the live inventory when a cached HUD source moved, improving hotbar slots 1-9 recognition across inventory layouts.
- Tracks source shulkers and batch-returns items, falling back to another slot in the original box.
- After returning tracked items, the hopper action consolidates inventory stacks into carried shulkers that already contain an identical item. It fills partial stacks first, then empty slots in the same matching box, never an unrelated empty box.
- Matching organization processes one stack at a time, limits partial-stack clicks to eight per tick, and waits for server-confirmed target counts before continuing.
- If an active HUD extraction finds a full inventory, it tries to store one non-selected stack to free a slot, preferring food, then tools, then other items, and retries the original extraction only once.
- Full-inventory clearance has item-ID allow and deny lists. Denied IDs are never moved; allowed IDs rank ahead of food, tools, and other items; denial wins when an ID appears in both lists. Entries accept comma, whitespace, or newline-separated `namespace:item_id` values.
- Ground-item pickup and automatic ground-item transfer are intentionally not implemented.
- Excludes tools, weapons, equipment, and food from automatic returns.
- Reconciles each return against the actual source-slot count change before updating tracked quantities.
- Clears pending operations, return history, and HUD caches when changing dimensions, worlds, or servers.
- Caches shulker scans, hash-based aggregation, and filtered search results using an inventory fingerprint.
- With Litematica installed, an easy-place pick that lacks its target block extracts one stack from a carried shulker and selects it. The first action restocks; the next places the block.
- Automatically refills a low, stackable main-hand item from an identical stack in a carried shulker, with configurable threshold, amount, and scan interval.

### Requirements

Client: Minecraft 26.1.x, Fabric Loader, Fabric API, MaLiLib 0.28.2, QuickShulker 3.0.0-26.1 or newer, and Better Shulker HUD.

Server: Fabric Loader, Fabric API, and QuickShulker 3.0.0-26.1 or newer. Litematica is an optional client dependency.

```powershell
$env:JAVA_HOME='path to Java 25'
.\gradlew.bat clean build
```

The project is released under CC0-1.0. BetterBundleHUD and its original author zeroowo24 are credited as the HUD design reference. Pinyin search uses PinIn and pinyin4j; see `LICENSE-PinIn`.
