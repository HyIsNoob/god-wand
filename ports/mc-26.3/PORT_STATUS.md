# Minecraft 26.3 port status

God Wand version: **1.3.2**. Scope: **Fabric and NeoForge 26.3**. The owner authorized NeoForge on 2026-10-29 after the camera/shake build. Minecraft 1.21.11 remains pending; no build or support is claimed for it.

## Completed source and build work

- All six skills, current damage/cooldown settings and 5-damage melee attacks.
- Native world geometry submission, three wand models, projectile models, menus and HUD.
- Independent Atomic, Supernova and impact post-processing; custom sound playback and hold/release.
- Camera, input, movement lock, protection, networking and terrain restoration APIs.
- Native recipe ingredients, recipe-book unlock and item-model registration.
- Inclusive world-height limits and full container data in save/restore records.

Toolchain: Minecraft 26.3, Java 25, Gradle 9.8.2, Loom 1.17.21, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3. NeoForge 26.3.0.99 uses ModDevGradle 2.0.148. settings.gradle includes both loaders; this project remains separate from the root build.

From `D:/fileluu/Tools/god-wand-1.3.0/ports/mc-26.3`:

```powershell
$env:JAVA_HOME = 'C:/Users/khang/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
.\gradlew.bat build --offline --console=plain
```

Installable test JARs: `fabric/build/libs/godwand-fabric-26.3-1.3.2.jar` and `neoforge/build/libs/godwand-neoforge-26.3-1.3.2.jar`; local copies are collected under the canonical `build/release/26.3/`. No God Wand binary is uploaded to GitHub.

## Verification

**221 common tests, 3 Fabric tests and 5 NeoForge tests pass, with no failures or skipped tests.** The common suite checks gameplay/config behavior, camera math and recovery, geometry, save-data roundtrips and the native recipe codec. Native bytecode checks cover configured mixin targets and world/projection/composite ordering. Three post-chain definitions decode with the actual Minecraft codec; eleven uniform layouts validate; all five fragment shaders compile for both depth conventions. Procedural geometry uses Minecraft 26.3's reversed depth comparison. Fabric access-widener validation, NeoForge access transforms and both packages pass. NeoForge tests additionally verify native item/mixin resources, AfterLevel event ordering before the shared composite/HUD, and explicit camera-view matrices after vanilla restores its view stack. Both loaders register their native pipelines before shader compilation; the shared post-effect hook runs before the 3D HUD.

Local logs and JUnit reports are under build/ and common/build/; generated output is ignored by Git.

## Owner testing still required

No Minecraft client, dedicated server or native computer automation was started. Build/unit checks do not prove visuals, GPU/shaderpack compatibility, FPS or real multiplayer. Test all six spells, V/K, crafting, Atomic cancel/hold/release/skip, and block restoration before publishing.

New 26.3 save-data records roundtrip in tests. Importing pending displacement journals from older Minecraft worlds is not implemented or verified: finish temporary terrain spells and allow their blocks to return before upgrading a world. Keep the old world backup.

## Pending work

1. Owner gameplay feedback and any 26.3 client/server fixes.
2. Fabric/NeoForge 1.21.11: optional, lower priority; do not start alongside 26.3 QA. Old notes remain in ../mc-1.21.11/PORT_STATUS.md.

Updated licensing: Java source is MIT; custom assets/SFX are ARR; Bebas Neue remains OFL-1.1. Both JARs contain the same complete LICENSE as the repository root.

Official references: [Fabric 26.3](https://www.fabricmc.net/2026/09/15/263.html), [26.3 migration primer](https://docs.neoforged.net/primer/docs/26.3/). Actual APIs were checked against the resolved Minecraft and Fabric JARs.
