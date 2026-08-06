# Better Shulker HUD

Better Shulker HUD 是 Fabric 客户端潜影盒物品管理模组。它会在背包和容器界面中汇总随身潜影盒内容，并可通过 QuickShulker 或兼容的 AxShulkers 服务端后端执行真实物品取放。

作者：`BF_skt`

> 当前版本仍在测试，可能存在较多 Bug。反馈时请提供 Minecraft 版本、Mod 版本、Fabric Loader、Fabric API、复现步骤、疑似冲突 Mod 和 `latest.log`。QQ 群：`1093770867`。

## 当前维护版本

每个目录都是可独立构建的完整 Gradle 工程。请使用与 Minecraft 版本完全匹配的 JAR，不能跨版本混用。

| Minecraft 版本 | Mod 版本 | 维护状态 | 源码 |
| --- | --- | --- | --- |
| `26.1.1` | `2.1.0` | 当前维护 | [`versions/26.1.1`](versions/26.1.1) |
| `26.1.2` | `2.0.2` | 当前维护 | [`versions/26.1.2`](versions/26.1.2) |
| `1.21.1` | `2.1.0` | 当前维护 | [`versions/1.21.1`](versions/1.21.1) |
| `1.21.4` | `2.1.0` | 当前维护 | [`versions/1.21.4`](versions/1.21.4) |
| `1.21.6-1.21.8` | `2.1.0` | 当前维护 | [`versions/1.21.8`](versions/1.21.8) |
| `1.21.11` | `2.0.2` | 当前维护 | [`versions/1.21.11`](versions/1.21.11) |

完整更新内容见 [`RELEASE_NOTES.md`](RELEASE_NOTES.md)，各版本的依赖、功能和构建方法见对应目录中的 `README.md`。

## 主要更新

- 汇总、分类、排序和搜索随身潜影盒物品，支持中文、英文、物品 ID 和拼音。
- 支持连续取放队列、同盒菜单复用、来源记录、批量归还和背包整理。
- 修复管理员物品/客户端预测导致的闪烁，并减少连续操作中的界面重建和鼠标跳动。
- 修复移动单个手持物品时误触发补货；水桶使用后补回水桶，不补空桶。
- 支持副手已有物品时安全交换，主手与副手补货分别跟踪。
- HUD 开关按钮仅在按 `E` 打开的玩家背包中显示。
- HUD 可与配方书同时打开，可选择隐藏配方书按钮，并可选适配 ModernUI、CozyUI+、REI、JEI 或 EMI（以各版本说明为准）。
- 可选兼容 Litematica Easy Place；不修改 Litematica 的投影与轻松放置根本逻辑。
- 可使用 QuickShulker 或兼容的 AxShulkers 服务端后端；没有后端时只能预览，不能真实取放。

## 其他版本

除上表六个版本外，`1.21`、`1.21.2-1.21.3`、`1.21.5`、`1.21.9-1.21.10`、`26.2.x` 以及更早版本可能不会继续更新。

历史构建仍保留在 GitHub Releases 和 Git 标签中。若需要继续维护这些版本，可自行 Fork 本仓库，并从最接近的历史标签或当前 `versions/` 工程移植修改。仓库根目录保留的是旧版 `1.8.8` 工程，仅用于历史参考，不代表当前推荐版本。

## 构建

进入目标版本目录后执行：

```powershell
.\gradlew.bat clean build
```

- Minecraft `1.21.x` 使用 Java 21。
- Minecraft `26.1.x` 使用 Java 25。
- 构建结果位于目标工程的 `build/libs/`。

## 许可证与致谢

项目使用 CC0-1.0。HUD 设计与交互参考 zeroowo24 的 BetterBundleHUD；本项目针对潜影盒、QuickShulker、MaLiLib、AxShulkers 和 Litematica 工作流独立实现并扩展。拼音搜索使用 PinIn 和 pinyin4j，详见 `LICENSE-PinIn`。
