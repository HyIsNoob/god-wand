package net.godwand.skill;

import net.godwand.GodWandMod;
import net.godwand.item.GodWandItem;
import net.godwand.spatial.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

public final class SpatialBridge {
    private static final Set<UUID> ENABLED = new HashSet<>(), PRESSED = new HashSet<>(), ELIGIBLE = new HashSet<>();
    private record Flight(ServerLevel world, net.minecraft.world.entity.Display.BlockDisplay display, BlockPos target, long born) {}
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

    public static List<BlockPos> plan(Vec3 feet, Vec3 direction, int y) {
        int dx = Math.abs(direction.x) > Math.abs(direction.z) ? (direction.x >= 0 ? 1 : -1) : 0;
        int dz = dx == 0 ? (direction.z >= 0 ? 1 : -1) : 0;
        List<BlockPos> result = new ArrayList<>();
        BlockPos origin = BlockPos.containing(feet.x, y, feet.z);
        for (int forward = 0; forward <= 3; forward++) {
            for (int side : new int[]{0, -1, 1}) result.add(origin.offset(dx * forward - dz * side, 0, dz * forward + dx * side));
        }
        return result;
    }

    private static boolean selected(ServerPlayer player) {
        for (var stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_SPATIAL_PATH) return true;
        }
        return false;
    }

    public static void toggle(ServerPlayer player) {
        UUID owner = player.getUUID();
        if (ENABLED.remove(owner)) {
            player.displayClientMessage(Component.literal("§7[God Wand] Bridge: OFF"), true);
        } else {
            ENABLED.add(owner);
            LEVEL.put(owner, Mth.floor(player.getY() - 0.01));
            if (!SpatialPathWalker.isActive(player)) {
                SpatialPathWalker.toggle(player);
            }
            player.displayClientMessage(Component.literal("§b[God Wand] Bridge: ON"), true);
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        UUID owner = player.getUUID();
        if (!player.isAlive() || !SpatialPathWalker.isActive(player)) {
            disable(owner);
            return;
        }
        if (player.isShiftKeyDown()) {
            if (PRESSED.add(owner) && selected(player)) ELIGIBLE.add(owner);
            if (!selected(player)) ELIGIBLE.remove(owner);
        } else if (PRESSED.remove(owner) && ELIGIBLE.remove(owner)) {
            toggle(player);
        }
        if (!ENABLED.contains(owner) || player.getAbilities().flying) return;
        int y = LEVEL.getOrDefault(owner, Mth.floor(player.getY() - 0.01));
        if (player.onGround()) y = Mth.floor(player.getY() - 0.01);
        else if (player.getDeltaMovement().y > 0) y = raisedLevel(y, player.getY());
        LEVEL.put(owner, y);
        Vec3 velocity = player.getDeltaMovement();
        Vec3 direction = new Vec3(velocity.x, 0, velocity.z);
        if (direction.lengthSqr() < 0.0025) direction = player.getViewVector(1).multiply(1, 0, 1);
        List<BlockPos> wanted = plan(player.position(), direction, y);
        ServerLevel world = player.serverLevel();
        List<DisplacedBlockRecord> records = SpatialDisplacementManager.getActiveRecordsForWorld(world.dimension());
        int built = 0;
        for (BlockPos target : wanted) {
            if (built >= 3) break;
            if (!world.isInWorldBounds(target) || !world.getWorldBorder().isWithinBounds(target)
                    || !world.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                    || !world.getBlockState(target).isAir()
                    || SpatialDisplacementManager.isPositionDisplaced(world.dimension(), target)
                    || world.players().stream().anyMatch(p -> !p.isSpectator() && p.getBoundingBox().intersects(new AABB(target)))) continue;
            DisplacedBlockRecord source = null;
            // Borrow stones already floating in this player's aura first.
            for (var record : records) {
                if (owner.equals(record.boundPlayerUuid) && record.bridgeDestination == null && !record.permanentDestruction
                        && record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER
                        && usable(world, record.pos, record.originalState) && !wanted.contains(record.pos)
                        && !occupied(world, record.pos)) { source = record; break; }
            }
            if (source == null) {
                BlockPos origin = player.blockPosition();
                for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-5, -1, -5), origin.offset(5, 4, 5))) {
                    if (!world.getWorldBorder().isWithinBounds(candidate)
                            || !world.getChunkSource().hasChunk(candidate.getX() >> 4, candidate.getZ() >> 4)
                            || wanted.contains(candidate) || occupied(world, candidate)) continue;
                    BlockState state = world.getBlockState(candidate);
                    if (!usable(world, candidate, state) || !SpatialDisplacementManager.canDisplace(world, candidate, state)) continue;
                    SpatialDisplacementManager.displaceBlock(world, candidate, new Vector3f(), new Quaternionf(), new Vector3f(1),
                            1200, 0, false, DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, owner, null, 0);
                    source = SpatialDisplacementManager.getActiveRecord(world.dimension(), candidate);
                    if (source != null) { records.add(source); break; }
                }
            }
            // With no nearby material left, roll the unoccupied rear of the bridge forward.
            if (source == null) {
                for (var record : records) {
                    if (owner.equals(record.boundPlayerUuid) && record.bridgeDestination != null
                            && !wanted.contains(record.bridgeDestination) && !occupied(world, record.bridgeDestination)
                            && world.getChunkSource().hasChunk(record.bridgeDestination.getX() >> 4, record.bridgeDestination.getZ() >> 4)
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
            if (!world.setBlock(target, source.originalState, Block.UPDATE_ALL)) {
                SpatialDisplacementManager.moveBridge(source, null);
                SpatialDisplacementManager.executeTwoPassRestoration(world, List.of(source));
                continue;
            }
            var visual = new net.minecraft.world.entity.Display.BlockDisplay(net.minecraft.world.entity.EntityType.BLOCK_DISPLAY, world);
            visual.addTag("godwand_displaced");
            visual.setPos(Vec3.atLowerCornerOf(source.pos));
            visual.setBlockState(source.originalState);
            world.addFreshEntity(visual);
            FLIGHTS.add(new Flight(world, visual, target, world.getGameTime()));
            DisplacementPersistentState.getServerState(world).setDirty();
            built++;
        }
    }

    private static boolean usable(ServerLevel world, BlockPos pos, BlockState state) {
        return state.isCollisionShapeFullBlock(world, pos) && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !(state.getBlock() instanceof FallingBlock) && state.getDestroySpeed(world, pos) >= 0;
    }

    public static boolean occupied(ServerLevel world, BlockPos pos) {
        AABB safety = new AABB(pos).inflate(0.35, 2.1, 0.35);
        return world.players().stream().anyMatch(p -> !p.isSpectator() && p.getBoundingBox().intersects(safety));
    }

    public static void clearVisuals(ServerLevel world) {
        FLIGHTS.removeIf(f -> { if (f.world() != world) return false; f.display().discard(); return true; });
    }

    public static void tickWorld(ServerLevel world) {
        FLIGHTS.removeIf(f -> {
            if (f.world() != world) return false;
            long age = world.getGameTime() - f.born();
            if (age == 2) {
                f.display().setTransformationInterpolationDelay(0);
                f.display().setTransformationInterpolationDuration(4);
                f.display().setTransformation(SpatialMath.createPivotTransform(Vec3.atLowerCornerOf(f.target()).subtract(f.display().position()), new Quaternionf(), 0.95f));
            }
            if (age < 8) return false;
            f.display().discard();
            return true;
        });
        for (var record : SpatialDisplacementManager.getActiveRecordsForWorld(world.dimension())) {
            BlockPos target = record.bridgeDestination;
            if (target == null || !world.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)) continue;
            if (!world.getBlockState(target).equals(record.originalState)) {
                // A mined/destroyed bridge block consumes its borrowed original instead of duplicating drops.
                SpatialDisplacementManager.removeActiveRecord(world.dimension(), record.pos);
                DisplacementPersistentState.getServerState(world).setDirty();
                continue;
            }
            var player = record.boundPlayerUuid == null ? null : world.getServer().getPlayerList().getPlayer(record.boundPlayerUuid);
            if (occupied(world, target)) continue;
            if (player != null && player.isAlive() && player.serverLevel() == world && SpatialPathWalker.isActive(player) && ENABLED.contains(player.getUUID())
                    && target.getCenter().distanceToSqr(player.position()) < 100) continue;
            if (!world.getChunkSource().hasChunk(record.pos.getX() >> 4, record.pos.getZ() >> 4)) continue;
            world.removeBlock(target, false);
            SpatialDisplacementManager.moveBridge(record, null);
            SpatialDisplacementManager.executeTwoPassRestoration(world, List.of(record));
        }
    }
}
