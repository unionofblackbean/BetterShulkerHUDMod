# MaLiLib 兼容性

最低版本依据 Sakura-Ryoko/MaLiLib 官方仓库的 Minecraft 分支、Git 标签和
对应 Maven 构件目录确认。这里的“官方最低版本”表示该 Minecraft 构建线最早
提供的 MaLiLib，不等同于 Better Shulker HUD 某个旧 JAR 的历史要求。

| Minecraft | 官方最低 MaLiLib | 当前 Better Shulker HUD 构建依赖 | 当前 Mod 元数据范围 |
| --- | --- | --- | --- |
| `26.1.1` | `0.28.2` | `0.28.2` | `>=0.28.2 <0.29.0` |
| `26.1.2` | `0.28.3` | `0.28.3` | `>=0.28.3 <0.29.0` |
| `26.2.x` | `0.29.0` | `0.29.0` | `>=0.29.0 <0.30.0` |

官方参考标签：

- `26.1.1-0.28.2`
- `26.1.2-0.28.3`
- `26.2-0.29.0`

本仓库的构建依赖、`fabric.mod.json` 和 `versions.json` 必须保持一致。更高的
同一兼容区间版本通常可以使用，但不能跨 Minecraft 构建线混用。例如：

- MaLiLib `0.28.6` 可以用于 Minecraft `26.1.2`，但必须使用本次重构后的
  Better Shulker HUD `2.0.7`；旧版 `2.0.6` 的元数据仍会拒绝它。
- MaLiLib `0.29.x` 属于 Minecraft `26.2` 兼容线，不能替代 26.1.x 的
  `0.28.x` 构件。

验证状态：三个 26.x 版本已使用各自最低官方 MaLiLib 版本完成 clean build；
随后又完成了全部 8 条维护源码线的 clean build。运行时仍应在对应 Minecraft、
Fabric Loader 和 MaLiLib 组合下进行客户端启动及设置界面/快捷键人工验收。
