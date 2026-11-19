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
    // Owner, 2026-11-19: small pieces, whirled round on their orbit and swallowed quickly.
    static final int FLIGHT_TICKS = 20;
    static final float START_SCALE = 1f;

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
        float o = (1 - START_SCALE) / 2;
        display.setTransformation(new Transformation(new Vector3f(o, o, o), null, new Vector3f(START_SCALE), null));
        level.addFreshEntity(display);
        SpatialDisplacementManager.trackEntity(display.getUUID(), display);
        return display;
    };

    private static final Map<Integer, int[][]> SPHERES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Cell offsets within radius, nearest to the cell centre first. Built once per radius with a counting sort on the squared distance (a
     *  radius-48 crater is ~460k cells; sorting them with a comparator on the blast tick stalled the server for half a second). */
    static int[][] sphere(int radius) { return SPHERES.computeIfAbsent(radius, BlueEater::buildSphere); }

    /** Builds a radius's cell list on a worker thread ahead of need (Purple's crater, while its cutscene plays). */
    static void prepare(int radius) { if (radius > 0 && !SPHERES.containsKey(radius)) java.util.concurrent.CompletableFuture.runAsync(() -> sphere(radius)); }

    private static int[][] buildSphere(int r) {
        int max = r * r;
        int[] start = new int[max + 2];
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            int d = x * x + y * y + z * z;
            if (d <= max) start[d + 1]++;
        }
        for (int d = 1; d < start.length; d++) start[d] += start[d - 1];
        int[][] cells = new int[start[max + 1]][];
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            int d = x * x + y * y + z * z;
            if (d <= max) cells[start[d]++] = new int[]{x, y, z};
        }
        return cells;
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
        return carveFrom(level, center, cells, from, budget, deadline, enabled, Integer.MAX_VALUE);
    }

    /** As above, clearing at most {@code cap} blocks this tick: the light and chunk updates that follow cost the server far more than the
     *  inspection, so a big crater is spread over more ticks instead of stalling one. */
    static int carveFrom(ServerLevel level, Vec3 center, int[][] cells, int from, int budget, long deadline, boolean enabled, int cap) {
        if (!enabled) return from;
        int cleared = 0;
        BlockPos core = BlockPos.containing(center);
        var pos = new BlockPos.MutableBlockPos();
        int i = from, inspected = 0;
        while (i < cells.length && inspected < budget) {
            if ((inspected & 31) == 0 && System.nanoTime() >= deadline) break;
            int[] c = cells[i++];
            inspected++;
            if (clear(level, pos.set(core.getX() + c[0], core.getY() + c[1], core.getZ() + c[2])) != null && ++cleared >= cap) break;
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

    /** One tick of flight: each display gets a new keyframe every tick (interpolated by the client). Owner, 2026-11-19: from its own size,
     *  shrinking, along half an arc straight into Blue; each block bends its own way, so the swarm reads smooth, not chaotic. */
    static void animate(List<Eaten> live, Vec3 center) {
        for (Iterator<Eaten> it = live.iterator(); it.hasNext(); ) {
            Eaten e = it.next();
            int age = ++e.age()[0];
            double p = Math.min(1, (age + 1) / (double) FLIGHT_TICKS), k = Math.pow(p, 1.4);
            Vec3 v0 = e.from().add(.5, .5, .5).subtract(center);
            long h = (long) Math.floor(e.from().x * 31 + e.from().y * 13 + e.from().z * 17);
            // The arc's axis: perpendicular to the way in, turned by the block's own hash.
            Vec3 side = new Vec3(Math.sin(h * 1.7), Math.cos(h * 2.3), Math.sin(h * 3.1 + 1));
            Vec3 n = v0.cross(side);
            n = n.lengthSqr() < 1e-6 ? v0.cross(new Vec3(0, 1, 0.3)) : n;
            n = n.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : n.normalize();
            double ang = Math.PI * k, cos = Math.cos(ang), sin = Math.sin(ang);
            // Rodrigues: v0 turned by ang about n (n is perpendicular to v0), drawn in as it turns.
            Vec3 v = v0.scale(cos).add(n.cross(v0).scale(sin)).scale(1 - k);
            float scale = (float) (START_SCALE * (1 - .92 * k));
            Vec3 to = center.add(v).subtract(e.from()).subtract(scale / 2.0, scale / 2.0, scale / 2.0);
            e.display().setTransformationInterpolationDelay(0);
            e.display().setTransformationInterpolationDuration(1);
            e.display().setTransformation(new Transformation(new Vector3f((float) to.x, (float) to.y, (float) to.z),
                    new Quaternionf().rotateXYZ((float) (k * 1.6), (float) (k * 2.4), 0), new Vector3f(scale), null));
            if (age >= FLIGHT_TICKS) { retire(e); it.remove(); }
        }
    }

    /** Tears one block loose as a floating chunk (Purple's charged ground), or null if it may not go. */
    static Eaten lift(ServerLevel level, BlockPos pos) {
        BlockState state = clear(level, new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ()));
        if (state == null || !state.getFluidState().isEmpty()) return null;
        return new Eaten(shower.show(level, pos, state), Vec3.atLowerCornerOf(pos));
    }

    /** One tick for the floating chunks: rising to their own height, trembling (charged) and turning slowly about their centre. */
    static void hover(List<Eaten> lifted) {
        for (Eaten e : lifted) {
            int age = ++e.age()[0];
            long h = BlueRedRules.liftHash(e.from().x, e.from().y, e.from().z);
            double up = BlueRedRules.liftRise(age, h);
            float spin = (float) (age * .03 * ((h & 1) == 0 ? 1 : -1));
            Quaternionf q = new Quaternionf().rotateXYZ(spin * .7f, spin, spin * .4f);
            Vector3f pivot = q.transform(new Vector3f(.5f, .5f, .5f));
            Vector3f t = new Vector3f(.5f - pivot.x + (float) (.06 * Math.sin(age * 2.9 + h)), .5f - pivot.y + (float) up, .5f - pivot.z + (float) (.06 * Math.cos(age * 3.7 + h)));
            e.display().setTransformationInterpolationDelay(0);
            e.display().setTransformationInterpolationDuration(1);
            e.display().setTransformation(new Transformation(t, q, null, null));
        }
    }

    private static final BlockState[] STAIN = {Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.PURPLE_CONCRETE.defaultBlockState(),
            Blocks.PURPLE_TERRACOTTA.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
            Blocks.PURPLE_TERRACOTTA.defaultBlockState(), Blocks.PURPLE_CONCRETE.defaultBlockState()};
    private static final Map<Integer, int[][]> DISCS = new java.util.concurrent.ConcurrentHashMap<>();
    static final int STAIN_PER_TICK = 600, PURPLE_CLEAR_PER_TICK = 2500;

    /** Column offsets within radius, nearest first. */
    static int[][] disc(int radius) {
        return DISCS.computeIfAbsent(radius, r -> {
            List<int[]> cells = new ArrayList<>();
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (x * x + z * z <= r * r) cells.add(new int[]{x, z});
            cells.sort(Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
            return cells.toArray(new int[0][]);
        });
    }

    /** Purple's after-image on the land (owner, 2026-11-19): the top block of each column turned violet, all over the crater and fading out
     *  over half a radius past its rim. Works through columns from {@code from}; returns where to resume. */
    static int stainFrom(ServerLevel level, Vec3 center, int radius, int from, int budget, long deadline, boolean enabled) {
        if (!enabled || radius <= 0) return Integer.MAX_VALUE;
        int[][] cols = disc(radius * 3 / 2);
        var pos = new BlockPos.MutableBlockPos();
        int i = from, done = 0, written = 0;
        while (i < cols.length && done < budget && written < STAIN_PER_TICK) {
            if ((done & 31) == 0 && System.nanoTime() >= deadline) break;
            int[] c = cols[i++];
            done++;
            int x = (int) Math.floor(center.x) + c[0], z = (int) Math.floor(center.z) + c[1];
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            double r = Math.sqrt(c[0] * c[0] + c[1] * c[1]), keep = r < radius ? .9 : .9 * (1 - (r - radius) / (radius * .5));
            long h = (x * 73856093L) ^ (z * 19349663L);
            if (((h >>> 8) & 1023) / 1024.0 >= keep) continue;
            pos.set(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || !SpatialDisplacementManager.canDisplace(level, pos, state)) continue;
            level.setBlock(pos.immutable(), STAIN[(int) Math.floorMod(h >>> 20, STAIN.length)], Block.UPDATE_CLIENTS);
            written++;
        }
        return i >= cols.length ? Integer.MAX_VALUE : i;
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
