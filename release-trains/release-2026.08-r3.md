# [MC 26.1.1 / 26.1.2 / 26.2][Mod 2.0.7 / 2.2.3 / 2.2.4] MaLiLib 最低版本兼容更新

本发布列车同步 Minecraft 26.x 三条维护线的最新潜影盒 HUD 构建。Minecraft 游戏版本和 Mod 版本分别标明，不能混用其他游戏版本的 JAR。

## 版本与依赖

| Minecraft | Better Shulker HUD | 最低 MaLiLib |
| --- | --- | --- |
| `26.1.1` | `2.2.3` | `0.28.2` |
| `26.1.2` | `2.0.7` | `0.28.3` |
| `26.2.x` | `2.2.4` | `0.29.0` |

## 更新内容

- 26.1.2 从 MaLiLib `>=0.28.9` 放宽到官方首个 26.1.2 构件 `>=0.28.3 <0.29.0`，因此 MaLiLib `0.28.6` 不再触发 Fabric 依赖冲突。
- 26.2 从 `>=0.29.2` 对齐到官方首个 26.2 构件 `>=0.29.0 <0.30.0`。
- 构建依赖、`fabric.mod.json`、`versions.json` 和版本矩阵保持一致。
- 没有修改投影识别、Easy Place、补货、潜影盒来源归还、容器同步协议或可选 Mod 的根本逻辑。
- 26.1.2 和 26.2 使用最低 MaLiLib 版本完成 clean build 与基础 Client GameTest；全部 8 条维护源码线也完成 clean build。

## 安装说明

- 26.1.2 用户请使用 `BetterShulkerHud-2.0.7+mc26.1.2.jar`，可搭配 MaLiLib `0.28.3` 至 `0.28.x`。
- 26.2 用户请使用 `BetterShulkerHud-2.2.4+mc26.2.x.jar`，可搭配 MaLiLib `0.29.0` 至 `0.29.x`。
- 不要在 `mods` 文件夹同时放入同一 Minecraft 版本的旧 JAR 和新 JAR。

当前版本仍可能存在 Bug。请在 QQ 群 `1093770867` 反馈，并附 Minecraft 游戏版本、Better Shulker HUD Mod 版本、Fabric Loader、Fabric API、MaLiLib 版本、复现步骤和完整日志。
