# God Wand Mod (Fabric 1.21.1) - Architecture & Technical Specification (V2 - Hardened)

## 1. Project Overview & Objectives
- **Mod Name:** God Wand (`godwand`)
- **Target Platform:** Minecraft 1.21.1, Fabric Loader, Fabric API
- **Java Version:** Java 21 LTS, Yarn Mappings
- **Core Concept:** A showcase magic wand introducing two high-impact spatial manipulation skills:
  1. **Skill 1: Spatial Path Walker (Passive Toggle):** As the player walks/sprints forward in a straight line, solid blocks smoothly part outwards into the air, floating, glowing, emitting arcane particles, and seamlessly sliding back to original positions without breaking delicate attachments (torches, lanterns) or displacing the ground under the player's feet.
  2. **Skill 2: Arcane Singularity Orb (Calamity Scale Charged Projectile):** A charged orb projectile that impacts any surface to trigger a multi-tick shockwave excavation (Radius up to $R = 15 - 16$ blocks, $\approx 32$ blocks diameter, ~14,000 blocks), pushing and floating hundreds of glowing debris blocks in a multi-strata anti-gravity shell for 5 seconds before a majestic singularity collapse reconstructs the entire terrain perfectly.

---

## 2. Technical Architecture & Components

```mermaid
flowchart TD
    subgraph Input & Item
        Player[Player Entity] -->|Shift + Right-Click| WandToggle[GodWandItem: Toggle Skill 1]
        Player -->|Hold / Release Right-Click| WandCharge[GodWandItem: Charge & Launch Skill 2]
    end

    subgraph Entities & Projectiles
        WandCharge --> OrbEntity[ArcaneOrbEntity]
        OrbEntity -->|On Block Collision| ImpactTrigger[Singularity Detonation]
    end

    subgraph Spatial Engine
        WandToggle -->|Player Tick Hook| SpatialManager[SpatialDisplacementManager]
        ImpactTrigger -->|Shockwave Slicing Queue| SpatialManager
        
        SpatialManager -->|T0: Save State + Clearable.clear + Air| MemoryRegistry[DisplacementTracker / PersistentState]
        SpatialManager -->|T0: Spawn Display at (0,0,0)| Displays[BlockDisplayEntity Pool]
        SpatialManager -->|T1: Update DataTracker Target Matrix| Displays
        
        SpatialManager -->|Hold Phase: Idle Bobbing| Displays
        SpatialManager -->|Wave Restoration Pass 1: Solid Cubes| RestorationEngine[Two-Pass Restoration Worker]
        SpatialManager -->|Wave Restoration Pass 2: Attached/Torches| RestorationEngine
        RestorationEngine -->|Re-place BlockState + NBT| World[ServerWorld]
    end

    subgraph Mixins & Environmental Guards
        FluidGuard[MixinFlowableFluid: canFlow hook] -->|Block active zone| World
    end
```

### Component Breakdown
1. **`net.godwand.GodWandMod`**:
   - Mod initializer registering items, entities, custom sounds, and server lifecycle hooks.
2. **`net.godwand.item.GodWandItem`**:
   - The magic wand item.
   - Handles `use` (charging skill 2) and `useOnBlock` / sneak-use (toggling skill 1).
   - Manages custom action bar status: `§d§l[✦ SPATIAL PATH: ACTIVE ✦]`.
3. **`net.godwand.entity.ArcaneOrbEntity`**:
   - Custom projectile extending `PersistentProjectileEntity` / `ProjectileEntity`.
   - Charge levels 1 to 3 with dynamic visual scale, particle spirals, and flight physics.
4. **`net.godwand.spatial.SpatialDisplacementManager`**:
   - Master manager running on server tick (`ServerTickEvents.END_WORLD_TICK`).
   - Manages active displacement zones, 2-tick spawn lifecycle, shockwave slicing queues, and wave restoration.
5. **`net.godwand.spatial.DisplacedBlockRecord`**:
   - Data class holding:
     - `BlockPos pos`
     - `BlockState state`
     - `NbtCompound blockEntityNbt` (created via `createNbtWithIdentifyingData`)
     - `UUID displayEntityUuid`
     - `int ageTicks`
     - `int maxLifetimeTicks`
     - `int spawnTickDelay` (for 2-tick interpolation handshake)
     - `Vec3d homePos`
     - `Vec3d displacedTargetPos`
     - `Quaternionf targetRotation`
     - `boolean isFullCube`
6. **`net.godwand.spatial.DisplacementPersistentState`**:
   - Extends `PersistentState` on `ServerWorld` to persist active records across server reloads/crashes.
7. **`net.godwand.mixin.FlowableFluidMixin`**:
   - Mixin intercepting fluid spread (`canFlow`) into any currently displaced coordinates.

---

## 3. Core Engine Mechanics & Bug-Hardened Solutions

### 3.1 Two-Tick Spawn Handshake (Fix for Client DataTracker Snap)
In Minecraft 1.21.1, if a `BlockDisplayEntity` is spawned and its transformation is set in the same tick ($T_0$), the initial `EntityTrackerUpdateS2CPacket` sent to clients includes the target matrix immediately. The client treats this as the spawn base state and snaps without playing interpolation.
- **Tick $T_0$:**
  - Save `BlockState` and `BlockEntity` NBT.
  - Call `Clearable.clear(be)` if present.
  - Set world block to `AIR` with `FORCE_STATE | NOTIFY_LISTENERS | SKIP_DROPS`.
  - Spawn `BlockDisplayEntity` at exact `pos` with **Identity Matrix** (translation = `(0,0,0)`, scale = `(1,1,1)`, rotation = Identity).
  - Register in tracker with `spawnTickDelay = 1`.
- **Tick $T_1$:**
  - Decrement delay. Once 1 tick has passed, execute:
    - `display.setInterpolationDelay(0)`
    - `display.setInterpolationDuration(8)` (Skill 1) or `(12)` (Skill 2)
    - `display.setTransformation(new Transformation(compensatedTranslation, targetRotation, scale, null))`
  - The client now has a valid base at $(0,0,0)$ and smoothly animates to the outward floating position across 8–12 ticks at 60/144+ FPS.

### 3.2 Global Floor Protection & Forward Funnel (Fix for Sprint Pitfall)
- **Global Floor Rule:**
  - To prevent sprinting players from falling into pits caused by forward reach scanning:
    $$\text{pos.getY()} < \lfloor\text{player.getY()}\rfloor \implies \text{SKIP}$$
  - Active across the entire corridor regardless of horizontal distance.
  - If player is flying and looking down with pitch $> 45^\circ$, the vertical plane shifts relative to look angle.
- **Corridor Geometry (Forward Funnel):**
  - Horizontal look direction: $D_{\text{horiz}} = \text{normalize}(lookVec.x, 0, lookVec.z)$.
  - Reach extended to **$6.5$ blocks**: At sprint speed ($5.61$ m/s $\approx 0.28$ m/tick), a 9-tick interpolation cycle requires at least $2.5 - 4.0$ m lead time so blocks are fully parted before the player's hitbox reaches them.
  - Funnel width: narrows at player feet ($R = 1.8$), expands forward to $R = 2.8$ blocks.

### 3.3 Safe BlockEntity Clearing & 1.21.1 Registry API (Fix for Item Dupe & Scatter)
- `SKIP_DROPS` only disables block loot tables; it does **not** suppress `BlockState#onStateReplaced` which triggers `ItemScatterer.spawn()`.
- **Displacement sequence:**
  ```java
  BlockEntity be = world.getBlockEntity(pos);
  NbtCompound nbt = null;
  if (be != null) {
      nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
      Clearable.clear(be); // Empties inventory in-memory before replacement
  }
  world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
  ```
- **Restoration sequence:**
  ```java
  world.setBlockState(pos, record.state(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
  if (record.nbt() != null) {
      BlockEntity be = world.getBlockEntity(pos);
      if (be != null) {
          be.read(record.nbt(), world.getRegistryManager());
          be.markDirty();
      }
  }
  ```
- **Double Blocks (Doors, Beds, Tall Plants):**
  - When either half is detected, the paired block is captured and displaced together to prevent one half breaking into item drops.

### 3.4 Pivot Center Compensation (Fix for Corner Wobble)
`BlockDisplayEntity` origin is at its lower-North-West corner $(0, 0, 0)$, not the center of gravity $(0.5, 0.5, 0.5)$. Applying raw rotation causes eccentric wobble around the corner.
- **Compensated translation formula:**
  $$C = (0.5, 0.5, 0.5)$$
  $$T_{\text{compensated}} = T_{\text{displacement}} + C - (Q_{\text{rot}} \cdot C)$$
- When $Q_{\text{rot}} = \text{Identity}$, $T_{\text{compensated}} = T_{\text{displacement}}$.
- During return animation ($Q \to \text{Identity}, T \to 0$), the transformation smoothly collapses exactly back to $(0, 0, 0)$.

### 3.5 Shockwave Slicing & Multi-Strata Sampling (Fix for Calamity $R=16$ Tick Spike)
Detonating a radius $R=16$ sphere instantaneously excavates ~14,000 blocks in 1 tick, overwhelming `ChunkLightProvider` and freezing the server for 180–350ms.
- **Shockwave Slicing (5 Ticks Excavation):**
  - Divide the $R=16$ sphere into 5 concentric spherical shells:
    $$r_1 \in [0, 3.2], \quad r_2 \in [3.2, 6.4], \quad r_3 \in [6.4, 9.6], \quad r_4 \in [9.6, 12.8], \quad r_5 \in [12.8, 16.0]$$
  - Each tick processes 1 shell (~2,800 blocks), maintaining steady 20 TPS while producing a visual expanding shockwave wave effect outward from the epicenter!
- **Multi-Strata Sampling (Visual Debris):**
  - Instead of only rendering outer crust (which leaves an artificial empty void), we sample up to **450 `BlockDisplayEntity` instances** across 3 concentric strata:
    - Inner Strata ($0.4R$): Scaled up ($1.35\times$), pushed fast toward the outer rim ($R+2$) with violent rotation.
    - Mid Strata ($0.7R$): Scaled standard ($1.15\times$), suspended at $R+1$.
    - Outer Shell ($1.0R$): Pushed gently outward ($R+0.5$), tilted along the crater edge.
  - This delivers the visual spectacle of an entire mountain core being shattered and hovering in orbit, without GPU or entity strain.
- **Wave Restoration (8 Ticks Return):**
  - Slices collapse in reverse from perimeter to center over 8 ticks.

### 3.6 Fluid Infiltration Prevention
- Blocks containing fluid (`FluidBlock`) or waterlogged blocks:
  - `BlockDisplayEntity` cannot render fluid models (`Blocks.WATER` / `Blocks.LAVA` have empty render shapes in `BlockRenderManager`).
  - Fluid source blocks are captured into records and set to `AIR`. Ambient water/lava is accompanied by floating `ParticleTypes.BUBBLE_POP` and `ParticleTypes.DRIPPING_WATER` / `DRIPPING_LAVA` particles.
- **Mixin Guard:**
  - `FlowableFluidMixin` intercepts `canFlow()`: if target `BlockPos` is currently registered in `SpatialDisplacementManager.isPositionDisplaced(pos)`, flow is cancelled. Perimeter water forms a sheer vertical floating water-wall rather than flooding the cavity.

### 3.7 Crash Resilience & Persistent State
- All active displacements are serialized into `DisplacementPersistentState` on the `ServerWorld`.
- On `ServerWorldEvents.LOAD`: If orphaned `BlockDisplayEntity` instances with tag `godwand_displaced` or un-restored state records exist from an ungraceful shutdown/crash, the manager immediately executes a recovery pass, restoring original blocks and cleaning up leftover entities.

---

## 4. Mathematical Motion & Floating Dynamics

### 4.1 Skill 1: Directional Push with Arcane Twist
Let $O$ be the player's eye position, $V$ the normalized horizontal movement direction, and $P$ the block center.
- Perpendicular projection on motion axis:
  $$H = O + ((P - O) \cdot V) V$$
- Outward push vector:
  $$R_{\text{out}} = P - H, \quad d = \|R_{\text{out}}\|, \quad \hat{r} = \frac{R_{\text{out}}}{\max(d, 0.001)}$$
  *(If $d \approx 0$, choose left or right based on coordinate hash $(pos.getX() + pos.getZ()) \pmod 2 == 0$ to cleanly part the center line).*
- Target displacement vector $\Delta_{\text{target}}$:
  $$\Delta_{\text{target}} = \underbrace{\hat{r} \cdot (R_{\text{corridor}} - d + 1.4)}_{\text{Outward Push}} + \underbrace{U \cdot (0.45 + 0.35\sin(\phi_{\text{hash}}))}_{\text{Phase-offset Levitation}} + \underbrace{(V \times \hat{r}) \cdot 0.35}_{\text{Arcane Vortex Twist}}$$
  where $\phi_{\text{hash}} = \text{hash}(pos.asLong()) \pmod{2\pi}$, and $U = (0, 1, 0)$.

### 4.2 Idle Bobbing Phase
While blocks are in the suspended/hold phase, every 10 ticks a subtle vertical wave $\Delta Y \pm 0.15$m with `interpolationDuration(10)` is dispatched, keeping the floating debris gently undulating like an anti-gravity field.

### 4.3 Two-Pass Restoration (Delicate Block Safety)
To prevent torches, wall lanterns, redstone, or signs popping off due to placement order (e.g., torch restored before the supporting wall block):
- **Pass 1 (Solid Base Blocks):**
  - Iterate all records where `state.isFullCube(world, pos) == true`.
  - Re-place block state and restore NBT.
- **Pass 2 (Dependent / Attached Blocks):**
  - Run immediately after Pass 1 (or 1 tick later):
  - Restore all non-solid / attached blocks (`!state.isFullCube()` or `canPlaceAt` dependencies).
  - All supporting walls now exist, so torches and wall lanterns anchor with zero breakage risk.

---

## 5. Skills Specifications Summary

| Feature | Skill 1: Spatial Path Walker | Skill 2: Arcane Singularity Orb |
|---|---|---|
| **Activation** | Shift + Right-Click (Toggle) | Hold Right-Click (Charge) & Release |
| **Range / Scale** | $R = 2.8$m, Forward reach $6.5$m | Charge L1: $R=5$m, L2: $R=10$m, L3: $R=16$m |
| **Displacement Logic** | Continuous forward funnel | 5-tick concentric shockwave slicing |
| **Max Active Displays** | ~120 blocks | ~450 blocks (Multi-strata sampling) |
| **Glow & Light** | Amethyst Purple (`0x9B59B6`), Brightness (15,15) | Cyan (`0x00F0FF`) + Purple, Brightness (15,15) |
| **Suspension Time** | Grace period $16$ ticks ($0.8$s) after passing | $100$ ticks ($5.0$ seconds) |
| **Restoration** | Two-pass smooth return $(0,0,0)$ | Inward vortex wave + Two-pass re-placement |
| **Audio** | Beacon chime, Amethyst hum | Siren charge, Warden/Amethyst detonation, Vault lock |
