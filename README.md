# God Wand 1.4.0-dev

One wand with six skills: walk through mountains, pull enemies into a gravity orb, raise a drill, unleash Supernova, fire a stone projectile or hold **I am Atomic** until you choose to release.

[Full mod description](DESCRIPTION.md) · [Changelog](CHANGELOG.md)

[Hướng dẫn nhanh bằng tiếng Việt](BAT_DAU_O_DAY.md)

| Minecraft | Loader | Java | Installable file |
| --- | --- | --- | --- |
| 1.21.1 | Fabric + Fabric API | 21 | `godwand-fabric-1.4.0-dev.jar` |
| 1.21.1 | NeoForge | 21 | `godwand-neoforge-1.4.0-dev.jar` |
| 1.20.1 | Fabric + Fabric API | 17 | `godwand-fabric-1.20.1-1.4.0-dev.jar` |
| 1.20.1 | Forge | 17 | `godwand-forge-1.20.1-1.4.0-dev.jar` |

Install one God Wand jar matching your exact Minecraft version and loader. The mod is required on the client and server.

## Skills and controls

Hold **V** with the wand to select a skill. Press **K** for client graphics and model settings.

1. **Spatial Path:** toggle a temporary tunnel and shield. Tap Shift while active for a bridge made from nearby blocks.
2. **Singularity:** hold and release right-click to launch a gravity orb. Right-click again to collapse it early. Displaced terrain returns afterward.
3. **Earth Drill:** charge a drill of floating blocks and slam it into the ground.
4. **Supernova:** hold right-click for seven seconds to release a black hole and explosion.
5. **Stone Cannon:** charge and release a piercing stone projectile.
6. **I am Atomic:** hold through the buildup, ATOMIC pose and compression. The dark pause waits for you to release right-click, triggering the blast. Movement is locked and incoming damage is blocked during the cast. Esc/F5 skips its camera; skipping after detonation also restores movement.

AUTO selects Aqua Heartia for skills 3/5, Shadow Blade for skill 6, and Eclipse for the others. K also supports a manual model choice.

## Terrain and modpacks

[Balance settings](docs/BALANCE_CONFIG.md) cover per-skill/per-charge damage and cooldowns. All wand appearances deal 5 normal attack damage.

Permanent terrain changes are configurable in `config/godwand-server.json`; defaults retain destructive spell behavior. Use the [terrain guide and presets](docs/TERRAIN_CONFIG.md) for damage-only spells or reversible block movement. An unreadable or malformed server config stops startup instead of enabling destructive defaults.

Client graphics settings are in `config/godwand-client.json`. [Dedicated-server verification and limits](docs/DEDICATED_SERVER.md) describes what has actually been tested.

## Build

Preview jars are collected in `build/preview/1.4.0-dev/`; released 1.3.1 jars remain in `build/release/`.

Run from this repository root:

```powershell
# Build and check all four variants, then collect their installable jars
.\gradlew.bat releaseJars

# Build only Fabric/NeoForge 1.21.1
.\gradlew.bat build

# Start Fabric 1.21.1 for manual testing
.\gradlew.bat :fabric:runClient
```

On Linux/macOS use `./gradlew`. Outputs from `releaseJars` are in `build/release/`. Java 21 is needed for 1.21.1; the 1.20.1 ports need a Java 17 toolchain.

See [development](docs/DEVELOPMENT.md) for individual builds and tests, and [source of truth](docs/SOURCE_OF_TRUTH.md) for the consolidation history. This root is an aggregator; it has no separate legacy implementation or fifth release jar.

## Release status

1.4.0-dev is a preview following the released 1.3.x builds. Only the four versions listed above are supported. Minecraft 1.21.11 and 26.3 port investigations are recorded under ports/; neither is playable yet. Remaining audit findings are tracked in [the audit report](docs/AUDIT_2026-10-27.md).

## License

Source code: [MIT](LICENSE). Modpack use is allowed. The bundled Bebas Neue font retains its included SIL Open Font License.
