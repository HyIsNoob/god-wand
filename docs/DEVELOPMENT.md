# Development

## Source layout

- `common/`: shared Minecraft 1.21.1 gameplay, models, effects and JUnit tests.
- `fabric/`: Fabric 1.21.1 registrations and rendering/network hooks.
- `neoforge/`: NeoForge 1.21.1 registrations and rendering/network hooks.
- `fabric-1.20.1/`: standalone Java 17 Fabric port and its checks.
- `forge-1.20.1/`: standalone Java 17 Forge port and its checks.
- `ports/mc-26.3/`: separate Java 25 Fabric/NeoForge builds; see their README/status. 1.21.11 remains pending.
- `tools/`: optional development-only QA. It is not packaged into God Wand.

The 1.20.1 projects remain separate Gradle builds because their Java/Minecraft/loader APIs differ. They consume canonical common sources through strict version adapters; do not edit copied implementations. See [shared-source ownership](SHARED_SOURCES.md). AtomicPortParityTest checks matching choreography, geometry and shader source after normalizing the required clamp API difference.

## Commands from the root

```powershell
.\gradlew.bat build                  # 1.21.1 common tests and both loaders
.\gradlew.bat :common:test          # shared JUnit suite
.\gradlew.bat buildFabric1201       # standalone Fabric 1.20.1
.\gradlew.bat buildForge1201        # standalone Forge 1.20.1
.\gradlew.bat buildAll              # all six builds and their checks
.\gradlew.bat releaseJars           # all six builds plus build/release/*.jar
.\gradlew.bat :fabric:runClient     # ordinary manual Fabric 1.21.1 game
```

On Linux/macOS use `./gradlew`. Add `--offline` when dependencies/toolchains are cached. The port tasks pass that setting to their child wrappers. These tasks start console build processes only; they do not launch Minecraft.

Development versions ending in -dev collect jars under build/preview/<version>/ instead of build/release/, so previews stay separate from released jars.

Root `clean` clears root build output. Each loader/standalone build owns its own build directory. Use the six jars collected in build/release rather than old files left elsewhere.

## QA scope

The common suite checks terrain restoration/config safety, model geometry/buffers, Atomic timing, camera math, mesh limits and port parity. Fabric 1.20.1 verifyAtomic and Forge atomicSelfCheck run as part of their checks. Builds and mocked tests do not establish gameplay, FPS, shaderpack or multiplayer compatibility.

QA game probes only alter their explicitly named local test world. Run ordinary runClient without an init script for manual testing. Never attach a probe to a personal world. Build/unit checks do not need control of the mouse or keyboard.
