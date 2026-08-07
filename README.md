# Better Shulker HUD

Better Shulker HUD 是一个 Fabric 客户端潜影盒物品管理模组。它会在背包和容器界面中汇总随身潜影盒内容，并可通过 QuickShulker 或兼容的 AxShulkers 服务端后端执行真实物品取放。

作者：`BF_skt`

> 当前版本仍在测试，可能存在较多 Bug。反馈时请提供 Minecraft 版本、Mod 版本、Fabric Loader、Fabric API、复现步骤、疑似冲突 Mod 和 `latest.log`。QQ 群：`1093770867`。

## 当前维护版本

每个 `versions/` 子目录都是可独立构建的完整 Gradle 工程。JAR 必须与 Minecraft 版本完全匹配，不能跨版本混用。

| Minecraft | Mod | 功能线 | Java | 源码 |
| --- | --- | --- | --- | --- |
| `1.21.1` | `2.1.1` | 完整 2.1.x | 21 | [`versions/1.21.1`](versions/1.21.1) |
| `1.21.4` | `2.1.1` | 完整 2.1.x | 21 | [`versions/1.21.4`](versions/1.21.4) |
| `1.21.6-1.21.8` | `2.1.1` | 完整 2.1.x | 21 | [`versions/1.21.8`](versions/1.21.8) |
| `1.21.9-1.21.10` | `2.1.1` | 完整 2.1.x | 21 | [`versions/1.21.10`](versions/1.21.10) |
| `1.21.11` | `2.0.3` | 稳定 2.0.x | 21 | [`versions/1.21.11`](versions/1.21.11) |
| `26.1.1` | `2.1.1` | 完整 2.1.x | 25 | [`versions/26.1.1`](versions/26.1.1) |
| `26.1.2` | `2.0.3` | 稳定 2.0.x | 25 | [`versions/26.1.2`](versions/26.1.2) |

详细差异见 [VERSION_MATRIX.md](VERSION_MATRIX.md)，更新记录见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。

## 主要功能

- 汇总、分类、排序和搜索随身潜影盒内容，支持本地化名称、物品 ID 和拼音。
- HUD 中直接取出、存入和连续处理物品，并记录来源以便批量归还。
- 支持主手/副手自动补货、副手安全交换、不死图腾类单件物品补货和水桶补水桶。
- HUD 开关仅在按 `E` 打开的玩家背包中显示，可与配方书同时打开，并可选择隐藏配方书按钮。
- 可选适配 ModernUI、CozyUI+、REI、JEI、EMI 和 Litematica，具体支持范围以版本矩阵为准。
- 可选择 QuickShulker 或 AxShulkers 后端；没有可用后端时只提供内容预览，不执行真实物品转移。
- 使用 MaLiLib 原生设置界面管理 HUD、快捷键、补货、整理和诊断选项。

## 2.1.1 / 2.0.3 修复

- AxShulkers 自动补货完成、失败或只补到部分数量后，不再反复打开同一个潜影盒。
- 等待服务端物品状态稳定后记录背包状态；玩家消耗、移动物品或潜影盒内容变化时才允许再次补货。
- 打开请求前置条件无效时立即结束操作，不再空等超时后重复检测。
- 减少 AxShulkers 打开/关闭消息在左下角连续刷屏。
- 手动 HUD 取放和 Litematica 投影逻辑不受此保护影响。

## 构建

进入目标版本目录后执行：

```powershell
.\gradlew.bat clean build
```

- Minecraft `1.21.x` 使用 Java 21。
- Minecraft `26.1.x` 使用 Java 25。
- 构建结果位于目标工程的 `build/libs/`。

## 其他版本

`1.21`、`1.21.2-1.21.3`、`1.21.5`、`26.2.x` 及更早版本仅保留历史构建，可能不会继续更新。需要继续维护时可自行 Fork 本仓库并从最接近的版本移植。

## 许可证

项目使用 CC0-1.0。HUD 设计与交互参考 zeroowo24 的 BetterBundleHUD；拼音搜索使用 PinIn 和 pinyin4j，详见 `LICENSE-PinIn`。
