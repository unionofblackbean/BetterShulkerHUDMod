# Better Shulker HUD 2.1.2 for Minecraft 1.21.1

[简体中文](#简体中文) | [English](#english)

## 简体中文

Better Shulker HUD 是 Minecraft 1.21.1 Fabric 客户端物品管理模组。它会在玩家背包和容器界面旁显示随身潜影盒内容，并通过 QuickShulker 或 AxShulkers 服务端后端执行真实物品转移。

作者：`BF_skt`

HUD 设计与交互参考 zeroowo24 的 BetterBundleHUD；本项目针对潜影盒、MaLiLib、QuickShulker、AxShulkers 和 Litematica 工作流独立实现并扩展。

### 主要功能

- 汇总随身潜影盒内容，合并同类物品并显示总数量。
- 支持分类、中文和英文名称、物品 ID 与拼音搜索。
- 左键取一个，`Shift + 左键`取一组，右键取到鼠标；支持拖入 HUD 和背包批量存放。
- 记录物品来源并批量归还，支持副手交换、水桶补水桶和低数量自动补货。
- 连续取放可复用同一个潜影盒菜单；跨盒操作使用队列和可调延迟，最后会确认背包菜单恢复稳定。
- 提供指定 `ItemSource` 的程序化接口，供 Quick Crafting 等兼容模组按来源潜影盒分组处理材料。
- HUD 与配方书可同时打开，可选择隐藏原版配方书按钮；HUD 开关仅在玩家背包界面显示。
- 可选适配 ModernUI、CozyUI+、Litematica、REI、JEI 和 EMI，不要求安装 ModernUI 或 CozyUI+ 前置。
- 使用 MaLiLib 原生设置界面，支持配置快捷键、HUD 位置、分类和诊断日志。

### 依赖

- Minecraft `1.21.1`
- Java 21
- Fabric Loader `0.16.14` 或更高版本
- 对应 Minecraft 1.21.1 的 Fabric API
- MaLiLib `0.21.x`
- QuickShulker 或 AxShulkers 服务端后端，用于实际取放物品

QuickShulker 是可选依赖；未安装时可以改用 AxShulkers。纯客户端仍可预览潜影盒内容，但没有可用的服务端后端时不能执行真实取放操作。

### 构建

```powershell
$env:JAVA_HOME='path to Java 21'
.\gradlew.bat clean build
```

当前版本仍在测试，可能存在较多问题。反馈时请提供 Minecraft 版本、Mod 版本、Fabric Loader 版本、Fabric API 版本、复现步骤和疑似冲突 Mod。QQ 群：`1093770867`。

## English

Better Shulker HUD is a Fabric client inventory manager for Minecraft 1.21.1. It previews carried shulker contents beside inventory screens and uses either QuickShulker or AxShulkers as the server-authoritative transfer backend.

Version 2.1.0 includes recipe-book coexistence, optional ModernUI and CozyUI+ adaptation, queued cross-shulker extraction and storage, inventory-menu settlement after programmatic batches, and a source-specific `ItemSource` compatibility API.

The build requires Java 21, Fabric Loader 0.16.14 or newer, Fabric API for Minecraft 1.21.1, and MaLiLib 0.21.x. QuickShulker and AxShulkers are optional alternatives, but one compatible server backend is required for actual item transfers.

This release remains under active testing. Report issues in QQ group `1093770867` with the Minecraft version, Mod version, Fabric Loader version, Fabric API version, reproduction steps, and suspected conflicting Mods.

Released under CC0-1.0. PinIn and pinyin4j are used for Pinyin search; see `LICENSE-PinIn`.
