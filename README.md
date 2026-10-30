# God Wand 1.4.0 development

Development branch for Fuga, the seventh skill. The stable 1.3.x maintenance checkout remains at `D:/fileluu/Tools/god-wand-1.3.0`; this worktree does not publish or merge into it.

One wand with seven skills: walk through mountains, pull enemies into a gravity orb, raise a drill, unleash Supernova, fire a stone projectile, hold **I am Atomic** until you choose to release, or fire Fuga’s blazing arrow.

[Full mod description](DESCRIPTION.md) · [Changelog](CHANGELOG.md)

| Minecraft | Loader | Java | Installable file |
| --- | --- | --- | --- |
| 1.21.1 | Fabric + Fabric API | 21 | `godwand-fabric-1.4.0.jar` |
| 1.21.1 | NeoForge | 21 | `godwand-neoforge-1.4.0.jar` |
| 1.20.1 | Fabric + Fabric API | 17 | `godwand-fabric-1.20.1-1.4.0.jar` |
| 1.20.1 | Forge | 17 | `godwand-forge-1.20.1-1.4.0.jar` |
| 26.3 | Fabric + Fabric API | 25 | `godwand-fabric-26.3-1.4.0.jar` |
| 26.3 | NeoForge | 25 | `godwand-neoforge-26.3-1.4.0.jar` |

Install one God Wand jar matching your exact Minecraft version and loader. The mod is required on the client and server.

## Skills and controls

Hold **V** with the wand to select a skill. Press **K** for client graphics and model settings.

1. **Spatial Path:** toggle a temporary tunnel and shield. Tap Shift while active for a bridge made from nearby blocks.
2. **Singularity:** hold and release right-click to launch a gravity orb. Right-click again to collapse it early. Displaced terrain returns afterward.
3. **Earth Drill:** charge a drill of floating blocks and slam it into the ground.
4. **Supernova:** hold right-click for seven seconds to release a black hole and explosion.
5. **Stone Cannon:** charge and release a piercing stone projectile.
6. **I am Atomic:** hold through the buildup, ATOMIC pose and compression. The dark pause waits for you to release right-click, triggering the blast. Movement is locked and incoming damage is blocked during the cast. Esc/F5 skips its camera; skipping after detonation also restores movement.

7. **Fuga:** hold to form a fire arrow, aim while standing still, then release to fire. Releasing before the arrow is ready cancels. Toggle its camera in **K**.

AUTO selects Aqua Heartia for skills 3/5, Shadow Blade for skill 6, and Eclipse for the others. K also supports a manual model choice.

## Terrain and modpacks

[Balance settings](docs/BALANCE_CONFIG.md) cover per-skill/per-charge damage and cooldowns. All wand appearances deal 5 normal attack damage.

Permanent terrain changes are configurable in `config/godwand-server.json`; defaults retain destructive spell behavior. Use the [terrain guide and presets](docs/TERRAIN_CONFIG.md) for damage-only spells or reversible block movement. An unreadable or malformed server config stops startup instead of enabling destructive defaults.

Client graphics settings are in `config/godwand-client.json`. [Dedicated-server verification and limits](docs/DEDICATED_SERVER.md) describes what has actually been tested.

## Build

All six supported builds come from canonical `common/` sources plus explicit API adapters. One root command checks and collects every target. See [shared source ownership](docs/SHARED_SOURCES.md) before making changes.

Run from this repository root:

```powershell
# Build and check all six variants, then collect their installable jars
.\gradlew.bat releaseJars

# Build only Fabric/NeoForge 1.21.1
.\gradlew.bat build

# Start Fabric 1.21.1 for manual testing
.\gradlew.bat :fabric:runClient
```

On Linux/macOS use `./gradlew`. Outputs from `releaseJars` are in `build/release/`. Install JDK 17, 21 and 25 for the complete matrix. The wrapper automatically selects the installed JDK 25 to run the build; this does not change the Java version needed to play each Minecraft version. See the development guide if Gradle cannot find an installed JDK.

See [development](docs/DEVELOPMENT.md) for individual builds and tests, and [shared-source ownership](docs/SHARED_SOURCES.md) for where to make changes. The root aggregate also invokes the Java 25 build for Fabric/NeoForge 26.3.

## Release status

This isolated 1.4.0 branch adds Fuga. It is a gameplay preview, not a published release. Build checks do not establish visual quality, multiplayer behavior or FPS/TPS. The 1.3.x maintenance branch remains separate. Minecraft 1.21.11 remains pending.

## License

* **Java source and Java compatibility adapters:** Licensed under the [MIT License](LICENSE).
* **Assets & Audio (`**/src/main/resources/assets/` and top-level `assets/`):** Custom sound effects (SFX), audio arrangements, visual assets, and mod branding are **All Rights Reserved**. Extracting or reusing these assets in standalone projects without explicit permission is prohibited.
* **Font:** The bundled *Bebas Neue* font retains its original [SIL Open Font License (OFL)](https://openfontlicense.org/).
* **Distribution & Modpacks:**
  * **Modpacks:** Permitted, provided the modpack references the official CurseForge/Modrinth release and does not monetize this mod directly.
  * **Third-Party Re-uploads:** Standalone redistribution of compiled `.jar` files on third-party websites or mod platforms (CurseForge, Modrinth, 9Minecraft, etc.) without explicit permission is strictly prohibited.


These paths cover every loader/version module. Restrictions on bundled assets do not restrict the MIT permissions for Java code. Earlier published copies retain their applicable license grants. See [LICENSE](LICENSE) for the complete terms.

## Contributors

Thanks to [KingIronMan2011](https://github.com/KingIronMan2011) for modernizing the build tools and fixing cross-version toolchain and encoding issues ([PR #2](https://github.com/HyIsNoob/god-wand/pull/2)).
