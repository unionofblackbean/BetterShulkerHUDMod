# 26.x 基准统一候选版：Mod 2.2.5-beta.1

## Scope and immutable reference

- Baseline: Minecraft **26.1.1**, Better Shulker HUD **2.2.3**.
- User-provided JAR SHA-256:
  `07C747A7B9D596B078EFB1F4E5DB6D8CE9886450D0AD830903C5512EF677F2D9`.
- The reference matches `versions/26.1.1/build/libs/BetterShulkerHud-2.2.3+mc26.1.1.jar`.
- Starting commit: `63ce5e1`.
- Targets: **26.1**, **26.1.1**, **26.1.2**, **26.2** only.
- No changes to installed game JARs, private repositories, or 1.21.x source trees.
- No change to Litematica schematic recognition or Easy Place's original placement algorithm.
- Optional compatibility stays optional; portable server storage needs both client and server support.

## Confirmed starting differences

1. 26.1 and 26.1.1 have identical production Java sources.
2. 26.1.2 lacks the baseline portable-storage protocol, ender chest/bundle support,
   Item Scroller input adapter, and baseline Easy Place compatibility guards.
3. 26.2 changes screen APIs and Item Scroller hooks, and has a different HUD cache.
   API changes must not be confused with deliberate gameplay differences.
4. Existing base Client GameTest puts portable server-storage tests behind the optional
   QuickShulker profile, even though that protocol belongs to this mod.

## 本轮修改

- 四个目标统一为 Mod `2.2.5-beta.1`，游戏版本保持分别标注。
- 26.1.2 从旧 2.0.x 功能线补齐到 26.1.1 基准：末影箱、收纳袋、
  便携存储服务端协议、修饰键存放、HUD 连续取出、Item Scroller 兼容。
- 26.2 保留 `Gui.screen()/setScreen()`、羊毛物品 API 等必要适配，
  同步基准版本的空桶选择与原版放置回退保护；不改写投影识别或 Easy Place 原算法。
- 三种 HUD 存储来源使用同一个纯 Java `shared-core/StableOrder` 策略。
  更新数量时保留已有物品顺序，耗尽项移除，新物品追加；真正关闭背包或切换
  存储页面才重新按数量排序。临时 QuickShulker/AxShulkers 界面切换保留顺序。
- 四版统一为内容指纹刷新，取消 26.2 独有的库存 revision-only 分支。
  同一 tick 的多次绘制复用缓存；容器同步包可立即使缓存失效，下个 tick
  也会重新读取内容，不依赖单一库存 revision 计数。
- 末影箱请求在等待响应时去重，40 tick 后允许重试，避免一直卡在等待状态。
- 保留原有 Payload ID 和字段格式，不增加可选兼容 Mod 的硬前置。
- 四版显式引用 Fabric Loader 的 `@jar` 构建制品，避免 POM-only 发布元数据
  导致编译类路径缺少 `ModInitializer` 等 Loader 类；没有改动 Loader 版本号。

## 构建与验证边界

用户明确要求：**不要进行验证，制作 Mod 后由用户验证功能。**

- 本轮只执行 Java 25 / Gradle `assemble`，不执行 `test`、`runClientGameTest`、
  `runClient`、服务端启动、兼容性冒烟或 CI 验证。
- 现有测试源文件随基准同步保留，不表示测试已经执行或通过。
- 所有成品仅标记为 beta 候选，不替换游戏内 JAR，不替换正式 `latest`，不发布 GitHub Release。
- 存放目录：`02-发布成品/BetterShulkerHud-Modrinth-Jars/candidates/26x-baseline-mod2.2.5-beta.1/`。
- 编译成功只表示成功生成 JAR，不能承诺没有运行时 Bug。

## 必须保留的版本差异

| Minecraft | Mod | Java | 构建 Loader | Fabric API | MaLiLib 构建依赖 |
| --- | --- | --- | --- | --- | --- |
| 26.1 | 2.2.5-beta.1 | 25 | 0.19.2 | 0.145.1+26.1 | 0.28.1 |
| 26.1.1 | 2.2.5-beta.1 | 25 | 0.19.2 | 0.145.4+26.1.1 | 0.28.2 |
| 26.1.2 | 2.2.5-beta.1 | 25 | 0.19.2 | 0.155.2+26.1.2 | 0.28.3 |
| 26.2.x | 2.2.5-beta.1 | 25 | 0.19.3 | 0.156.0+26.2 | 0.29.0 |

表内依赖是编译配置，不是本轮实测通过的兼容版本列表。末影箱与收纳袋实际
转移需要服务端也安装支持相同协议的本 Mod；单纯安装 AxShulkers 不提供这个协议。
安装时只保留一个对应游戏版本的 Better Shulker HUD JAR，不要同时放入旧版和候选版。

## 本地编译记录（2026-10-11）

四版均执行 Java 25 / Gradle `assemble`，不运行功能验证。
网络缓存缺失时，仅补下载原版本依赖；失效的旧 MaLiLib Maven 地址使用 MaLiLib
官方 Modrinth 项目 `GcWjdA9I` 的同版本原始 JAR 供编译，未升级或降级。
本地临时 Maven 仓库只用于解析编译依赖，不把 MaLiLib、QuickShulker 或 Fabric API
嵌入产物，也不修改用户游戏实例的依赖。
