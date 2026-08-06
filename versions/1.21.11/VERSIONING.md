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
