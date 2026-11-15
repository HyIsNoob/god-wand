package net.godwand.skill;

import com.mojang.math.Transformation;
import net.godwand.config.GodWandConfig;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Blue swallows real terrain: nearest blocks first, a few per tick, each shown flying into the core as a block display. */
public final class BlueEater {
    /** Blocks removed per Blue per tick, so the hole grows visibly instead of appearing at once. */
    static final int EAT_PER_TICK = 12;
    /** Live flying displays per Blue; further blocks vanish without one. */
    static final int MAX_DISPLAYS = 48;
    static final int FLIGHT_TICKS = 12;

    record Eaten(Display.BlockDisplay display, Vec3 from, int[] age) {
        Eaten(Display.BlockDisplay display, Vec3 from) { this(display, from, new int[1]); }
    }

    interface Shower { Display.BlockDisplay show(ServerLevel level, BlockPos pos, BlockState state); }
    /** Test seam: spawns a tracked display that the displacement lifecycle discards if its chunk reloads later. */
    static Shower shower = (level, pos, state) -> {
        var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.addTag("godwand_managed_displacement");
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        display.setBlockState(state);
        display.setTransformation(Transformation.identity());
        level.addFreshEntity(display);
        SpatialDisplacementManager.trackEntity(display.getUUID(), display);
        return display;
    };

    private static final Map<Integer, int[][]> SPHERES = new HashMap<>();

    private BlueEater() {}

    /** Cell offsets within radius, nearest to the cell centre first. */
    static int[][] sphere(int radius) {
        return SPHERES.computeIfAbsent(radius, r -> {
            List<int[]> cells = new ArrayList<>();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++)
                if (x * x + y * y + z * z <= r * r) cells.add(new int[]{x, y, z});
            cells.sort(Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1] + c[2] * c[2]));
            return cells.toArray(new int[0][]);
        });
    }

    /** Eats up to EAT_PER_TICK blocks around center; returns the inspections used from the shared budget. */
    static int eat(ServerLevel level, Vec3 center, int radius, int budget, long deadline, List<Eaten> live) {
        if (!GodWandConfig.SERVER.blueTerrainDamage || radius <= 0 || budget <= 0) return 0;
        BlockPos core = BlockPos.containing(center);
        var pos = new BlockPos.MutableBlockPos();
        int inspected = 0, eaten = 0;
        for (int[] c : sphere(radius)) {
            if (inspected >= budget || eaten >= EAT_PER_TICK) break;
            if ((inspected & 31) == 0 && System.nanoTime() >= deadline) break;
            inspected++;
            pos.set(core.getX() + c[0], core.getY() + c[1], core.getZ() + c[2]);
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir() || !SpatialDisplacementManager.canDisplace(level, pos, state)) continue;
            BlockPos at = pos.immutable();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            eaten++;
            if (live.size() < MAX_DISPLAYS && state.getFluidState().isEmpty())
                live.add(new Eaten(shower.show(level, at, state), Vec3.atLowerCornerOf(at)));
        }
        return inspected;
    }

    /** One tick of flight: on the first tick each display is sent spinning and shrinking into the core, then retired. */
    static void animate(List<Eaten> live, Vec3 center) {
        for (Iterator<Eaten> it = live.iterator(); it.hasNext(); ) {
            Eaten e = it.next();
            int age = ++e.age()[0];
            if (age == 1) {
                Vec3 to = center.subtract(e.from()).subtract(0.1, 0.1, 0.1);
                e.display().setTransformationInterpolationDelay(0);
                e.display().setTransformationInterpolationDuration(FLIGHT_TICKS - 1);
                e.display().setTransformation(new Transformation(new Vector3f((float) to.x, (float) to.y, (float) to.z),
                        new Quaternionf().rotateXYZ(2.1f, 3.4f, 1.3f), new Vector3f(0.2f), null));
            }
            if (age >= FLIGHT_TICKS) { retire(e); it.remove(); }
        }
    }

    static void retire(Eaten e) {
        SpatialDisplacementManager.untrackEntity(e.display().getUUID());
        e.display().discard();
    }

    static void retireAll(List<Eaten> live) {
        for (Eaten e : live) retire(e);
        live.clear();
    }
}
