# 源码收敛与 GLM 方案回调记录

日期：2026-08-29  
仓库：`unionofblackbean/BetterShulkerHUDMod`  
当前治理分支：`codex/reconcile-glm-governance`

## 结论

`BetterShulkerHUDMod-Public-Release` 是唯一活跃源码库，`versions.json` 是版本、
依赖、功能等级和产物名称的单一事实源。`01-源码项目` 中的完整副本保留原地，
仅作冻结参考，不再作为构建输入；旧 JAR 也只归档，不覆盖或删除。

本次没有把维护范围粗暴收缩到 26.x。GLM 方案中能减少漂移的治理措施被采纳，
会改变用户兼容范围或现有功能的决策已回调。

## 当前维护范围

| Minecraft 构建线 | 源码 | Mod 版本 | 功能线 | 维护等级 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 1.21.1 | `versions/1.21.1` | 2.1.3 | 2.1.x | active | 保留 EMI/JEI/REI 入口 |
| 1.21.4 | `versions/1.21.4` | 2.1.3 | 2.1.x | active | 仅声明已编译的可选兼容 |
| 1.21.6–1.21.8 | `versions/1.21.8` | 2.1.2 | 2.1.x | active/beta | 1.21.7 tooltip 图层修复已构建，待人工验收 |
| 1.21.9–1.21.10 | `versions/1.21.10` | 2.1.3 | 2.1.x | active | 使用对应 1.21.10 API |
| 1.21.11 | `versions/1.21.11` | 2.0.6 | 2.0.x | maintenance | 只接受安全和兼容性修复 |
| 26.1.1 | `versions/26.1.1` | 2.2.3 | 2.2.x | active | Java 25、Loader 0.19.2 |
| 26.1.2 | `versions/26.1.2` | 2.0.6 | 2.0.x | maintenance | 保留现有稳定功能，不强行升级功能线 |
| 26.2.x | `versions/26.2` | 2.2.3 | 2.2.x | active | Java 25、Loader 0.19.3 |

1.21、1.21.2–1.21.3 和 1.21.5 没有 2.x 继任源码，经典 `1.8.22` 仅存于
`legacy/1.8.22/`，不参与当前 CI，也不再发布新的 1.8.x 修复版。

## 采纳的治理措施

- 统一产物名为 `BetterShulkerHud-<Mod版本>+mc<游戏版本>.jar`，旧名保留映射。
- 所有版本目录固定为 `versions/<Minecraft 构建线>/`，禁止使用 Mod 版本命名目录。
- `Test-VersionManifest.ps1` 检查清单、`gradle.properties`、`fabric.mod.json`、
  Gradle wrapper、Java 版本和产物名称的一致性。
- `Build-All.ps1` 是批量构建入口，会按版本自动选择 Java 21 或 Java 25；也支持
  `-Java21Home`、`-Java25Home` 显式指定 JDK。
- PR、Nightly 和 Release workflow 都先运行版本清单、可选 Mod 边界和滚动条输入门禁。
- QuickShulker、AxShulkers、Item Scroller、REI、JEI、EMI、ModernUI、CozyUI+ 和
  Litematica 继续是可选兼容，不转为硬前置。
- 旧源码和旧产物不删除；发布出口保持在
  `Z:\我的世界project\02-发布成品\BetterShulkerHud-Modrinth-Jars`，新批次应进入
  `candidates/<release-train>` 或 `releases/<release-train>`。

## 已回调的内容

- 不删除或归档 1.21.1、1.21.4、1.21.8、1.21.10、1.21.11 的 2.x 活跃源码。
- 不取消 1.21.7 tooltip 问题的后续修复；该版本线只是暂缓稳定发布。
- 不把 1.21.x 源码移动到 `legacy/2.x/`，避免用户误以为这些版本已经停止维护。
- 不用一个副本整体覆盖另一个版本；Minecraft API、Mixin 和可选兼容必须逐文件核对。
- 不在没有行为测试的情况下把“编译通过”标为“修复完成”。
- 2.2.x 的容器同步协议保留在 26.x 主线；container-safe 旧版不作为第三条长期功能线，
  但其网络语义只有在逐项协议兼容验证通过后才可移植到 1.21.x。

## 差异核对结果

- `1.21.1` 的 EMI/JEI 排除入口已存在；其他版本只声明其实际存在且可编译的入口，
  不为了表格整齐虚构兼容。
- 26.2 主线包含 `StorageActionPayload`、服务端存储访问和客户端网络层；旧
  `ContainerHud*` 文件是历史前身，不能直接复制覆盖主线协议。
- 冻结副本中没有发现应覆盖主线的功能修复；主线额外包含来源校验、重复请求保护、
  GameTest 及滚动条输入隔离逻辑。

## 后续执行顺序

1. 先完成 1.21.8 tooltip 图层的渲染顺序修复和人工验收，再生成该线正式 Release。
2. 按 Java 21 / Java 25 分组执行全量 clean build；失败时保留日志，不降级为“通过”。
3. 每次跨版本 Bug 修复都记录受影响的 `versions/<id>`，并让清单门禁拒绝遗漏版本。
4. 稳定后再逐个抽取无 Minecraft 依赖的公共核心；在此之前保持浅收敛，避免重写
   Litematica Easy Place、QuickShulker 或网络协议的根本逻辑。
