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
    static final int MAX_DISPLAYS = 96;
    static final int FLIGHT_TICKS = 32;

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
        return carve(level, center, radius, budget, deadline, EAT_PER_TICK, live, GodWandConfig.SERVER.blueTerrainDamage);
    }

    /** Clears up to cap blocks of a sphere, nearest first; live (or null for none) collects flying displays. */
    static int carve(ServerLevel level, Vec3 center, int radius, int budget, long deadline, int cap, List<Eaten> live, boolean enabled) {
        if (!enabled || radius <= 0 || budget <= 0) return 0;
        BlockPos core = BlockPos.containing(center);
        var pos = new BlockPos.MutableBlockPos();
        int inspected = 0, eaten = 0;
        for (int[] c : sphere(radius)) {
            if (inspected >= budget || eaten >= cap) break;
            if ((inspected & 31) == 0 && System.nanoTime() >= deadline) break;
            inspected++;
            pos.set(core.getX() + c[0], core.getY() + c[1], core.getZ() + c[2]);
            BlockState state = clear(level, pos);
            if (state == null) continue;
            eaten++;
            if (live != null && live.size() < MAX_DISPLAYS && state.getFluidState().isEmpty())
                live.add(new Eaten(shower.show(level, pos.immutable(), state), Vec3.atLowerCornerOf(pos)));
        }
        return inspected;
    }

    /** Clears cells[from..] around center (no cap, no displays) until the budget or deadline runs out; returns where to resume. */
    static int carveFrom(ServerLevel level, Vec3 center, int[][] cells, int from, int budget, long deadline, boolean enabled) {
        if (!enabled) return from;
        BlockPos core = BlockPos.containing(center);
        var pos = new BlockPos.MutableBlockPos();
        int i = from, inspected = 0;
        while (i < cells.length && inspected < budget) {
            if ((inspected & 31) == 0 && System.nanoTime() >= deadline) break;
            int[] c = cells[i++];
            inspected++;
            clear(level, pos.set(core.getX() + c[0], core.getY() + c[1], core.getZ() + c[2]));
        }
        return i;
    }

    /** Removes one block if it may go (loaded chunk, in height, not air, displaceable); returns what was there, else null. */
    private static BlockState clear(ServerLevel level, BlockPos.MutableBlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        BlockState state = chunk.getBlockState(pos);
        if (state.isAir() || !SpatialDisplacementManager.canDisplace(level, pos, state)) return null;
        level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        return state;
    }

    /** One tick of flight. Every 2 ticks each display gets a new keyframe (interpolated by the client). Owner's ref 41 (2026-11-18): torn up
     *  and pulled out to a ring round Blue, then whirled round it on a tilted orbit, faster and faster, the ring tightening, tumbling and
     *  shrinking until it is swallowed. */
    static void animate(List<Eaten> live, Vec3 center) {
        for (Iterator<Eaten> it = live.iterator(); it.hasNext(); ) {
            Eaten e = it.next();
            int age = ++e.age()[0];
            if (age % 2 == 1) {
                double p = Math.min(1, (age + 1) / (double) FLIGHT_TICKS), ease = p * p;
                Vec3 mid = e.from().add(.5, .5, .5), v0 = mid.subtract(center);
                long h = (long) Math.floor(e.from().x * 31 + e.from().y * 13 + e.from().z * 17);
                // Each block on its own orbit plane, any orientation (owner, 2026-11-18: all directions, not a flat disc), at its own radius.
                Vec3 n = new Vec3(Math.sin(h * 1.7), Math.cos(h * 2.3), Math.sin(h * 3.1 + 1)).normalize();
                Vec3 u = n.cross(Math.abs(n.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), w = n.cross(u);
                double sign = (h & 1) == 0 ? 1 : -1, ring = 2.4 + ((h >> 3) & 7) * .3;
                double join = Math.min(1, p / .25), j = join * join * (3 - 2 * join);
                double rad = ring * (1 - .9 * Math.pow(Math.max(0, (p - .5) / .5), 2));
                double a0 = Math.atan2(v0.dot(w), v0.dot(u)), ang = a0 + sign * Math.PI * 3.5 * Math.pow(p, 1.2);
                Vec3 onRing = u.scale(Math.cos(ang) * rad).add(w.scale(Math.sin(ang) * rad));
                Vec3 v = v0.add(onRing.subtract(v0).scale(j));
                float scale = (float) (1 - .75 * ease);
                Vec3 to = center.add(v).subtract(e.from()).subtract(scale / 2.0, scale / 2.0, scale / 2.0);
                e.display().setTransformationInterpolationDelay(0);
                e.display().setTransformationInterpolationDuration(2);
                e.display().setTransformation(new Transformation(new Vector3f((float) to.x, (float) to.y, (float) to.z),
                        new Quaternionf().rotateXYZ((float) (ease * 2.1), (float) (ease * 3.4), (float) (ease * 1.3)), new Vector3f(scale), null));
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
