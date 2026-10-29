# God Wand 1.3.2 — Minecraft 26.3

Fabric and NeoForge test builds include all six skills, models, custom SFX, interactive Atomic release and current balance/config settings. Both builds and automated tests pass. Gameplay, shaderpack compatibility and multiplayer still need owner testing.

Run from this directory with **Java 25**:

```powershell
.\gradlew.bat build                # both loaders and shared tests
.\gradlew.bat :fabric:build        # Fabric only
.\gradlew.bat :neoforge:build      # NeoForge only
```

Outputs:

- `fabric/build/libs/godwand-fabric-26.3-1.3.2.jar`: Fabric Loader 0.20.1+ and Fabric API for 26.3.
- `neoforge/build/libs/godwand-neoforge-26.3-1.3.2.jar`: NeoForge 26.3.0.99+ for Minecraft 26.3.

Install exactly the JAR matching your loader, on both client and server. Local test copies are collected in the canonical `build/release/26.3/`. JARs are not published to GitHub.

The build does not launch Minecraft. Minecraft 1.21.11 remains pending. See [PORT_STATUS.md](PORT_STATUS.md) for scope and verification limits. [LICENSE](../../LICENSE) separates MIT Java code, reserved custom assets/SFX, and the OFL font.

Routine changes belong in canonical common source; this directory retains native-only bridges and loader hooks. Root releaseJars builds this port with the other four variants. See [shared sources](../../docs/SHARED_SOURCES.md).
