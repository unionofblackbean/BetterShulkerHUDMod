# Release trains

Minecraft 游戏版本和 Better Shulker HUD Mod 版本是两套独立版本号，任何新分支、PR、Release 和正式附件都必须同时标明两者，禁止只写一个裸版本号。

统一格式：

```text
分支：codex/mc-<Minecraft版本>-mod-<Mod版本>-<用途>
PR/Release 标题：[MC <Minecraft版本>][Mod <Mod版本>] <内容>
附件：BetterShulkerHud-<Mod版本>+mc<Minecraft版本>.jar
```

示例：

```text
codex/mc-26.2.x-mod-2.2.3-fix-scrollbar
[MC 26.2.x][Mod 2.2.3] 修复 HUD 滚动条输入
BetterShulkerHud-2.2.3+mc26.2.x.jar
```

涉及全部维护版本时，Minecraft 写 `all`，Mod 部分按实际情况列出全部版本，例如：

```text
codex/mc-all-mod-2.0.6-2.1.2-2.1.3-2.2.3-fix-scrollbar
[MC all][Mod 2.0.6 / 2.1.2 / 2.1.3 / 2.2.3] 修复 HUD 滚动条输入
```

每个 GitHub Release 都由此目录下同名 JSON 清单驱动。标签与清单中的 `train` 必须完全一致，例如：

```text
release-trains/release-2026.08-r1-beta.1.json
release-trains/release-2026.08-r1-beta.1.md
```

Beta 清单只列本轮变化的版本；正式清单列出当时全部维护版本。发布脚本会从 `versions.json` 解析真实 Minecraft 游戏版本和 Mod 版本、构建 JAR 与源码 JAR、检查 `fabric.mod.json` 和 Java class major、生成 `ASSETS.json` 与 `SHA256SUMS.txt`，再由 GitHub Actions 创建 Release。

本仓库不会通过此流程自动上传 Modrinth 或 CurseForge。
