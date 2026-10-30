# Minecraft 1.21.11

Fabric and NeoForge targets for the canonical God Wand source in `../../common`.
`compat/profiles/mc-1.21.11` contains explicit native API adaptations; generated Java and resources live in `../../build/shared/mc-1.21.11`.
Do not copy gameplay sources into this port.

Build from this directory with Java 25 running Gradle:

```powershell
./gradlew.bat build --console=plain
```

Shipped classes target Java 21. The test compiler uses Java 25 for native bytecode inspection.
Dependencies: Minecraft 1.21.11, Fabric Loader 0.20.1 / Fabric API 0.141.6+1.21.11, NeoForge 21.11.48.
The mod version comes from the canonical root `gradle.properties`.

Verified on 2026-10-30: full common/Fabric/NeoForge build, 250 common tests, 4 Fabric tests and 5 NeoForge tests; Java 21 archive checks for both loader JARs. Checks cover native mixin targets and remapping, GPU draw arguments, world-composite ordering, forward depth shaders, std140 layouts, item models, recipe decoding, damage and displacement persistence.

In-game visual fit, shaderpack compatibility and multiplayer gameplay remain manual validation. No client or game automation was run.
