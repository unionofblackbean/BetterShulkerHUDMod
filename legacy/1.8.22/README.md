# Legacy 1.8.22（经典功能线）保育源码

经典线 1.8.x 已于 2026-08-29 终结（政策见仓库根 `VERSIONING.md`）。本目录为**纯归档**
（终结决策：直接终止，不做任何后续修复），保存无 2.x 继任者的三个 MC 版本范围的 1.8.22 最终源码，供考古或自行 Fork：

| 目录 | MC 兼容范围 | 适配要点 |
|------|-------------|----------|
| `mc1.21/` | 1.21 | 基线 1.21，3 个 exclusion 兼容插件（EMI/JEI/REI 最全） |
| `mc1.21.2-1.21.3/` | 1.21.2–1.21.3 | 与 1.21.1 线同源，accesswidener 差异 |
| `mc1.21.5/` | 1.21.5 | 独立小版本适配 |

其余 MC 范围的 1.8.22 历史 jar 与 `-sources.jar` 归档于
`02-发布成品/BetterShulkerHud-归档-旧发布/BetterShulkerHUDMod-1.21.x-release/`。

## 规则

- 2026-08-29 决策：**经典线 1.8.x 直接终止**。本目录为纯归档（考古/自行 Fork 用），
  不再接收任何修复——包括安全问题——也不会发布 `1.8.23`。
- 本目录**不参与 CI**，不登记进 `versions.json`（与 legacyRoot 同策略，
  `Test-VersionManifest.ps1` 只校验 `versions/`）。
- 其余 MC 范围一律引导用户升级 26.x 线（见 `VERSION_MATRIX.md`）。

## 构建

各目录是自包含的 Fabric Loom 工程（gradle wrapper 齐备）。MC 1.21.x 线需 Java 21：

```powershell
cd legacy/1.8.22/mc1.21
./gradlew build
```
