# Versioning

本文件是 BetterShulkerHUDMod 的版本与命名权威规范。执行由 `scripts/Test-VersionManifest.ps1`
强制保证：`versions.json` 是唯一事实源，与各 `versions/<mc>/gradle.properties`、
`fabric.mod.json`、构建脚本、产物文件名全链一致性不符即构建门禁失败。

## 产物命名

现行（唯一允许，自 2.2.3 / 2.1.3 / 2.0.6 批次生效）：

`BetterShulkerHud-<mod-semver>+mc<minecraft-version-or-range>.jar`

源码包为同名 `-sources.jar`。Git tag 只含 mod 版本（`v2.2.3`），不含 MC 版本。

### 命名迁移映射（历史 → 现行）

| 时代 | 旧格式（仅存于归档） | 示例 |
|------|---------------------|------|
| 1.8.x 内部构建 | `better-shulker-hud-<mc>-<mod>+mc<mc>.jar` | `better-shulker-hud-1.21.1-1.8.22+mc1.21.1.jar` |
| 2.0.x–2.2.2 过渡发布 | `BetterShulkerHud-<mc>-<mod>.jar` | `BetterShulkerHud-26.2-2.2.2.jar` |
| **现行** | `BetterShulkerHud-<mod>+mc<mc>.jar` | `BetterShulkerHud-2.2.3+mc26.2.jar` |

历史 jar 不重命名、不重发；用户侧按上表对应即可。

## 版本语义

- MC 版本只表达兼容性，不改变 mod 版本；同一功能集在所有 MC 版本上使用同一 mod 版本。
- PATCH = 兼容性 bug 修复；MINOR = 兼容性新功能；MAJOR = 不兼容的工作流/配置变更。
- 预发布用 SemVer 后缀（`2.3.0-beta.1`）。

## 功能线（feature lines）

| 线 | 状态 | 说明 |
|----|------|------|
| `2.2.x` | active（26.1.1、26.2） | 含完整 Storage 网络协议：潜影盒/末影箱/收纳袋服务端校验存取、整组交换回退、Portable Return 追踪、Client GameTest |
| `2.1.x` | active（1.21.1/1.21.4/1.21.9-1.21.10）、beta（1.21.6-1.21.8） | 2.2.x 的 1.21.x 适配层；1.21.6-1.21.8 已完成 tooltip 遮挡修复，等待 1.21.7 人工验收 |
| `2.0.x` | maintenance（1.21.11、26.1.2） | 仅安全修复，不加新功能 |
| `1.8.x` | **legacy，已终结** | 经典功能集。最终版 1.8.22；源码仅存于 `legacy/1.8.22/`（无 2.x 继任者的三个 MC 范围）与归档 `-sources.jar`。只接受致命安全问题的定点修复，且修复后以 `1.8.23` 发布 |
| `1.8.22-container-safe` | **已废弃** | HUD 容器保留变体（26.2）。其客户端-服务端容器同步语义已被 2.2.x `StorageActionPayload`/`StorageServerUtil` 协议正式实现并超越（服务端校验、更多存储类型、回归测试）。不再维护，用户迁移至 2.2.x |

## 源码目录规则

- 活跃源码只允许存在于本仓库 `versions/<mc-version>/`，且必须登记进 `versions.json`
  （门禁强制目录与清单一一对应）。
- 禁止用 mod 版本号命名源码目录；禁止仓库外再建完整副本（历史教训：12 个无 git
  追踪的副本导致修复漂移与版本号失控，已于 2026-08-29 全部冻结；盘点和回调决策见
  [`docs/SOURCE_CONVERGENCE.md`](docs/SOURCE_CONVERGENCE.md)）。
- 经典线保育源码位于 `legacy/1.8.22/`，仅覆盖无 2.x 继任者的 MC 范围
  （1.21、1.21.2-1.21.3、1.21.5），不参与 CI（与 legacyRoot 同策略）。

## 发布流程

1. 修改代码于对应 `versions/<mc>/`。
2. 在 `versions.json` 提升该功能线所有版本的 `modVersion`（同功能集同号）。
3. `scripts/Build-All.ps1`（或 CI matrix）构建全部维护线。
4. `scripts/Test-VersionManifest.ps1` 必须通过。
5. `scripts/New-ReleaseBundle.ps1` 生成 release train，产物落入唯一出口目录
   `02-发布成品/BetterShulkerHud-Modrinth-Jars/`，同步更新
   `LATEST-VERSIONS.md` 与 `SHA256SUMS-LATEST.txt`。
