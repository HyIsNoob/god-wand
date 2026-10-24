# Multi-Loader (Fabric + NeoForge 1.21.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the single-module Fabric mod into a clean, standalone Multi-Loader architecture (`common`, `fabric`, `neoforge`) targeting Minecraft 1.21.1, producing standalone jars for both Fabric and NeoForge with zero third-party runtime library requirements.

**Architecture:** Shared core game logic (all 5 skills, spatial displacement, custom post-fx shaders, client renderers, 3D procedural wand model, soundscapes, data recipes, and config) resides in `:common`. Platform-specific entry points, registration, network packet dispatch, and render event callbacks are implemented in `:fabric` and `:neoforge` via a lightweight `Services` abstraction.

**Tech Stack:** Java 21, Gradle 8.10+, Fabric Loom / ModDevGradle, Minecraft 1.21.1, Fabric API 0.104.0+, NeoForge 21.1.x, JUnit 5.

**Spec:** Multi-loader porting request from user turn 2026-10-24.

## Global Constraints

- Zero emojis in all code, comments, and commit messages.
- Maintain full parity of all 5 wand skills, sounds, post-fx bloom/distortion, camera trauma shake, and custom `.ogg` support.
- Do not add heavy runtime framework dependencies (like Architectury API runtime) if possible; keep jars standalone and modpack-friendly.
- Preserve existing config structure (`config/godwand.json`).
- Ensure all unit tests continue to pass on both platforms.

---

### Task 1: Project Backup & Multi-Project Gradle Scaffolding

**Files:**
- Create: `settings.gradle` (multi-project: include `common`, `fabric`, `neoforge`)
- Modify: `build.gradle` (root orchestrator)
- Modify: `gradle.properties` (shared versions and loader versions)
- Backup: `.local-backups/20261024-pre-multiloader-fabric-1.1.0/`

**Interfaces:**
- Consumes: Existing single-project Fabric setup.
- Produces: Multi-project Gradle layout recognizing `:common`, `:fabric`, and `:neoforge`.

- [ ] **Step 1: Backup current working Fabric project**
Copy `src/`, `build.gradle`, `settings.gradle`, `gradle.properties` into `.local-backups/20261024-pre-multiloader-fabric-1.1.0/`.

- [ ] **Step 2: Update `settings.gradle` to declare subprojects**
```groovy
pluginManagement {
    repositories {
        maven { url = "https://maven.fabricmc.net/" }
        maven { url = "https://maven.neoforged.net/releases" }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "godwand"
include("common")
include("fabric")
include("neoforge")
```

- [ ] **Step 3: Configure root `build.gradle` and subproject properties in `gradle.properties`**
Add `neoforge_version=21.1.72` and configure root `build.gradle` to define shared compile options (Java 21, encoding UTF-8, repository declarations).

- [ ] **Step 4: Verify Gradle evaluates all 3 subprojects**
Run: `.\gradlew.bat projects --console=plain`
Expected: Lists `Project ':common'`, `Project ':fabric'`, `Project ':neoforge'`.

- [ ] **Step 5: Commit**
```bash
git add settings.gradle build.gradle gradle.properties
git commit -m "chore: setup multi-project gradle scaffolding for common, fabric, and neoforge"
```

---

### Task 2: Service Platform Abstraction & Shared Code Migration to `:common`

**Files:**
- Create: `common/build.gradle`
- Create: `common/src/main/java/net/godwand/platform/Services.java`
- Create: `common/src/main/java/net/godwand/platform/IPlatformHelper.java`
- Create: `common/src/main/java/net/godwand/platform/INetworkHelper.java`
- Move: `src/main/java/net/godwand/*` -> `common/src/main/java/net/godwand/*` (except loader entrypoints)
- Move: `src/main/resources/assets/*` -> `common/src/main/resources/assets/*`
- Move: `src/main/resources/data/*` -> `common/src/main/resources/data/*`
- Move: `src/test/java/net/godwand/*` -> `common/src/test/java/net/godwand/*`

**Interfaces:**
- `INetworkHelper`: `sendToPlayer(ServerPlayerEntity player, CustomPayload payload)`, `sendToNearby(ServerWorld world, Vec3d pos, double radius, CustomPayload payload)`, `sendToServer(CustomPayload payload)`.
- `IPlatformHelper`: `isModLoaded(String modId)`, `getPlatformName()`, `getConfigDirectory()`.

- [ ] **Step 1: Write `common/build.gradle`**
Configure Fabric Loom in common module with official/yarn mappings so common code compiles cleanly.

- [ ] **Step 2: Define platform abstraction interfaces**
Implement `IPlatformHelper.java`, `INetworkHelper.java`, and `Services.java` using Java `ServiceLoader`.

- [ ] **Step 3: Move shared assets, resources, shaders, and core packages into `:common`**
Move skill managers, spatial displacement, post-fx, models, renderers, and tests to `common/`.

- [ ] **Step 4: Replace direct Fabric network calls in common managers with `Services.NETWORK`**
Update `RatioCollapseManager`, `SupernovaManager`, `StoneCannonManager`, and `ShockwaveSlicer` to dispatch S2C packets via `Services.NETWORK.sendToNearby(...)` and `Services.NETWORK.sendToPlayer(...)`.

- [ ] **Step 5: Run tests in `:common`**
Run: `.\gradlew.bat :common:test --console=plain`
Expected: PASS all tests.

- [ ] **Step 6: Commit**
```bash
git add common/
git commit -m "refactor: migrate shared logic and assets into common module with platform services"
```

---

### Task 3: Fabric Subproject Implementation & Parity Verification

**Files:**
- Create: `fabric/build.gradle`
- Create: `fabric/src/main/java/net/godwand/fabric/GodWandFabricMod.java`
- Create: `fabric/src/main/java/net/godwand/fabric/client/GodWandFabricClientMod.java`
- Create: `fabric/src/main/java/net/godwand/fabric/platform/FabricPlatformHelper.java`
- Create: `fabric/src/main/java/net/godwand/fabric/platform/FabricNetworkHelper.java`
- Create: `fabric/src/main/resources/fabric.mod.json`
- Create: `fabric/src/main/resources/META-INF/services/net.godwand.platform.IPlatformHelper`
- Create: `fabric/src/main/resources/META-INF/services/net.godwand.platform.INetworkHelper`
- Move: `src/main/resources/godwand.accesswidener` -> `fabric/src/main/resources/godwand.accesswidener`
- Move: `src/main/resources/godwand.mixins.json` -> `fabric/src/main/resources/godwand.mixins.json`

**Interfaces:**
- Consumes: `:common` classes and `IPlatformHelper`/`INetworkHelper`.
- Produces: Working `fabric/build/libs/godwand-fabric-1.1.0.jar`.

- [ ] **Step 1: Write `fabric/build.gradle`**
Link `:fabric` to `:common`, configuring Loom, fabric-loader, and fabric-api.

- [ ] **Step 2: Implement `FabricPlatformHelper` and `FabricNetworkHelper`**
Implement packet sending using `ServerPlayNetworking.send` and `ClientPlayNetworking.send`. Register in `META-INF/services`.

- [ ] **Step 3: Implement `GodWandFabricMod` and `GodWandFabricClientMod`**
Register item, S2C packet receivers, client tick events, HUD callback, and translucent world render events.

- [ ] **Step 4: Build Fabric jar and verify tests**
Run: `.\gradlew.bat :fabric:build --console=plain`
Expected: BUILD SUCCESSFUL, producing `fabric/build/libs/godwand-fabric-1.1.0.jar`.

- [ ] **Step 5: Copy Fabric jar to SKLauncher mods folder and verify game load**
Deploy to `C:\Users\khang\AppData\Roaming\.sklauncher\instances\fabric1-21-1\mods\` and verify version 1.1.0 loads.

- [ ] **Step 6: Commit**
```bash
git add fabric/
git commit -m "feat: complete fabric subproject in multi-loader setup"
```

---

### Task 4: NeoForge Subproject Implementation

**Files:**
- Create: `neoforge/build.gradle`
- Create: `neoforge/src/main/java/net/godwand/neoforge/GodWandNeoForgeMod.java`
- Create: `neoforge/src/main/java/net/godwand/neoforge/client/GodWandNeoForgeClientMod.java`
- Create: `neoforge/src/main/java/net/godwand/neoforge/platform/NeoForgePlatformHelper.java`
- Create: `neoforge/src/main/java/net/godwand/neoforge/platform/NeoForgeNetworkHelper.java`
- Create: `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `neoforge/src/main/resources/META-INF/services/net.godwand.platform.IPlatformHelper`
- Create: `neoforge/src/main/resources/META-INF/services/net.godwand.platform.INetworkHelper`
- Create: `neoforge/src/main/resources/godwand.neoforge.mixins.json`

**Interfaces:**
- Consumes: `:common` classes, NeoForge 21.1.x API.
- Produces: Working `neoforge/build/libs/godwand-neoforge-1.1.0.jar`.

- [ ] **Step 1: Write `neoforge/build.gradle`**
Apply NeoForge plugin / Loom NeoForge, depend on `:common` and NeoForge 21.1.x.

- [ ] **Step 2: Implement `NeoForgePlatformHelper` and `NeoForgeNetworkHelper`**
Implement packet sending using NeoForge's `PacketDistributor` and register in `META-INF/services`.

- [ ] **Step 3: Implement `GodWandNeoForgeMod` (@Mod)**
Use `DeferredRegister.createItems` to register `god_wand`, hook `RegisterPayloadHandlersEvent` to register all 6 network payloads.

- [ ] **Step 4: Implement `GodWandNeoForgeClientMod`**
Subscribe to `RenderLevelStageEvent.AFTER_TRANSLUCENT` to render Cannon Pressure, Impact Debris, and Supernova Accretion.
Subscribe to `RegisterGuiLayersEvent` for `CustomImpactFrameOverlay`.
Subscribe to `ClientTickEvent.Post` for soundscape and cinematic updates.

- [ ] **Step 5: Create `META-INF/neoforge.mods.toml`**
Declare mod ID `godwand`, version `1.1.0`, dependencies on `neoforge` and `minecraft ~1.21.1`.

- [ ] **Step 6: Build NeoForge jar**
Run: `.\gradlew.bat :neoforge:build --console=plain`
Expected: BUILD SUCCESSFUL, producing `neoforge/build/libs/godwand-neoforge-1.1.0.jar`.

- [ ] **Step 7: Commit**
```bash
git add neoforge/
git commit -m "feat: complete neoforge 1.21.1 subproject in multi-loader setup"
```

---

### Task 5: Final Multi-Loader Verification & Build Pipeline

**Files:**
- Modify: `README.md` (Add Multi-Loader build instructions)
- Verify: Jars produced by `./gradlew.bat build`

- [ ] **Step 1: Run root build to produce all jars simultaneously**
Run: `.\gradlew.bat build --console=plain`
Expected: BUILD SUCCESSFUL, producing both:
- `fabric/build/libs/godwand-fabric-1.1.0.jar`
- `neoforge/build/libs/godwand-neoforge-1.1.0.jar`

- [ ] **Step 2: Verify sound and asset packing in both jars**
Inspect both jars to ensure `earth_drill_sequence.ogg`, `supernova_sequence.ogg`, shaders, and models are present in both distributions.

- [ ] **Step 3: Deploy and verify Fabric 1.21.1 in launcher**
Deploy `godwand-fabric-1.1.0.jar` to local launcher instance.

- [ ] **Step 4: Final commit**
```bash
git add .
git commit -m "chore: finalize multi-loader architecture for 1.21.1 fabric and neoforge"
```
