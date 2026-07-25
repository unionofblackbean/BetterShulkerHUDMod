# Better Shulker HUD

[English](#english) | [简体中文](#简体中文)

## 简体中文

Better Shulker HUD 是一款适用于 Minecraft 26.1-26.1.2 Fabric 的客户端物品管理模组。它会在玩家背包界面中集中展示所有随身潜影盒的内容，让玩家无需逐个放置和打开潜影盒，也能搜索、取出、存放和归还物品。

界面的分类与排列方式参考了 BetterBundleHUDMod，并针对潜影盒和 QuickShulker 的交互方式进行了重新实现。所有物品移动都通过服务器打开的原版容器完成，由服务器验证并保存，不会在客户端直接生成或修改物品。

### 主要功能

- 在背包界面中汇总显示背包和快捷栏内所有潜影盒的物品。
- 将物品和组件完全相同的条目合并显示，并标注总数量。
- 按工具、战斗、方块、食物等类别筛选，支持滚轮浏览。
- 搜索时仅显示匹配物品，支持中文、物品 ID、全拼、拼音首字母和中英文混合输入。
- 左键取出一组物品，右键取出一个物品。
- 将鼠标拿起的物品拖到 HUD 上，可自动存入任意有空间的潜影盒。
- 记录取出物品的来源潜影盒；点击漏斗按钮可整理背包并批量归还。
- 原槽位被占用时，会优先尝试原潜影盒中的其他可用槽位。
- 自动归还会排除工具、武器、装备和食物；手动拖放存入不受此限制。
- HUD 可展开或最小化，并会根据 GUI 缩放和屏幕尺寸自动调整布局。
- 搜索框支持输入法切换、Unicode 输入和 `Ctrl+V` 粘贴中文。

拼音搜索示例：

- `潜影盒` -> `qianyinghe`
- `潜影盒` -> `qyh`
- `红色羊毛` -> `hong色ym`

### 操作方式

- 点击背包界面中配方书旁的潜影盒按钮：展开 HUD。
- 点击 HUD 右上角的减号按钮：最小化 HUD。
- 左键点击 HUD 物品：取出该条目中的一组物品。
- 右键点击 HUD 物品：取出一个物品。
- 在 HUD 中滚动鼠标滚轮：浏览更多物品。
- 将光标上的物品拖入 HUD 后松开：存入任意有空间的潜影盒。
- 点击左下角漏斗按钮：将已记录的物品归还到来源潜影盒。

### 安装要求

客户端：

- Minecraft 26.1-26.1.2
- Java 25
- Fabric Loader 0.18.4 或更高版本
- Fabric API
- QuickShulker 3.0.1-26.1 或更高版本
- Better Shulker HUD

服务端：

- Fabric Loader 与 Fabric API
- QuickShulker 3.0.1-26.1 或更高版本

Better Shulker HUD 本体只需安装在客户端。客户端与服务端都必须安装兼容版本的 QuickShulker，才能执行取出、归还和自由存放操作。

### 从源码构建

本项目使用 Gradle Wrapper，第三方编译依赖会从公开 Maven 仓库下载，不需要把依赖 JAR 放入仓库。

```powershell
$env:JAVA_HOME='你的 Java 25 安装目录'
.\gradlew.bat clean build
```

构建产物位于 `build/libs/`。

### 开源许可与致谢

- 本项目以 [CC0 1.0](LICENSE) 发布。
- HUD 的界面设计参考 [BetterBundleHUDMod](https://github.com/zeroowo24/BetterBundleHUDMod)，感谢 zeroowo24 的原始设计。
- 潜影盒交互依赖 [QuickShulker](https://github.com/MoRanpcy/quickshulker)。
- 拼音搜索使用 [PinIn](https://github.com/Towdium/PinIn) 和 [pinyin4j](https://github.com/belerweb/pinyin4j)。PinIn 以 MIT 许可证发布，许可证副本见 [LICENSE-PinIn](LICENSE-PinIn)。

---

## English

Better Shulker HUD is a client-side inventory management mod for Minecraft 26.1-26.1.2 on Fabric. It presents the contents of every shulker box carried by the player in one inventory HUD, allowing items to be searched, extracted, stored, and returned without placing and opening each box manually.

Its category layout is inspired by BetterBundleHUDMod and has been reimplemented for shulker boxes and QuickShulker. Every item transfer uses a vanilla container opened by the server. The server validates and saves all inventory changes; the mod never creates or edits items on the client alone.

### Features

- Displays items from every shulker box in the inventory and hotbar.
- Combines entries with identical items and components, then displays their total count.
- Filters items by categories such as tools, combat, blocks, and food, with scroll-wheel navigation.
- Shows only matching items while searching by localized name, item ID, full pinyin, pinyin initials, or mixed Chinese and pinyin.
- Extracts one stack with left click or one item with right click.
- Stores the item carried by the cursor in any shulker box with enough space by dropping it onto the HUD.
- Tracks the source shulker of extracted items and returns them in batches with the hopper button.
- Falls back to another available slot in the original shulker when the recorded slot is occupied.
- Excludes tools, weapons, equipment, and food from automatic return; manual drag-to-store remains unrestricted.
- Supports minimize/expand controls and responsive layout across GUI scales and screen sizes.
- Supports IME switching, Unicode input, and Chinese text pasted with `Ctrl+V`.

Pinyin search examples:

- `潜影盒` -> `qianyinghe`
- `潜影盒` -> `qyh`
- `红色羊毛` -> `hong色ym`

### Controls

- Click the shulker button beside the recipe book control to expand the HUD.
- Click the minus button in the upper-right corner of the HUD to minimize it.
- Left-click a HUD item to extract one stack from its source.
- Right-click a HUD item to extract one item.
- Scroll over the HUD to browse additional items.
- Release an item carried by the cursor over the HUD to store it in any shulker with space.
- Click the hopper button in the lower-left corner to return tracked items to their source shulkers.

### Requirements

Client:

- Minecraft 26.1-26.1.2
- Java 25
- Fabric Loader 0.18.4 or newer
- Fabric API
- QuickShulker 3.0.1-26.1 or newer
- Better Shulker HUD

Server:

- Fabric Loader and Fabric API
- QuickShulker 3.0.1-26.1 or newer

Better Shulker HUD itself is client-side only. A compatible QuickShulker version must be installed on both the client and server for extraction, return, and drag-to-store operations.

### Building From Source

The project uses the Gradle Wrapper. Third-party compile dependencies are downloaded from public Maven repositories, so no dependency JARs need to be committed.

```powershell
$env:JAVA_HOME='path to your Java 25 installation'
.\gradlew.bat clean build
```

Build artifacts are written to `build/libs/`.

### License And Credits

- This project is released under [CC0 1.0](LICENSE).
- The HUD layout is inspired by [BetterBundleHUDMod](https://github.com/zeroowo24/BetterBundleHUDMod). Credit goes to zeroowo24 for the original design.
- Shulker interaction is powered by [QuickShulker](https://github.com/MoRanpcy/quickshulker).
- Pinyin search uses [PinIn](https://github.com/Towdium/PinIn) and [pinyin4j](https://github.com/belerweb/pinyin4j). PinIn is licensed under MIT; a copy is included in [LICENSE-PinIn](LICENSE-PinIn).
