# God Wand Mod: Comprehensive Visual, Physical & State Refactor Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the God Wand mod's visual fidelity, physical weight, entity population, and state lifecycle to deliver a flawless Living Aura for Skill 1, a majestic celestial vortex for Skill 2, and an authentic 3-Phase Magnetic Snap Reassembly.

**Architecture:** 
1. Redesign `DisplacedBlockRecord` with `DisplacementMode` (`HYSTERESIS_AURA`, `TIMED_LIFECYCLE`, `CUSTOM_SCRIPTED`), live vector tracking, and a 15-tick 3-Phase restoration counter.
2. Upgrade `SpatialDisplacementManager` with 2-tick spawn handshake, dynamic evasion physics, staggered vortex rotation, and 3-Phase Magnetic Snap Reassembly (1:1 state & NBT preservation, two-pass Y-ordered torch/lantern safety).
3. Transform `SpatialPathWalker` (Skill 1) into a true Spherical Aura ($R=3.5$m) + Forward Cone ($6.5$m) with Distance-Driven Hysteresis (holds $d \le 5.5$m, interrupts on return) and absolute floor safety.
4. Scale `ShockwaveSlicer` (Skill 2) to 100% display coverage ($R \le 8$) and up to 1,500 displays ($R > 8$) with Upward Canopy ($\Delta Y \in [+3.5\text{m}, +9.0\text{m}]$) and staggered 2.5° Y-vortex rotation.

**Tech Stack:** Minecraft 1.21.1, Fabric Loader, Fabric API, JOML (Matrix/Vector/Quaternion math), Yarn mappings, Java 21 LTS.

---

## Global Constraints
- Target: Minecraft 1.21.1 Fabric (Yarn mappings), Java 21 LTS.
- Zero-Griefing: 100% of displaced blocks must return to their exact original `BlockState` and `BlockEntity` NBT.
- Torch/Delicate Block Safety: Solid blocks restore at tick 14; non-solid/attached blocks restore at tick 15 in Y-ascending order.
- Floor Protection: Blocks with $y < \lfloor \text{player.getY()} \rfloor$ are strictly protected unless flying with pitch $> 45^\circ$.
- No Packet Floods: Vortex rotation updates are staggered across ticks by `entityId % 3`.

---

### Task 1: Refactor DisplacedBlockRecord & Core State Model

**Files:**
- Modify: `src/main/java/net/godwand/spatial/DisplacedBlockRecord.java`
- Modify: `src/test/java/net/godwand/spatial/SpatialMathTest.java`

**Interfaces:**
- Produces: `DisplacementMode` enum (`HYSTERESIS_AURA`, `TIMED_LIFECYCLE`, `CUSTOM_SCRIPTED`)
- Produces: `restoreTick` (0 = active, 1..15 = restoring), `currentOffset`, `currentRotation`, `boundPlayerUuid`
- Produces: `beginRestoration()`, `interruptRestoration()`, `resetRestoreTick()`

- [ ] **Step 1: Write unit tests for DisplacedBlockRecord state transitions**
- [ ] **Step 2: Update DisplacedBlockRecord with DisplacementMode, live offsets, and restoration helpers**
- [ ] **Step 3: Run `.\gradlew.bat test` to verify clean compilation and tests passing**
- [ ] **Step 4: Commit with `git commit -m "refactor: add DisplacementMode and restoration states to DisplacedBlockRecord"`**

---

### Task 2: Implement 3-Phase Magnetic Snap Reassembly & Two-Pass Torch Safety in SpatialDisplacementManager

**Files:**
- Modify: `src/main/java/net/godwand/spatial/SpatialDisplacementManager.java`

**Interfaces:**
- Consumes: `DisplacedBlockRecord` with `restoreTick`, `DisplacementMode`
- Implements:
  - Phase 1 (restoreTick == 1..10): Pull to 15% distance, rotate towards zero.
  - Phase 2 (restoreTick == 11..13): Magnetic Snap to (0,0,0) with `SoundEvents.BLOCK_VAULT_OPEN_SHUTTER`.
  - Phase 3 (restoreTick == 14 for solid, 15 for delicate): Set exact `BlockState` + NBT, discard display.
  - 2-Tick handshake: T0 spawn at identity, T1 set target transformation with interpolation 8.

- [ ] **Step 1: Implement 3-Phase restoration tick loop in `SpatialDisplacementManager.tick(ServerWorld)`**
- [ ] **Step 2: Implement Phase 1 inward drag, Phase 2 magnetic snap, and Phase 3 two-pass restoration**
- [ ] **Step 3: Ensure exact 1:1 original block state and NBT restoration**
- [ ] **Step 4: Run `.\gradlew.bat compileJava` and `.\gradlew.bat test`**
- [ ] **Step 5: Commit with `git commit -m "feat: implement 3-Phase Magnetic Snap Reassembly and 2-tick spawn handshake"`**

---

### Task 3: Overhaul Skill 1 (SpatialPathWalker) - Living Spherical Aura & Dynamic Evasion

**Files:**
- Modify: `src/main/java/net/godwand/skill/SpatialPathWalker.java`
- Modify: `src/main/java/net/godwand/spatial/SpatialDisplacementManager.java`

**Interfaces:**
- Implements:
  - Spherical Aura ($R = 3.5$m) + Forward Cone ($6.5$m) geometry.
  - Floor safety ($y < \lfloor \text{player.getY()} \rfloor$).
  - Distance-Driven Hysteresis: holds $d \le 5.5$m from `homePos`; triggers restoration at $d > 5.5$m; interrupts restoration if player returns.
  - Dynamic Evasion: every 2 ticks, checks actual flying position vs player chest; pushes away if $d < 2.8$m with duration 3.

- [ ] **Step 1: Implement Spherical Aura + Forward Cone scanning in `SpatialPathWalker.tickPlayer`**
- [ ] **Step 2: Implement Distance-Driven Hysteresis & interrupt-on-return logic**
- [ ] **Step 3: Implement Dynamic Evasion in `SpatialDisplacementManager.tick` for `HYSTERESIS_AURA` records**
- [ ] **Step 4: Run `.\gradlew.bat compileJava` and `.\gradlew.bat test`**
- [ ] **Step 5: Commit with `git commit -m "feat: implement Living Aura with distance hysteresis and dynamic evasion for Skill 1"`**

---

### Task 4: Overhaul Skill 2 (ShockwaveSlicer) - High-Density Display Population & Upward Vortex

**Files:**
- Modify: `src/main/java/net/godwand/skill/ShockwaveSlicer.java`
- Modify: `src/main/java/net/godwand/spatial/SpatialDisplacementManager.java`

**Interfaces:**
- Implements:
  - 100% display coverage for $R \le 8$; up to 1,500 displays for $R > 8$ with surface + 40% interior scaled 1.35x.
  - Upward Canopy: $\text{LiftY} = (\text{blastCenter}.y - \text{pos}.getY()) + 3.5 + (\text{hash01}(\text{pos}) \times 5.5)$.
  - Staggered Vortex Rotation: 2.5° around Y axis every 3 ticks, staggered by `display.getId() % 3`.
  - 100-tick hold followed by 3-Phase Magnetic Snap.

- [ ] **Step 1: Update `ShockwaveSlicer` to sample 100% ($R \le 8$) and up to 1,500 displays ($R > 8$)**
- [ ] **Step 2: Implement Upward Canopy lift formula ($\Delta Y \in [+3.5\text{m}, +9.0\text{m}]$)**
- [ ] **Step 3: Implement staggered 2.5° Y-axis vortex rotation in `SpatialDisplacementManager`**
- [ ] **Step 4: Run `.\gradlew.bat compileJava` and `.\gradlew.bat test`**
- [ ] **Step 5: Commit with `git commit -m "feat: implement high-density upward canopy and staggered vortex for Skill 2"`**

---

### Task 5: End-to-End Build, Verification & Packaging

**Files:**
- Run test suite
- Run full Loom build
- Check generated jar

- [ ] **Step 1: Run `.\gradlew.bat test` to verify all unit tests pass**
- [ ] **Step 2: Run `.\gradlew.bat build` to produce clean `godwand-1.0.0.jar`**
- [ ] **Step 3: Inspect final jar size and contents**
- [ ] **Step 4: Review `git log` and `git status`**
- [ ] **Step 5: Report completion to user with detailed feature summary**
