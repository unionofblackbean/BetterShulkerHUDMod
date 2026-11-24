# Versioning

Release artifacts use:

`BetterShulkerHud-<minecraft-version-or-range>-<mod-semver>.jar`

- The Minecraft version identifies compatibility and never changes the Mod version.
- Branches with the same feature set use the same Mod version.
- PATCH is for compatible bug fixes, MINOR for compatible features, and MAJOR for incompatible workflow or configuration changes.
- Pre-releases use SemVer suffixes such as `2.1.0-beta.1`.

Current feature lines:

- `1.8.22`: classic feature set.
- `2.0.0`: water-bucket replacement, death-protection restocking, separate offhand restocking, and the configurable HUD-to-offhand hotkey.
- `2.0.1`: safe swapping for occupied offhands.
- `2.0.2`: recipe-book coexistence, optional ModernUI integration, and CozyUI+ sprite adaptation.
- `2.0.3`: programmatic batch storage that keeps one shulker menu open across successive inventory stacks.
- `2.0.4`: hidden programmatic storage sessions with a four-tick pause before reopening a different shulker.
- `2.0.5`: configurable continuous extraction/storage intervals and same-item shulker target priority.
- `2.0.6`: reliable cross-box extraction queue ownership and verified inventory-menu settlement for Quick Crafting.
- `2.0.7`: rebuilds and rebinds stale inventory screens after programmatic extraction instead of relying on delayed object-identity convergence.
- `2.1.0`: source-specific grouped extraction API and adaptive programmatic transfer timing for Quick Crafting.
- `2.1.1`: prevents AxShulkers automatic restocking from reopening the same shulker until the inventory changes.
