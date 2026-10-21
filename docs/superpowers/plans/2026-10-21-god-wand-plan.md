# God Wand Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Fabric 1.21.1 Minecraft mod ("God Wand") introducing a showcase magic wand with two spatial manipulation skills: a passive smooth tunneling path walker that parts blocks outward with anti-gravity floating and zero item breakage, and a charged calamity-scale singularity projectile that hollows out a 32-block crater over 5 shockwave ticks with multi-strata floating debris before smoothly restoring all blocks via a two-pass reconstruction engine.

**Architecture:** The mod uses Minecraft's native `BlockDisplayEntity` for 60+ FPS client-side matrix interpolation, decoupled across a 2-tick spawn handshake ($T_0$ identity spawn, $T_1$ target matrix) to prevent DataTracker snap. Real blocks are temporarily replaced with `AIR` using `FORCE_STATE` while block entities are captured via `createNbtWithIdentifyingData` and cleared via `Clearable.clear()` to prevent item duplication. Pivot center compensation ($C=(0.5,0.5,0.5)$) guarantees natural rotation without corner wobble. The calamity explosion ($R=16$, ~14,000 blocks) is sliced into 5 concentric shells to maintain a steady 20 TPS, with up to 450 multi-strata display entities, and restored via a two-pass algorithm (solid bases first, attached torches second) with mixin fluid guards.

**Tech Stack:** Minecraft 1.21.1, Fabric Loader 0.16.x, Fabric API 0.104.x, Java 21 LTS, Gradle 8.10, Loom 1.7, Yarn Mappings.

**Spec:** `docs/superpowers/specs/2026-10-21-god-wand-design.md`

## Global Constraints
- Mod ID: `godwand`
- Group: `net.godwand`
- Target: Minecraft 1.21.1 (Yarn mappings)
- Java Version: Java 21
- All displacements MUST use 2-tick handshake to guarantee client matrix interpolation.
- Ground safety rule: `pos.getY() < Math.floor(player.getY())` strictly preserved across corridor.
- Clearable inventories MUST be cleared via `Clearable.clear(be)` before setting block to `AIR` to prevent item drops and duplication.
- Restorations MUST use two-pass order (Pass 1: `isFullCube`, Pass 2: attached/dependent) to prevent torches popping off.
- Fluids MUST be blocked from entering active zones via `FlowableFluid` mixin.

---

### Task 1: Project Scaffolding & Build Environment

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/godwand.mixins.json`
- Create: `src/main/resources/assets/godwand/lang/en_us.json`
- Create: `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Produces: Compilable Fabric Loom workspace for Minecraft 1.21.1 on Java 21.

- [ ] **Step 1: Create gradle wrapper and properties**
Create `gradle/wrapper/gradle-wrapper.properties` and `gradle.properties`:
```properties
# gradle.properties
minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
loader_version=0.16.5
fabric_version=0.104.0+1.21.1
mod_version=1.0.0
maven_group=net.godwand
archives_base_name=godwand
```

- [ ] **Step 2: Create settings.gradle and build.gradle**
Configure Loom plugin, repositories, and dependencies (Fabric Loader, Fabric API).
```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven { url = "https://maven.fabricmc.net/" }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "godwand"
```
```groovy
// build.gradle
plugins {
    id 'fabric-loom' version '1.7-SNAPSHOT'
    id 'maven-publish'
}
version = project.mod_version
group = project.maven_group
base { archivesName = project.archives_base_name }
repositories {
    mavenCentral()
}
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}
tasks.withType(JavaCompile).configureEach {
    it.options.release = 21
}
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}
test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Create fabric.mod.json and mixin config**
`src/main/resources/fabric.mod.json`:
```json
{
  "schemaVersion": 1,
  "id": "godwand",
  "version": "${version}",
  "name": "God Wand",
  "description": "Showcase magic wand with spatial deformation and calamity singularity skills.",
  "authors": ["GodWand Team"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": [
      "net.godwand.GodWandMod"
    ]
  },
  "mixins": [
    "godwand.mixins.json"
  ],
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.1",
    "java": ">=21",
    "fabric-api": "*"
  }
}
```
`src/main/resources/godwand.mixins.json`:
```json
{
  "required": true,
  "package": "net.godwand.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "FlowableFluidMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 4: Create base entrypoint stub and test build**
`src/main/java/net/godwand/GodWandMod.java`:
```java
package net.godwand;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GodWandMod implements ModInitializer {
    public static final String MOD_ID = "godwand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("God Wand Mod initialized.");
    }
}
```
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**
```bash
git add .
git commit -m "chore: setup Fabric 1.21.1 workspace with Loom and Java 21"
```

---

### Task 2: Core Data Records & Pivot / Mathematical Utilities

**Files:**
- Create: `src/main/java/net/godwand/spatial/DisplacedBlockRecord.java`
- Create: `src/main/java/net/godwand/spatial/SpatialMath.java`
- Test: `src/test/java/net/godwand/spatial/SpatialMathTest.java`

**Interfaces:**
- Produces:
  - `DisplacedBlockRecord`: immutable state carrier for displaced coordinates.
  - `SpatialMath.calculateCompensatedTranslation(Vec3d displacement, Quaternionf rotation)`: returns `Vec3d` with $(0.5, 0.5, 0.5)$ pivot center compensation.
  - `SpatialMath.calculateTargetDisplacement(Vec3d blockPos, Vec3d playerEyes, Vec3d lookDir, double corridorRadius)`: returns radial displacement vector.

- [ ] **Step 1: Write unit tests for pivot compensation & spatial mathematics**
`src/test/java/net/godwand/spatial/SpatialMathTest.java`:
```java
package net.godwand.spatial;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpatialMathTest {
    @Test
    public void testPivotCompensationIdentity() {
        Vector3f disp = new Vector3f(1.0f, 2.0f, 3.0f);
        Quaternionf identity = new Quaternionf(); // no rotation
        Vector3f compensated = SpatialMath.compensatePivot(disp, identity);
        
        assertEquals(1.0f, compensated.x, 1e-4);
        assertEquals(2.0f, compensated.y, 1e-4);
        assertEquals(3.0f, compensated.z, 1e-4);
    }

    @Test
    public void testPivotCompensationWithRotation() {
        Vector3f disp = new Vector3f(0.0f, 0.0f, 0.0f);
        Quaternionf rot90Y = new Quaternionf().rotateY((float) Math.toRadians(90));
        Vector3f compensated = SpatialMath.compensatePivot(disp, rot90Y);
        
        // Pivot C = (0.5, 0.5, 0.5). Rotated C: (0.5, 0.5, -0.5).
        // C - rot(C) = (0, 0, 1.0).
        assertEquals(0.0f, compensated.x, 1e-4);
        assertEquals(0.0f, compensated.y, 1e-4);
        assertEquals(1.0f, compensated.z, 1e-4);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew test`
Expected: FAIL with compilation error (SpatialMath does not exist yet)

- [ ] **Step 3: Implement SpatialMath and DisplacedBlockRecord**
`src/main/java/net/godwand/spatial/SpatialMath.java`:
```java
package net.godwand.spatial;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SpatialMath {
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    public static Vector3f compensatePivot(Vector3f displacement, Quaternionf rotation) {
        // T_compensated = T_displacement + C - (Q_rot * C)
        Vector3f rotatedPivot = new Vector3f(PIVOT);
        rotation.transform(rotatedPivot);
        
        Vector3f result = new Vector3f(displacement);
        result.add(PIVOT);
        result.sub(rotatedPivot);
        return result;
    }

    public static double hashCoordinateToPhase(long posAsLong) {
        long h = posAsLong * 0x5deece66dL + 0xbL;
        return ((h >> 16) & 0xFFFF) / 65535.0 * 2.0 * Math.PI;
    }
}
```
`src/main/java/net/godwand/spatial/DisplacedBlockRecord.java`:
```java
package net.godwand.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.UUID;

public class DisplacedBlockRecord {
    public final BlockPos pos;
    public final BlockState state;
    public final NbtCompound nbt;
    public UUID displayEntityUuid;
    public int ageTicks;
    public int maxLifetimeTicks;
    public int spawnTickDelay; // 1 tick handshake delay
    public final Vector3f homePos;
    public final Vector3f targetDisplacement;
    public final Quaternionf targetRotation;
    public final boolean isFullCube;
    public boolean transformed;

    public DisplacedBlockRecord(BlockPos pos, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this.pos = pos;
        this.state = state;
        this.nbt = nbt;
        this.displayEntityUuid = null;
        this.ageTicks = 0;
        this.maxLifetimeTicks = maxLifetimeTicks;
        this.spawnTickDelay = 1;
        this.homePos = new Vector3f(pos.getX(), pos.getY(), pos.getZ());
        this.targetDisplacement = targetDisplacement;
        this.targetRotation = targetRotation;
        this.isFullCube = isFullCube;
        this.transformed = false;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**
Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all unit tests pass

- [ ] **Step 5: Commit**
```bash
git add src/main/java/net/godwand/spatial src/test/java/net/godwand/spatial
git commit -m "feat: implement DisplacedBlockRecord and pivot compensation math with unit tests"
```

---

### Task 3: Fluid Interception Guard (MixinFlowableFluid)

**Files:**
- Create: `src/main/java/net/godwand/mixin/FlowableFluidMixin.java`

**Interfaces:**
- Consumes: `SpatialDisplacementManager.isPositionDisplaced(BlockPos pos)`
- Injects into: `net.minecraft.fluid.FlowableFluid#canFlow`

- [ ] **Step 1: Implement FlowableFluidMixin**
Prevent liquids (water, lava) from spreading into coordinates that are actively hollowed out by the wand.
```java
package net.godwand.mixin;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "canFlow", at = @At("HEAD"), cancellable = true)
    private void godwand$preventFlowIntoDisplacedArea(
            BlockView world, BlockPos fluidPos, BlockState fluidBlockState,
            Direction flowDirection, BlockPos targetPos, BlockState targetBlockState,
            FluidState targetFluidState, net.minecraft.fluid.Fluid fluid,
            CallbackInfoReturnable<Boolean> cir) {
        if (SpatialDisplacementManager.isPositionDisplaced(targetPos)) {
            cir.setReturnValue(false);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**
```bash
git add src/main/java/net/godwand/mixin/FlowableFluidMixin.java
git commit -m "feat: add FlowableFluidMixin to prevent fluids entering active displaced zones"
```

---

### Task 4: SpatialDisplacementManager - The Two-Tick Display & Two-Pass Restoration Engine

**Files:**
- Create: `src/main/java/net/godwand/spatial/SpatialDisplacementManager.java`

**Interfaces:**
- Produces:
  - `SpatialDisplacementManager.displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement, Quaternionf rotation, int lifetime, int glowColor)`
  - `SpatialDisplacementManager.tick(ServerWorld world)`: handles 2-tick spawn handshake, idle bobbing, and two-pass restoration.
  - `SpatialDisplacementManager.isPositionDisplaced(BlockPos pos)`: boolean check for fluids and overlap prevention.
  - `SpatialDisplacementManager.emergencyRestoreAll(ServerWorld world)`: safety cleanup on unload/shutdown.

- [ ] **Step 1: Implement core SpatialDisplacementManager**
`src/main/java/net/godwand/spatial/SpatialDisplacementManager.java`:
Must strictly enforce:
1. `Clearable.clear(be)` before replacing with `AIR` to prevent item dupe.
2. Tick $T_0$: Spawn `BlockDisplayEntity` with Identity matrix, `display.setInterpolationDuration(0)`.
3. Tick $T_1$: Set compensated target transformation with `setInterpolationDelay(0)` and `setInterpolationDuration(8)`.
4. Idle Bobbing: every 10 ticks, adjust $\Delta Y \pm 0.15$m.
5. Restoration Pass 1: Solid cubes (`record.isFullCube == true`).
6. Restoration Pass 2: Attached/dependent blocks (`record.isFullCube == false`) with `be.read(record.nbt, world.getRegistryManager())`.
7. Cleanup Display entities and unregister.

```java
package net.godwand.spatial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<BlockPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplayEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    public static boolean isPositionDisplaced(BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(pos);
    }

    public static boolean canDisplace(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getHardness(world, pos) < 0) return false;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.BARRIER)) return false;
        return !isPositionDisplaced(pos);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        BlockState state = world.getBlockState(pos);
        if (!canDisplace(world, pos, state)) return;

        BlockEntity be = world.getBlockEntity(pos);
        NbtCompound nbt = null;
        if (be != null) {
            nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
            Clearable.clear(be);
        }

        boolean isFull = state.isFullCube(world, pos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(pos, state, nbt, targetDisplacement, rotation, lifetime, isFull);

        // Replace with AIR immediately
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        // Tick T0: Spawn BlockDisplayEntity at pos with Identity matrix
        BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        display.setPosition(pos.getX(), pos.getY(), pos.getZ());
        display.setBlockState(state);
        display.setGlowing(true);
        display.setGlowColorOverride(glowColor);
        display.setBrightness(new net.minecraft.entity.decoration.Brightness(15, 15));
        
        // Spawn with identity transformation
        world.spawnEntity(display);
        record.displayEntityUuid = display.getUuid();
        ACTIVE_RECORDS.put(pos, record);
        ACTIVE_ENTITIES.put(display.getUuid(), display);
    }

    public static void tick(ServerWorld world) {
        List<DisplacedBlockRecord> toRestore = new ArrayList<>();

        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            record.ageTicks++;
            BlockDisplayEntity display = ACTIVE_ENTITIES.get(record.displayEntityUuid);

            // Tick T1 Handshake: Apply transformation on second tick
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(8);
                    Vector3f comp = SpatialMath.compensatePivot(record.targetDisplacement, record.targetRotation);
                    display.setTransformation(new net.minecraft.util.math.AffineTransformation(
                            comp, record.targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), null));
                    record.transformed = true;
                }
            } else if (record.ageTicks < record.maxLifetimeTicks - 12 && display != null) {
                // Idle Bobbing every 10 ticks
                if (record.ageTicks % 10 == 0) {
                    float bob = (float) Math.sin((record.ageTicks / 10.0) + SpatialMath.hashCoordinateToPhase(record.pos.asLong())) * 0.15f;
                    Vector3f bobbedDisp = new Vector3f(record.targetDisplacement.x, record.targetDisplacement.y + bob, record.targetDisplacement.z);
                    Vector3f comp = SpatialMath.compensatePivot(bobbedDisp, record.targetRotation);
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(10);
                    display.setTransformation(new net.minecraft.util.math.AffineTransformation(
                            comp, record.targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), null));
                }
            } else if (record.ageTicks == record.maxLifetimeTicks - 10 && display != null) {
                // Return animation: interpolate back to identity (0,0,0)
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(10);
                display.setTransformation(new net.minecraft.util.math.AffineTransformation(
                        new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), null));
            }

            if (record.ageTicks >= record.maxLifetimeTicks) {
                toRestore.add(record);
            }
        }

        if (!toRestore.isEmpty()) {
            executeTwoPassRestoration(world, toRestore);
        }
    }

    private static void executeTwoPassRestoration(ServerWorld world, List<DisplacedBlockRecord> records) {
        // Pass 1: Solid base blocks
        for (DisplacedBlockRecord r : records) {
            if (r.isFullCube) {
                restoreSingleBlock(world, r);
            }
        }
        // Pass 2: Attached & delicate blocks (Torches, Lanterns, Signs)
        for (DisplacedBlockRecord r : records) {
            if (!r.isFullCube) {
                restoreSingleBlock(world, r);
            }
        }
    }

    private static void restoreSingleBlock(ServerWorld world, DisplacedBlockRecord r) {
        world.setBlockState(r.pos, r.state, Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
        if (r.nbt != null) {
            BlockEntity be = world.getBlockEntity(r.pos);
            if (be != null) {
                be.read(r.nbt, world.getRegistryManager());
                be.markDirty();
            }
        }
        BlockDisplayEntity display = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
        if (display != null) {
            display.discard();
        }
        ACTIVE_RECORDS.remove(r.pos);
    }

    public static void emergencyRestoreAll(ServerWorld world) {
        List<DisplacedBlockRecord> all = new ArrayList<>(ACTIVE_RECORDS.values());
        executeTwoPassRestoration(world, all);
    }
}
```

- [ ] **Step 2: Register Server Tick Hook in GodWandMod**
Hook `SpatialDisplacementManager.tick(world)` in `ServerTickEvents.END_WORLD_TICK` and `emergencyRestoreAll` in `ServerLifecycleEvents.SERVER_STOPPING`.
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**
```bash
git add src/main/java/net/godwand/spatial/SpatialDisplacementManager.java src/main/java/net/godwand/GodWandMod.java
git commit -m "feat: implement SpatialDisplacementManager with 2-tick handshake and 2-pass restoration"
```

---

### Task 5: Skill 1 Implementation - Spatial Path Walker

**Files:**
- Create: `src/main/java/net/godwand/skill/SpatialPathWalker.java`
- Modify: `src/main/java/net/godwand/GodWandMod.java`

**Interfaces:**
- Produces:
  - `SpatialPathWalker.togglePlayer(ServerPlayerEntity player)`: activates or deactivates passive mode.
  - `SpatialPathWalker.tickPlayer(ServerPlayerEntity player)`: executes forward funnel scanning with global ground safety rule and directional push.

- [ ] **Step 1: Implement SpatialPathWalker with Global Floor Protection**
`src/main/java/net/godwand/skill/SpatialPathWalker.java`:
- Funnel forward reach: 6.5 blocks.
- Floor rule: strictly ignore any `pos.getY() < Math.floor(player.getY())` (unless player is flying & looking down $> 45^\circ$).
- Calculate radial perpendicular push + phase levitation offset + vortex twist.
- Push blocks with 16 ticks lifetime (refreshed as player advances).

```java
package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialPathWalker {
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE_PLAYERS.contains(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            player.sendMessage(Text.literal("§7[✦ SPATIAL PATH: §cOFF§7]"), true);
        } else {
            ACTIVE_PLAYERS.add(player.getUuid());
            player.sendMessage(Text.literal("§d§l[✦ SPATIAL PATH: §aACTIVE §d§l✦]"), true);
        }
    }

    public static void deactivate(PlayerEntity player) {
        ACTIVE_PLAYERS.remove(player.getUuid());
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        if (!isActive(player)) return;

        ServerWorld world = player.getServerWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);
        
        // Horizontal forward vector when on ground
        Vec3d forwardDir = new Vec3d(lookVec.x, 0, lookVec.z);
        if (forwardDir.lengthSquared() < 0.001) {
            forwardDir = new Vec3d(1, 0, 0);
        } else {
            forwardDir = forwardDir.normalize();
        }

        double floorY = Math.floor(player.getY());
        double reach = 6.5;
        double radius = 2.8;

        int minX = MathHelper.floor(player.getX() - radius - reach);
        int maxX = MathHelper.floor(player.getX() + radius + reach);
        int minY = (int) floorY;
        int maxY = MathHelper.floor(player.getY() + 3.5);
        int minZ = MathHelper.floor(player.getZ() - radius - reach);
        int maxZ = MathHelper.floor(player.getZ() + radius + reach);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);
                    
                    // Global Floor Protection
                    if (y < floorY) continue;

                    Vec3d blockCenter = new Vec3d(x + 0.5, y + 0.5, z + 0.5);
                    Vec3d toBlock = blockCenter.subtract(eyePos);
                    
                    // Project onto forward direction
                    double forwardDist = toBlock.dotProduct(forwardDir);
                    if (forwardDist < -0.5 || forwardDist > reach) continue;

                    // Perpendicular vector
                    Vec3d proj = forwardDir.multiply(forwardDist);
                    Vec3d perp = toBlock.subtract(proj);
                    double lateralDist = Math.sqrt(perp.x * perp.x + perp.z * perp.z);

                    if (lateralDist <= radius) {
                        BlockState state = world.getBlockState(mutablePos);
                        if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                        // Calculate push direction
                        Vec3d pushDir;
                        if (lateralDist < 0.1) {
                            // Coordinate hash split to avoid center lock
                            boolean goRight = ((x + z) & 1) == 0;
                            pushDir = new Vec3d(-forwardDir.z, 0, forwardDir.x).multiply(goRight ? 1 : -1);
                        } else {
                            pushDir = new Vec3d(perp.x, 0, perp.z).normalize();
                        }

                        double pushDist = radius - lateralDist + 1.2;
                        double phase = SpatialMath.hashCoordinateToPhase(mutablePos.asLong());
                        double lift = 0.45 + 0.35 * Math.sin(phase);

                        Vector3f targetDisp = new Vector3f(
                                (float) (pushDir.x * pushDist),
                                (float) lift,
                                (float) (pushDir.z * pushDist)
                        );

                        // Subtle tilt rotation
                        Quaternionf tilt = new Quaternionf().rotateY((float) Math.toRadians(10 * Math.sin(phase)))
                                                            .rotateZ((float) Math.toRadians(8 * Math.cos(phase)));

                        SpatialDisplacementManager.displaceBlock(world, mutablePos.toImmutable(), targetDisp, tilt, 20, 0x9B59B6);
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Connect player tick handler**
In `GodWandMod`, hook `ServerTickEvents.START_SERVER_TICK` to tick `SpatialPathWalker.tickPlayer` for all active players.
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**
```bash
git add src/main/java/net/godwand/skill/SpatialPathWalker.java src/main/java/net/godwand/GodWandMod.java
git commit -m "feat: implement Skill 1 SpatialPathWalker with global floor protection and forward reach"
```

---

### Task 6: Skill 2 Implementation - Arcane Singularity Orb & Calamity Slicing

**Files:**
- Create: `src/main/java/net/godwand/entity/ArcaneOrbEntity.java`
- Create: `src/main/java/net/godwand/skill/ShockwaveSlicer.java`
- Modify: `src/main/java/net/godwand/GodWandMod.java`

**Interfaces:**
- Produces:
  - `ArcaneOrbEntity`: projectile spawned on wand release with charge levels 1-3.
  - `ShockwaveSlicer.queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius)`: manages 5-tick concentric shell excavation and multi-strata display sampling.

- [ ] **Step 1: Implement ShockwaveSlicer (5-Tick Concentric Shell Slicing)**
`src/main/java/net/godwand/skill/ShockwaveSlicer.java`:
Slices the $R=16$ sphere into 5 shells ($0-3.2, 3.2-6.4, 6.4-9.6, 9.6-12.8, 12.8-16.0$), processing one shell per tick to prevent light provider tick freeze. Samples up to 450 `BlockDisplayEntity` instances across 3 strata ($0.4R$ scaled $1.35\times$, $0.7R$ scaled $1.15\times$, $1.0R$).

```java
package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShockwaveSlicer {
    public static class SlicingTask {
        public final ServerWorld world;
        public final BlockPos center;
        public final double maxRadius;
        public int currentShell = 0;
        public final int totalShells = 5;
        public int sampledDisplays = 0;
        public static final int MAX_DISPLAYS = 450;

        public SlicingTask(ServerWorld world, BlockPos center, double maxRadius) {
            this.world = world;
            this.center = center;
            this.maxRadius = maxRadius;
        }
    }

    private static final Queue<SlicingTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius) {
        ACTIVE_TASKS.add(new SlicingTask(world, center, maxRadius));
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.2f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.5f, 0.8f);
    }

    public static void tick(ServerWorld world) {
        Iterator<SlicingTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SlicingTask task = it.next();
            if (task.world != world) continue;

            double shellStep = task.maxRadius / task.totalShells;
            double rMin = task.currentShell * shellStep;
            double rMax = (task.currentShell + 1) * shellStep;
            double rMinSq = rMin * rMin;
            double rMaxSq = rMax * rMax;

            int intR = (int) Math.ceil(rMax);
            BlockPos.Mutable mutable = new BlockPos.Mutable();

            for (int x = -intR; x <= intR; x++) {
                for (int y = -intR; y <= intR; y++) {
                    for (int z = -intR; z <= intR; z++) {
                        double dSq = x * x + y * y + z * z;
                        if (dSq >= rMinSq && dSq < rMaxSq) {
                            mutable.set(task.center.getX() + x, task.center.getY() + y, task.center.getZ() + z);
                            BlockState state = world.getBlockState(mutable);
                            if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                            double dist = Math.sqrt(dSq);
                            Vec3d dir = dist < 0.1 ? new Vec3d(0, 1, 0) : new Vec3d(x / dist, y / dist, z / dist);
                            
                            // Multi-Strata Display Sampling
                            boolean spawnDisplay = false;
                            float scale = 1.0f;
                            double targetPush = (task.maxRadius - dist) + 2.0;

                            if (task.sampledDisplays < SlicingTask.MAX_DISPLAYS) {
                                // Sample 1 in every 8 blocks for display entity
                                if (((x ^ y ^ z) & 7) == 0) {
                                    spawnDisplay = true;
                                    task.sampledDisplays++;
                                    if (dist < task.maxRadius * 0.4) {
                                        scale = 1.35f;
                                        targetPush += 3.0;
                                    } else if (dist < task.maxRadius * 0.7) {
                                        scale = 1.15f;
                                    }
                                }
                            }

                            Vector3f targetDisp = new Vector3f(
                                    (float) (dir.x * targetPush),
                                    (float) (dir.y * targetPush + 0.8),
                                    (float) (dir.z * targetPush)
                            );

                            double phase = SpatialMath.hashCoordinateToPhase(mutable.asLong());
                            Quaternionf rot = new Quaternionf().rotateAxis((float) Math.toRadians(25), (float) dir.x, (float) dir.y, (float) dir.z);

                            if (spawnDisplay) {
                                SpatialDisplacementManager.displaceBlock(world, mutable.toImmutable(), targetDisp, rot, 100, 0x00F0FF);
                            } else {
                                // Interior blocks hollowed out to AIR without display entity
                                SpatialDisplacementManager.displaceBlock(world, mutable.toImmutable(), new Vector3f(0, 0, 0), new Quaternionf(), 100, 0);
                            }
                        }
                    }
                }
            }

            // Spawn shockwave ring particles
            world.spawnParticles(ParticleTypes.FLASH, task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                    5, rMax * 0.5, 0.5, rMax * 0.5, 0.0);

            task.currentShell++;
            if (task.currentShell >= task.totalShells) {
                it.remove();
            }
        }
    }
}
```

- [ ] **Step 2: Implement ArcaneOrbEntity Projectile**
`src/main/java/net/godwand/entity/ArcaneOrbEntity.java`:
Extends `ProjectileEntity`, renders swirling cyan & amethyst particle trails, detonates on block hit.
```java
package net.godwand.entity;

import net.godwand.skill.ShockwaveSlicer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class ArcaneOrbEntity extends ProjectileEntity {
    private int chargeLevel = 1;
    private int age = 0;

    public ArcaneOrbEntity(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = false;
    }

    public void setChargeLevel(int level) {
        this.chargeLevel = level;
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        this.age++;
        Vec3d velocity = this.getVelocity();
        this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        if (!this.getWorld().isClient()) {
            ServerWorld sw = (ServerWorld) this.getWorld();
            // Cyan & Amethyst dual particles
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.2f),
                    this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
            
            HitResult hit = this.getWorld().raycast(new net.minecraft.world.RaycastContext(
                    this.getPos(), this.getPos().add(velocity),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, this));

            if (hit.getType() != HitResult.Type.MISS) {
                onCollision(hit);
            }
            if (this.age > 200) {
                this.discard();
            }
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient()) {
            double radius = switch (chargeLevel) {
                case 1 -> 5.0;
                case 2 -> 10.0;
                default -> 16.0; // Calamity Level 3
            };
            ShockwaveSlicer.queueCalamityDetonation((ServerWorld) this.getWorld(), this.getBlockPos(), radius);
            this.discard();
        }
    }
}
```

- [ ] **Step 3: Register EntityType and ShockwaveSlicer tick**
Register `ArcaneOrbEntity` in `GodWandMod` with `FabricEntityTypeBuilder`, and hook `ShockwaveSlicer.tick` in server tick.
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add src/main/java/net/godwand/entity src/main/java/net/godwand/skill/ShockwaveSlicer.java src/main/java/net/godwand/GodWandMod.java
git commit -m "feat: implement Skill 2 ArcaneOrbEntity and ShockwaveSlicer with multi-strata calamity excavation"
```

---

### Task 7: God Wand Item, Controls & Audio-Visual Presentation

**Files:**
- Create: `src/main/java/net/godwand/item/GodWandItem.java`
- Create: `src/main/resources/assets/godwand/models/item/god_wand.json`
- Create: `src/main/resources/assets/godwand/textures/item/god_wand.png`
- Modify: `src/main/java/net/godwand/GodWandMod.java`

**Interfaces:**
- Produces:
  - `GodWandItem`: Handheld wand item.
  - Controls:
    - Shift + Right-Click: calls `SpatialPathWalker.toggle((ServerPlayerEntity) user)`.
    - Hold Right-Click: charges orb. Release shoots `ArcaneOrbEntity` with velocity proportional to charge time.

- [ ] **Step 1: Implement GodWandItem**
`src/main/java/net/godwand/item/GodWandItem.java`:
```java
package net.godwand.item;

import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.skill.SpatialPathWalker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GodWandItem extends Item {
    public GodWandItem(Settings settings) {
        super(settings.maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        
        if (user.isSneaking()) {
            if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                SpatialPathWalker.toggle(serverPlayer);
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5f, 1.6f);
            }
            return TypedActionResult.success(stack);
        } else {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient() && user instanceof PlayerEntity player) {
            int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
            int level = 1;
            if (chargeDuration >= 50) { // 2.5s+ -> Calamity Level 3
                level = 3;
            } else if (chargeDuration >= 24) { // 1.2s -> Level 2
                level = 2;
            }

            ArcaneOrbEntity orb = new ArcaneOrbEntity(net.godwand.GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
            orb.setOwner(player);
            orb.setChargeLevel(level);
            orb.setPosition(player.getEyePos().x, player.getEyePos().y - 0.1, player.getEyePos().z);

            Vec3d look = player.getRotationVec(1.0f);
            orb.setVelocity(look.x * 2.2, look.y * 2.2, look.z * 2.2);
            world.spawnEntity(orb);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.4f);
            return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: Add item assets and language file**
Create `assets/godwand/models/item/god_wand.json`:
```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "godwand:item/god_wand"
  }
}
```
Create a clean placeholder pixel-art 16x16 PNG for `god_wand.png`.
Update `assets/godwand/lang/en_us.json`:
```json
{
  "item.godwand.god_wand": "God Wand",
  "item.godwand.god_wand.desc": "§7Shift+Right Click: Toggle Spatial Path\n§7Hold Right Click: Charge Arcane Singularity"
}
```

- [ ] **Step 3: Register item in ItemGroup**
Register `GodWandItem` in `ItemGroups.COMBAT` or custom `GOD_WAND_GROUP`.
Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add src/main/java/net/godwand/item src/main/resources/assets
git commit -m "feat: implement GodWandItem with dual skills and asset bindings"
```

---

### Task 8: End-to-End Build, Resilience & Package Verification

**Files:**
- Create: `src/main/java/net/godwand/spatial/DisplacementPersistentState.java`
- Modify: `src/main/java/net/godwand/GodWandMod.java`

**Interfaces:**
- Produces: Complete working mod JAR file in `build/libs/`.

- [ ] **Step 1: Implement DisplacementPersistentState for Crash Recovery**
Persist active displacements in ServerWorld so if JVM halts or restarts, blocks can be checked and restored upon world load.
- [ ] **Step 2: Run full build and test suite**
Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, producing `build/libs/godwand-1.0.0.jar`.
- [ ] **Step 3: Commit**
```bash
git add .
git commit -m "feat: complete God Wand mod implementation and verify build package"
```
