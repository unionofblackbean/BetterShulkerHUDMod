# Better Shulker HUD

Better Shulker HUD 是一个以 Fabric 客户端为主体的便携存储管理模组。它会在背包和容器界面中汇总随身潜影盒内容；从 Mod 2.2.0 开始，还可在同一 HUD 中管理末影箱与收纳袋。服务器安装同版本模组后可直接执行三类存储的安全取放，未安装时潜影盒仍沿用 QuickShulker / AxShulkers 兼容路径。

作者：`BF_skt`

> 当前版本仍在测试，可能存在较多 Bug。反馈时请提供 Minecraft 版本、Mod 版本、Fabric Loader、Fabric API、复现步骤、疑似冲突 Mod 和 `latest.log`。QQ 群：`1093770867`。

## 当前维护版本

每个 `versions/` 子目录都是可独立构建的完整 Gradle 工程。JAR 必须与 Minecraft 版本完全匹配，不能跨版本混用。

版本号始终成对标注，例如 `Minecraft 26.2.x / Mod 2.2.3`。单独出现的 `26.2.x` 是 Minecraft 游戏版本，`2.2.3` 是 Better Shulker HUD 自身的 Mod 版本。JAR 使用 `BetterShulkerHud-<Mod版本>+mc<Minecraft版本>.jar`，例如 `BetterShulkerHud-2.2.3+mc26.2.x.jar`。

<!-- generated:maintained-versions:start -->
| Minecraft 游戏版本 | Better Shulker HUD Mod 版本 | 功能线 | 维护等级 | Java | 源码 |
| --- | --- | --- | --- | --- | --- |
| `1.21.1` | `2.1.3` | 完整 2.1.x | `active` | 21 | [`versions/1.21.1`](versions/1.21.1) |
| `1.21.4` | `2.1.3` | 完整 2.1.x | `active` | 21 | [`versions/1.21.4`](versions/1.21.4) |
| `>=1.21.6 <=1.21.8` | `2.1.2` | 2.1.x（Issue #6 修复待发布） | `active` | 21 | [`versions/1.21.8`](versions/1.21.8) |
| `>=1.21.9 <=1.21.10` | `2.1.3` | 完整 2.1.x | `active` | 21 | [`versions/1.21.10`](versions/1.21.10) |
| `1.21.11` | `2.0.6` | 稳定 2.0.x | `maintenance` | 21 | [`versions/1.21.11`](versions/1.21.11) |
| `26.1.1` | `2.2.3` | 完整便携存储 2.2.x | `active` | 25 | [`versions/26.1.1`](versions/26.1.1) |
| `26.1.2` | `2.0.6` | 稳定 2.0.x | `maintenance` | 25 | [`versions/26.1.2`](versions/26.1.2) |
| `>=26.2 <26.3` | `2.2.3` | 完整便携存储 2.2.x | `active` | 25 | [`versions/26.2`](versions/26.2) |
<!-- generated:maintained-versions:end -->

详细差异见 [VERSION_MATRIX.md](VERSION_MATRIX.md)，更新记录见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。

## 仓库结构

- `versions/` 是当前维护源码的唯一入口，版本、依赖、测试和发布状态由 [`versions.json`](versions.json) 统一管理。
- 仓库根目录的 Gradle 工程是历史 `Minecraft 26.1.2 / Mod 1.8.8` 源码，只用于追溯，不参与当前 CI，也不是 26.1 的新适配基础。
- 历史根工程会在基础设施稳定后通过单独的机械迁移 PR 移到 `legacy/26.1.2-1.8.8`；当前阶段不改动其源码。
- GitHub Release 使用 `release-YYYY.MM-rN` 发布列车标签；每个附件文件名和 JAR 内元数据仍保留真实 Mod 与 Minecraft 版本。
- 新分支使用 `codex/mc-<Minecraft版本>-mod-<Mod版本>-<用途>`；PR 与 Release 标题使用 `[MC <Minecraft版本>][Mod <Mod版本>] <内容>`。涉及全部维护版本时，Minecraft 写 `all`，Mod 部分列出所有实际版本。

## 主要功能

- 通过潜影盒、末影箱、收纳袋三个标签汇总和搜索随身存储内容，支持本地化名称、物品 ID 和拼音。
- HUD 中直接取出、存入和连续处理物品，并记录来源以便批量归还。
- 按住可配置修饰键（默认空格）点击或划过背包槽位即可顺滑存放；在 HUD 上按住左键划过物品可连续取出。
- 可选接管 Item Scroller 的 HUD 滚轮输入：普通滚轮一次取一个，整组修饰键配合滚轮一次取出整组。
- 支持主手/副手自动补货、副手安全交换、不死图腾类单件物品补货和水桶补水桶。
- HUD 开关仅在按 `E` 打开的玩家背包中显示，可与配方书同时打开，并可选择隐藏配方书按钮。
- 可选适配 ModernUI、CozyUI+、REI、JEI、EMI 和 Litematica，具体支持范围以版本矩阵为准。
- 末影箱和收纳袋的真实取放要求服务器安装同版本 Better Shulker HUD。潜影盒在服务器未安装本模组时仍可选择 QuickShulker 或 AxShulkers 后端；没有任何可用后端时只提供预览。
- 使用 MaLiLib 原生设置界面管理 HUD、快捷键、补货、整理和诊断选项。

## 最新修复（Mod 2.0.6 / 2.1.2 / 2.1.3 / 2.2.3）

- 26.1.1、26.1.2 与 26.2 在“背包已满、目标潜影盒也满、无法常规腾位”时，可把当前手中整组物品与目标潜影盒槽位直接交换，轻松放置不再因没有临时空位而中断。
- 整组交换复用 QuickShulker/AxShulkers 的真实容器，校验双方物品与数量、空光标及服务端同步；未改动 Litematica 投影或 Easy Place 根本判定。
- 修复从 HUD 连续取出物品时，QuickShulker/AxShulkers 的临时容器界面切换被误判为玩家关闭背包的问题。
- 在背包仍打开且未切换页面时，只刷新数量和来源，不重新排序 HUD；连续点击同一坐标会继续取出原物品。
- 真正关闭背包或切换页面时仍保留原有排序行为，避免改变用户主动触发的整理逻辑。
- 本次公开同步覆盖 `1.21.1`、`1.21.4`、`1.21.9-1.21.10`、`1.21.11`、`26.1.1`、`26.1.2` 和 `26.2`。
- `1.21.6-1.21.8` 暂留公开版 `2.1.1`：已确认 1.21.7 存在 HUD 按钮后绘制、遮挡原版 tooltip 的图层问题，修复并验证后单独发布。

## Mod 2.2.0 更新

- 新增末影箱 27 格同步以及收纳袋聚合显示，三类来源均支持单件、整组、光标取放和来源记忆。
- 增加服务器二次校验：客户端请求携带预期物品，槽位变化时会重新定位同类物品，不会误取或误存其他物品。
- 参考 Sakurastreet 的 MIT 许可实现改进空格修饰键存放、鼠标连续操作与 Item Scroller 联动，并在发行 JAR 中附带原许可。
- 将漏斗整理按钮移动到原右上角最小化位置，移除最小化按钮和底部分类介绍，只保留三个紧凑来源图标。
- 保留 ModernUI 与 CozyUI+ 的可选兼容路径，二者均不是硬前置；未安装时继续使用现有原版风格、圆角和阴影。
- 保留 QuickShulker、AxShulkers、自动补货、副手交换、水桶、来源归还和 Litematica Easy Place 的既有判断，不改写投影根本逻辑。

## Mod 2.1.1 / 2.0.3 修复

- AxShulkers 自动补货完成、失败或只补到部分数量后，不再反复打开同一个潜影盒。
- 等待服务端物品状态稳定后记录背包状态；玩家消耗、移动物品或潜影盒内容变化时才允许再次补货。
- 打开请求前置条件无效时立即结束操作，不再空等超时后重复检测。
- 减少 AxShulkers 打开/关闭消息在左下角连续刷屏。
- 手动 HUD 取放和 Litematica 投影逻辑不受此保护影响。

## 构建

推荐在仓库根目录通过版本清单构建，脚本会检查所用 Java 大版本：

```powershell
.\scripts\Build-Version.ps1 -Id 1.21.11 -JavaHome "<Java 21 目录>" -Clean
.\scripts\Build-Version.ps1 -Id 26.2 -JavaHome "<Java 25 目录>" -Clean
```

Client GameTest 与可选 Mod 兼容通道：

```powershell
.\scripts\Build-Version.ps1 -Id 1.21.11 -JavaHome "<Java 21 目录>" -GameTest
.\scripts\Build-Version.ps1 -Id 1.21.11 -JavaHome "<Java 21 目录>" -GameTest -CompatibilityProfile quickshulker
.\scripts\Build-Version.ps1 -Id 26.2 -JavaHome "<Java 25 目录>" -GameTest -CompatibilityProfile itemscroller -LocalArtifact "<Item Scroller JAR>"
```

也可以进入目标版本目录直接执行：

```powershell
.\gradlew.bat clean build
```

- Minecraft `1.21.x` 使用 Java 21。
- Minecraft `26.x` 使用 Java 25。
- 构建结果位于目标工程的 `build/libs/`。

## 其他版本

`1.21`、`1.21.2-1.21.3`、`1.21.5` 及更早版本仅保留历史构建，可能不会继续更新。需要继续维护时可自行 Fork 本仓库并从最接近的版本移植。

## 许可证

项目主体使用 CC0-1.0。HUD 设计与交互参考 zeroowo24 的 BetterBundleHUD；拼音搜索使用 PinIn 和 pinyin4j，详见 `LICENSE-PinIn`。2.2.0 的部分便携存储交互参考 Sakurastreet/BetterShulkerHUDMod 的 MIT 许可实现，详见各 2.2.x 子项目中的 `LICENSE-Sakurastreet-BetterShulkerHUD`。
