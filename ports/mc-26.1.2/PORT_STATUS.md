# Minecraft 26.1.2 port status

God Wand version: **1.3.2**. Scope: **Fabric only**, as requested on 2026-10-28. NeoForge 26.1.2 and Minecraft 1.21.11 remain pending; no build or support is claimed for them.

## Completed source and build configuration

- All six skills, current damage/cooldown settings and 5-damage melee attacks.
- Native world geometry submission, three wand models, projectile models, menus and HUD.
- Independent Atomic, Supernova and impact post-processing; custom sound playback and hold/release.
- Camera, input, movement lock, protection, networking and terrain restoration APIs.
- Native recipe ingredients, recipe-book unlock and item-model registration.
- Inclusive world-height limits and full container data in save/restore records.

Toolchain: Minecraft 26.1.2, Java 25, Gradle 9.8.2, Loom 1.19.3, Fabric Loader 0.20.1, Fabric API 0.155.3+26.1.2. NeoForge is deliberately absent from settings.gradle.

To build from `ports/mc-26.1.2`:

```powershell
$env:JAVA_HOME = 'C:/Users/khang/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
.\gradlew.bat build --offline --console=plain
```

Installable test JAR: `fabric/build/libs/godwand-fabric-26.1.2-1.3.2.jar`.

## Scope and Verification

- The project mirrors the Minecraft 26.x architecture adapted for 26.1.2 with Fabric API 0.155.3+26.1.2.
- NeoForge 26.1.2 is pending and excluded from build settings.
- Build and execution are initiated by the owner.

Official references: Fabric API 0.155.3+26.1.2, Fabric Loader 0.20.1.

