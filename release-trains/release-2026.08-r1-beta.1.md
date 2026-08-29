# [MC 1.21.6-1.21.8][Mod 2.1.2] HUD tooltip 图层修复 beta.1

这是 1.21.6–1.21.8 维护线的首个 beta 构建，实际 Mod 版本仍为 `2.1.2`。

## 本轮修复

- HUD 从 `AbstractContainerScreen.render()` 的尾部绘制迁移到 `renderContents()` 的尾部。
- 原版 tooltip 会在 HUD 之后绘制，因此物品提示不会再被 HUD 按钮或来源高亮遮挡。
- 配方书界面不再重复绘制 HUD；普通背包和配方书共用同一个容器内容渲染入口。
- 不修改 Litematica 投影识别、Easy Place 放置条件、QuickShulker/AxShulkers 操作协议或可选 Mod 前置边界。

## 验收范围

- 已完成 1.21.8 clean build，版本清单、兼容边界和滚动条输入门禁均通过。
- 请重点在 Minecraft `1.21.7` + Fabric Loader `0.19.2` 下验证：鼠标悬停物品时 tooltip 应显示在 HUD 和按钮上方。
- 同时验证普通背包、配方书共存、不同 GUI 缩放和按钮拖动。

当前仍可能存在较多 Bug。请在 QQ 群 `1093770867` 反馈，并附 Minecraft 游戏版本、Better Shulker HUD Mod 版本、Fabric Loader、Fabric API、复现步骤和完整日志。
