# Release trains

每个 GitHub Release 都由此目录下同名 JSON 清单驱动。标签与清单中的 `train` 必须完全一致，例如：

```text
release-trains/release-2026.08-r1-beta.1.json
release-trains/release-2026.08-r1-beta.1.md
```

Beta 清单只列本轮变化的版本；正式清单列出当时全部维护版本。发布脚本会从 `versions.json` 解析真实 Mod 版本、构建 JAR 与源码 JAR、检查 `fabric.mod.json` 和 Java class major、生成 `ASSETS.json` 与 `SHA256SUMS.txt`，再由 GitHub Actions 创建 Release。

本仓库不会通过此流程自动上传 Modrinth 或 CurseForge。
