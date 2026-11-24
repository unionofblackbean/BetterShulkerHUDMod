# 26.x ux.3 验证记录（2026-11-24）

本记录对应 [版本清单](../../versions.json) 中五个 26.x 预览版和 [更新内容](../../RELEASE_NOTES.md)。工作副本在 Java 25.0.4.1、Gradle 9.5.1、Fabric Loom 1.16.3 下逐版执行构建、check、测试源编译及实际 Fabric Client GameTest。原始来源包共 363 个文件在最终验证前后 SHA-256 与基线一致。此结论只覆盖受测环境，**不是用户实际整合包或服务端验收**。

| Minecraft | build/check/测试源编译 | base 客户端 | QuickShulker 客户端 | 匹配 Mod Menu 客户端 |
| --- | --- | --- | --- | --- |
| 26.1 | PASS | PASS | PASS | PASS（18.0.0） |
| 26.1.1 | PASS | PASS | PASS | PASS（18.0.0） |
| 26.1.2 | PASS | PASS | PASS | PASS（18.0.0） |
| 26.2 | PASS | PASS | PASS | PASS（20.0.3） |
| 26.3 | PASS | PASS | PASS | PASS（21.0.1） |

15 条独立客户端 profile 均有新鲜 START/SUCCESS 标记，日志未报告崩溃。共五组独立共享核心断言（HudInputRoutingTest、HudOverlayLayoutTest、HudPanelLayoutTest、HudPresentationTest、PotionRestockPolicyTest）也通过；**单纯执行 Gradle build/check 并不会运行这些 main 方法断言**。历史 26.3 JVM 原生启动崩溃不代表本轮 26.3 的结果，新的 26.3 三条 profile 均完成。

六个界面点的实际测试包括：[26.3 客户端测试](../../versions/26.3/src/clientGameTest/java/bettershulkerhud/gametest/BetterShulkerHudClientGameTest.java) 与其余四版对应测试，断言页脚组右对齐且不覆盖分类；配方书开启时整块 HUD 移右、手动坐标不变、关闭后恢复、窄窗口不留下幽灵输入；分类名称 tooltip；真实空容器不常驻文字、悬停显示原因。五版均注入一次模组名为 “-” 的错误 MaLiLib 注册记录后重开设置页，验证本模组名称恢复与注册唯一；26.1/26.1.1/26.1.2 进一步断言真正渲染文字，26.2/26.3 采用注册断言与截图人工目视。Mod Menu profile 真正点击配置按钮进入设置并用 Esc 返回，而不是只检查入口类存在。

**未覆盖范围**：用户此前显示 “-” 的具体外部成因未证明；26.2/26.3 声明的 Item Scroller 由于缺精确本地 JAR 而跳过，不能当成兼容 PASS；测试没有逐一模拟载入中/内容未知的网络时序、所有语言和屏幕缩放、真人整合包、跨平台 GPU/JVM 或联机服务端。Mod Menu JAR 为匹配版本的**仅测试依赖**，不内置于模组。源代码、现有兼容协议及其他 1.21.x 版本没有因这 15 个 profile 就获得普遍兼容保证。用户反馈请附完整 Minecraft/Mod/Fabric/MaLiLib 版本、复现步骤与 latest.log。
