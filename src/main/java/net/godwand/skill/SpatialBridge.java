package net.godwand.skill;

import net.godwand.GodWandMod;
import net.godwand.item.GodWandItem;
import net.godwand.spatial.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

public final class SpatialBridge {
    private static final Set<UUID> ENABLED = new HashSet<>(), PRESSED = new HashSet<>(), ELIGIBLE = new HashSet<>();
    private record Flight(ServerWorld world, net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity display, BlockPos target, long born) {}
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final Map<UUID, Integer> LEVEL = new HashMap<>();

    public static void resetRuntime() {
        ENABLED.clear(); PRESSED.clear(); ELIGIBLE.clear(); LEVEL.clear(); FLIGHTS.clear();
    }

    public static void disable(UUID owner) {
        ENABLED.remove(owner); PRESSED.remove(owner); ELIGIBLE.remove(owner); LEVEL.remove(owner);
    }

    public static int raisedLevel(int current, double feetY) {
        return feetY >= current + 1.9 ? current + 1 : current;
    }

    public static List<BlockPos> plan(Vec3d feet, Vec3d direction, int y) {
        int dx = Math.abs(direction.x) > Math.abs(direction.z) ? (direction.x >= 0 ? 1 : -1) : 0;
        int dz = dx == 0 ? (direction.z >= 0 ? 1 : -1) : 0;
        List<BlockPos> result = new ArrayList<>();
        BlockPos origin = BlockPos.ofFloored(feet.x, y, feet.z);
        for (int forward = 0; forward <= 3; forward++) {
            for (int side : new int[]{0, -1, 1}) result.add(origin.add(dx * forward - dz * side, 0, dz * forward + dx * side));
        }
        return result;
    }

    private static boolean selected(ServerPlayerEntity player) {
        for (var stack : List.of(player.getMainHandStack(), player.getOffHandStack())) {
            if (stack.isOf(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_SPATIAL_PATH) return true;
        }
        return false;
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        UUID owner = player.getUuid();
        if (player.isSneaking()) {
            if (PRESSED.add(owner) && selected(player)) ELIGIBLE.add(owner);
            if (!selected(player)) ELIGIBLE.remove(owner);
        } else if (PRESSED.remove(owner) && ELIGIBLE.remove(owner)) {
            if (ENABLED.remove(owner)) {
                player.sendMessage(Text.literal("Bridge off"), true);
            } else {
                ENABLED.add(owner);
                LEVEL.put(owner, MathHelper.floor(player.getY() - 0.01));
                player.sendMessage(Text.literal("Bridge on"), true);
            }
        }
        if (!ENABLED.contains(owner) || player.getAbilities().flying) return;
        int y = LEVEL.getOrDefault(owner, MathHelper.floor(player.getY() - 0.01));
        if (player.isOnGround()) y = MathHelper.floor(player.getY() - 0.01);
        else if (player.getVelocity().y > 0) y = raisedLevel(y, player.getY());
        LEVEL.put(owner, y);
        Vec3d velocity = player.getVelocity();
        Vec3d direction = new Vec3d(velocity.x, 0, velocity.z);
        if (direction.lengthSquared() < 0.0025) direction = player.getRotationVec(1).multiply(1, 0, 1);
        List<BlockPos> wanted = plan(player.getPos(), direction, y);
        ServerWorld world = player.getServerWorld();
        List<DisplacedBlockRecord> records = SpatialDisplacementManager.getActiveRecordsForWorld(world.getRegistryKey());
        int built = 0;
        for (BlockPos target : wanted) {
            if (built >= 3) break;
            if (!world.isInBuildLimit(target) || !world.getWorldBorder().contains(target)
                    || !world.getChunkManager().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)
                    || !world.getBlockState(target).isAir()
                    || SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), target)
                    || world.getPlayers().stream().anyMatch(p -> !p.isSpectator() && p.getBoundingBox().intersects(new Box(target)))) continue;
            DisplacedBlockRecord source = null;
            // Borrow stones already floating in this player's aura first.
            for (var record : records) {
                if (owner.equals(record.boundPlayerUuid) && record.bridgeDestination == null && !record.permanentDestruction
                        && record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER
                        && usable(world, record.pos, record.originalState) && !wanted.contains(record.pos)
                        && !occupied(world, record.pos)) { source = record; break; }
            }
            if (source == null) {
                BlockPos origin = player.getBlockPos();
                for (BlockPos candidate : BlockPos.iterate(origin.add(-5, -1, -5), origin.add(5, 4, 5))) {
                    if (!world.getWorldBorder().contains(candidate)
                            || !world.getChunkManager().isChunkLoaded(candidate.getX() >> 4, candidate.getZ() >> 4)
                            || wanted.contains(candidate) || occupied(world, candidate)) continue;
                    BlockState state = world.getBlockState(candidate);
                    if (!usable(world, candidate, state) || !SpatialDisplacementManager.canDisplace(world, candidate, state)) continue;
                    SpatialDisplacementManager.displaceBlock(world, candidate, new Vector3f(), new Quaternionf(), new Vector3f(1),
                            1200, 0, false, DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, owner, null, 0);
                    source = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), candidate);
                    if (source != null) { records.add(source); break; }
                }
            }
            // With no nearby material left, roll the unoccupied rear of the bridge forward.
            if (source == null) {
                for (var record : records) {
                    if (owner.equals(record.boundPlayerUuid) && record.bridgeDestination != null
                            && !wanted.contains(record.bridgeDestination) && !occupied(world, record.bridgeDestination)
                            && world.getChunkManager().isChunkLoaded(record.bridgeDestination.getX() >> 4, record.bridgeDestination.getZ() >> 4)
                            && world.getBlockState(record.bridgeDestination).equals(record.originalState)) {
                        world.removeBlock(record.bridgeDestination, false);
                        source = record; break;
                    }
                }
            }
            if (source == null) break;
            SpatialDisplacementManager.moveBridge(source, target);
            source.restoreTick = 0;
            if (source.displayEntityUuid != null) {
                var display = SpatialDisplacementManager.getActiveEntity(source.displayEntityUuid);
                if (display != null) { display.discard(); SpatialDisplacementManager.untrackEntity(source.displayEntityUuid); }
                source.displayEntityUuid = null;
            }
            if (!world.setBlockState(target, source.originalState, Block.NOTIFY_ALL)) {
                SpatialDisplacementManager.moveBridge(source, null);
                SpatialDisplacementManager.executeTwoPassRestoration(world, List.of(source));
                continue;
            }
            var visual = new net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity(net.minecraft.entity.EntityType.BLOCK_DISPLAY, world);
            visual.addCommandTag("godwand_displaced");
            visual.setPosition(Vec3d.of(source.pos));
            visual.setBlockState(source.originalState);
            world.spawnEntity(visual);
            FLIGHTS.add(new Flight(world, visual, target, world.getTime()));
            DisplacementPersistentState.getServerState(world).markDirty();
            built++;
        }
    }

    private static boolean usable(ServerWorld world, BlockPos pos, BlockState state) {
        return state.isFullCube(world, pos) && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !(state.getBlock() instanceof FallingBlock) && state.getHardness(world, pos) >= 0;
    }

    public static boolean occupied(ServerWorld world, BlockPos pos) {
        Box safety = new Box(pos).expand(0.35, 2.1, 0.35);
        return world.getPlayers().stream().anyMatch(p -> !p.isSpectator() && p.getBoundingBox().intersects(safety));
    }

    public static void clearVisuals(ServerWorld world) {
        FLIGHTS.removeIf(f -> { if (f.world() != world) return false; f.display().discard(); return true; });
    }

    public static void tickWorld(ServerWorld world) {
        FLIGHTS.removeIf(f -> {
            if (f.world() != world) return false;
            long age = world.getTime() - f.born();
            if (age == 2) {
                f.display().setStartInterpolation(0);
                f.display().setInterpolationDuration(4);
                f.display().setTransformation(SpatialMath.createPivotTransform(Vec3d.of(f.target()).subtract(f.display().getPos()), new Quaternionf(), 0.95f));
            }
            if (age < 8) return false;
            f.display().discard();
            return true;
        });
        for (var record : SpatialDisplacementManager.getActiveRecordsForWorld(world.getRegistryKey())) {
            BlockPos target = record.bridgeDestination;
            if (target == null || !world.getChunkManager().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) continue;
            if (!world.getBlockState(target).equals(record.originalState)) {
                // A mined/destroyed bridge block consumes its borrowed original instead of duplicating drops.
                SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), record.pos);
                DisplacementPersistentState.getServerState(world).markDirty();
                continue;
            }
            var player = record.boundPlayerUuid == null ? null : world.getServer().getPlayerManager().getPlayer(record.boundPlayerUuid);
            // Keep offline players' footing across restart; reclaim it once they return and move away.
            if (player == null || occupied(world, target)) continue;
            if (player.getServerWorld() == world && SpatialPathWalker.isActive(player) && ENABLED.contains(player.getUuid())
                    && target.toCenterPos().squaredDistanceTo(player.getPos()) < 100) continue;
            if (!world.getBlockState(record.pos).isAir()) continue;
            world.removeBlock(target, false);
            SpatialDisplacementManager.moveBridge(record, null);
            SpatialDisplacementManager.executeTwoPassRestoration(world, List.of(record));
        }
    }
}
