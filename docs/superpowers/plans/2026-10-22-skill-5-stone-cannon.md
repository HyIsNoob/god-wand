# Skill 5: Rudeus Hyper-Sonic Stone Cannon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Skill 5 (Rudeus Hyper-Sonic Stone Cannon - Pháo Đá Động Năng Khí Động Học) in the God Wand mod, featuring in-front earthen block gathering, spiraling aerodynamic wind vortices, charge-scaled drill projectile, hyper-velocity supersonic flight with wind helix trails, and cataclysmic kinetic impact crater excavation.

**Architecture:** 
1. `StoneCannonProjectileEntity` handles projectile collision, charge state, and flight mechanics.
2. `StoneCannonManager` manages the 4-tier charging phase (gathering ground stone shards, spiraling wind vortex cone at chest focus point), firing recoil, and impact crater excavation with reversible `SpatialDisplacementManager` blocks.
3. `StoneCannonEntityRenderer` provides a custom 3D aerodynamic tapered drill model on the client, continuously rifling and rotating along its forward velocity vector.
4. `GodWandItem` and `GodWandMod` register Mode 5 (`MODE_STONE_CANNON`), expanding `TOTAL_MODES` from 4 to 5 with full English localization and RPG power meter.

**Tech Stack:** Fabric Loader 1.21.1, Fabric API, Loom, Java 21 LTS, JOML math, Minecraft Client Rendering pipeline (`EntityRenderer`, `BlockRenderManager`), JUnit 5.

---

## Global Constraints
- Minecraft 1.21.1 Yarn mappings.
- Reversible spatial displacement: No permanent destruction of user terrain; crater blocks use `SpatialDisplacementManager.displaceBlock` with automatic restoration.
- Consistent Discrete 4-Tier Charge System: Level I / II / III / IV (MAX).
- Zero performance stutter: Multi-threaded / server tick budgeted particles and display entities.

---

### Task 1: Projectile Entity & Mathematical Dynamics

**Files:**
- Create: `src/main/java/net/godwand/entity/StoneCannonProjectileEntity.java`
- Create: `src/test/java/net/godwand/skill/StoneCannonMathTest.java`

**Interfaces:**
- Produces: `StoneCannonProjectileEntity` extending `ProjectileEntity` with:
  - `setChargeLevel(int level)`, `getChargeLevel()`
  - Collision handling calling `StoneCannonManager.onProjectileImpact`
  - Flight aerodynamic wind vortex particles
- Test verifies velocity scaling, charge levels, and vortex math formulas.

- [ ] **Step 1: Write failing unit test for Stone Cannon math**
Create `src/test/java/net/godwand/skill/StoneCannonMathTest.java` verifying charge thresholds, velocity scaling, and damage formulas.

- [ ] **Step 2: Run test to verify it fails**
Run `.\gradlew.bat test --tests net.godwand.skill.StoneCannonMathTest`

- [ ] **Step 3: Implement `StoneCannonProjectileEntity.java`**
Implement the entity with charge level NBT serialization, `canHit` filtering, and in-flight helical wind vortex generation.

- [ ] **Step 4: Run test to verify it passes**
Run `.\gradlew.bat test --tests net.godwand.skill.StoneCannonMathTest`

- [ ] **Step 5: Commit**
`git add src/main/java/net/godwand/entity/StoneCannonProjectileEntity.java src/test/java/net/godwand/skill/StoneCannonMathTest.java`
`git commit -m "feat: implement StoneCannonProjectileEntity and kinetic scaling math"`

---

### Task 2: StoneCannonManager - Charge Vortex, Ground Inflow & Kinetic Excavation

**Files:**
- Create: `src/main/java/net/godwand/skill/StoneCannonManager.java`
- Create: `src/test/java/net/godwand/skill/StoneCannonManagerTest.java`

**Interfaces:**
- Produces:
  - `StoneCannonManager.tickCharge(ServerPlayerEntity player, int chargeTicks, int level)`
  - `StoneCannonManager.fire(ServerPlayerEntity player, int chargeDuration)`
  - `StoneCannonManager.onProjectileImpact(StoneCannonProjectileEntity projectile, HitResult hit)`
  - `StoneCannonManager.getCraterRadius(int level)`, `StoneCannonManager.getDirectDamage(int level)`

- [ ] **Step 1: Write unit test for StoneCannonManager**
Create `src/test/java/net/godwand/skill/StoneCannonManagerTest.java` testing crater radius, direct damage, and splash damage scaling.

- [ ] **Step 2: Run test to verify it fails**
Run `.\gradlew.bat test --tests net.godwand.skill.StoneCannonManagerTest`

- [ ] **Step 3: Implement `StoneCannonManager.java`**
Implement charging particles (ground stone inflow, spiraling wind cone), firing recoil, projectile launch, impact damage, audio layering, and spatial crater displacement.

- [ ] **Step 4: Run test to verify it passes**
Run `.\gradlew.bat test --tests net.godwand.skill.StoneCannonManagerTest`

- [ ] **Step 5: Commit**
`git add src/main/java/net/godwand/skill/StoneCannonManager.java src/test/java/net/godwand/skill/StoneCannonManagerTest.java`
`git commit -m "feat: implement StoneCannonManager with wind vortex and kinetic crater excavation"`

---

### Task 3: Client 3D Drill Rendering & High-Speed Rifling Motion

**Files:**
- Create: `src/main/java/net/godwand/client/StoneCannonEntityRenderer.java`
- Modify: `src/main/java/net/godwand/client/GodWandClientMod.java`

**Interfaces:**
- Produces:
  - `StoneCannonEntityRenderer extends EntityRenderer<StoneCannonProjectileEntity>`
  - Registration in `EntityRendererRegistry.register(GodWandMod.STONE_CANNON_ENTITY_TYPE, StoneCannonEntityRenderer::new)`

- [ ] **Step 1: Implement `StoneCannonEntityRenderer.java`**
Render 3-tier aerodynamic conical drill (nose cone, fluted shaft, finned base) oriented to projectile velocity and rotating continuously along its axis.

- [ ] **Step 2: Register renderer in `GodWandClientMod.java`**
Add `EntityRendererRegistry.register(GodWandMod.STONE_CANNON_ENTITY_TYPE, StoneCannonEntityRenderer::new)`.

- [ ] **Step 3: Compile verification**
Run `.\gradlew.bat compileJava` to verify client classes compile without error.

- [ ] **Step 4: Commit**
`git add src/main/java/net/godwand/client/StoneCannonEntityRenderer.java src/main/java/net/godwand/client/GodWandClientMod.java`
`git commit -m "feat: implement StoneCannonEntityRenderer with aerodynamic rifling rotation"`

---

### Task 4: Mod & Item Integration (Mode 5, Handlers, Callbacks)

**Files:**
- Modify: `src/main/java/net/godwand/GodWandMod.java`
- Modify: `src/main/java/net/godwand/item/GodWandItem.java`
- Modify: `src/main/resources/assets/godwand/lang/en_us.json`

**Interfaces:**
- Produces:
  - `GodWandMod.STONE_CANNON_ENTITY_TYPE`
  - `GodWandItem.MODE_STONE_CANNON = 4`, `TOTAL_MODES = 5`
  - Switch skill handling, `usageTick`, `onStoppedUsing`, and tooltips.

- [ ] **Step 1: Register entity in `GodWandMod.java`**
Register `STONE_CANNON_ENTITY_TYPE` in `Registries.ENTITY_TYPE`.

- [ ] **Step 2: Update `GodWandItem.java`**
Add `MODE_STONE_CANNON`, handle charge in `usageTick` using `StoneCannonManager.tickCharge`, and fire in `onStoppedUsing` using `StoneCannonManager.fire`.

- [ ] **Step 3: Update `en_us.json`**
Add localization entries for Mode 5.

- [ ] **Step 4: Run unit tests**
Run `.\gradlew.bat test` to verify all tests pass.

- [ ] **Step 5: Commit**
`git add src/main/java/net/godwand/GodWandMod.java src/main/java/net/godwand/item/GodWandItem.java src/main/resources/assets/godwand/lang/en_us.json`
`git commit -m "feat: integrate Skill 5 Stone Cannon into GodWandItem and entity registry"`

---

### Task 5: End-to-End Build, Verification & SKLauncher Deployment

**Files:**
- Production build: `build/libs/godwand-1.0.0.jar`
- Deployment: `C:\Users\khang\AppData\Roaming\.sklauncher\instances\fabric1-21-1\mods\godwand-1.0.0.jar`

- [ ] **Step 1: Run full test suite**
Run `.\gradlew.bat test`

- [ ] **Step 2: Run full production build**
Run `.\gradlew.bat build`

- [ ] **Step 3: Deploy JAR to SKLauncher**
Copy output JAR to launcher directory and verify file properties.

- [ ] **Step 4: Final Git commit & status check**
`git status` and commit any remaining updates.
