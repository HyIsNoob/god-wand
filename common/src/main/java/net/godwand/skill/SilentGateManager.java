package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.SilentGatePayload;
import net.godwand.platform.Services;
import net.godwand.skill.SilentGateSequence.Phase;
import net.godwand.spatial.DisplacementPersistentState;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Server owns the Silent Gate lifecycle (BUILD, WAIT, BARRAGE, END), targeting, beam damage and craters. */
public final class SilentGateManager {
    interface Sender { void send(ServerLevel level, Vec3 pos, CustomPacketPayload payload); }
    /** Test seam: the real sender fans out to players within 256 blocks of the gate anchor. */
    static Sender sender = (level, pos, payload) -> Services.NETWORK.sendToNearby(level, pos, 256, payload);

    private static final int CRATER_BLOCKS_PER_TICK = 96;
    private static final Map<UUID, Task> TASKS = new LinkedHashMap<>();
    /** Max queue entries looked at per world per tick, skipped ones included. */
    private static final int CRATER_SCAN_PER_TICK = 8 * CRATER_BLOCKS_PER_TICK;
    /** Pending block positions of one world, deduplicated (overlapping craters), dug in insertion order. */
    private static final class CraterQueue {
        final ServerPlayer breaker;
        final LinkedHashSet<Long> pending = new LinkedHashSet<>();
        CraterQueue(ServerPlayer breaker) { this.breaker = breaker; }
    }
    private static final Map<ServerLevel, CraterQueue> PENDING = new IdentityHashMap<>();

    private static final class Task {
        final ServerPlayer caster;
        final ServerLevel world;
        final long started, seed;
        final Vec3 anchor;
        final float yaw;
        Phase phase = Phase.BUILD;
        long phaseAt, commandedAt = -1;
        boolean commanded;
        List<LivingEntity> candidates = List.of();
        /** The one target the beams spare until the kill-shot volley; picked on the first barrage tick. */
        LivingEntity featured;
        boolean featuredPicked;
        final Work work = new Work();
        /** Height of the land the beams fall on (see {@link #fire}). */
        double groundY;
        /** Config snapshot taken at click 1: server tick and client payload must keep agreeing after a reload. */
        final int skyCircles = GodWandConfig.SERVER.gateSkyCircles, barrageTicks = GodWandConfig.SERVER.gateBarrageTicks;
        Task(ServerPlayer player) {
            caster = player; world = player.serverLevel(); started = phaseAt = world.getGameTime();
            anchor = player.position(); yaw = player.getYRot();
            // The land under the caster, through any tree they stand on: where beams over unloaded land strike until real hits refine it.
            groundY = Math.min(anchor.y, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(anchor.x), (int) Math.floor(anchor.z)));
            seed = started * 0x9E3779B97F4A7C15L ^ player.getUUID().getLeastSignificantBits();
        }
    }

    private SilentGateManager() {}

    /** age = ticks since click 1 for BUILD/WAIT, ticks since the command for BARRAGE/END; null means finished. */
    static Phase next(Phase phase, long age, boolean commanded, int waitTicks, int barrageTicks) {
        return switch (phase) {
            case BUILD -> age >= SilentGateSequence.BUILD_TICKS ? Phase.WAIT : Phase.BUILD;
            case WAIT -> commanded || age >= SilentGateSequence.BUILD_TICKS + waitTicks ? Phase.BARRAGE : Phase.WAIT;
            case BARRAGE -> age >= SilentGateSequence.ACTIVATE_TICKS + barrageTicks ? Phase.END : Phase.BARRAGE;
            case END -> age >= SilentGateSequence.endTick(barrageTicks) + SilentGateSequence.RETURN_TICKS ? null : Phase.END;
        };
    }

    static boolean cooldownOnCancel(Phase phase) { return phase != Phase.BUILD; }

    public static boolean click(ServerPlayer player, InteractionHand hand) {
        Task task = TASKS.get(player.getUUID());
        if (task != null) {
            if (task.phase != Phase.WAIT || invalid(task)) return false;
            task.commanded = true;
            enter(task, Phase.BARRAGE);
            fx(task, CinematicFxPayload.EVENT_TRAUMA_SHAKE, .8f, 14);
            return true;
        }
        var world = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || !GodWandConfig.SERVER.isDimensionAllowed(world.dimension().location().toString())
                || TASKS.values().stream().filter(t -> t.world == world).count() >= SilentGateSequence.MAX_TASKS_PER_WORLD
                || !holding(player)) return false;
        task = new Task(player);
        TASKS.put(player.getUUID(), task);
        broadcast(task, task.phase.ordinal(), new int[0], new double[0]);
        return true;
    }

    public static boolean movementLocked(ServerPlayer player) {
        Task task = TASKS.get(player.getUUID());
        return task != null && (task.phase == Phase.BUILD || task.phase == Phase.WAIT)
                && player.isAlive() && !player.isSpectator() && task.world == player.serverLevel();
    }

    public static boolean invulnerable(ServerPlayer player) {
        Task task = TASKS.get(player.getUUID());
        return task != null && !invalid(task);
    }

    public static void cancelFromClient(ServerPlayer player, long startedAt) {
        Task task = TASKS.get(player.getUUID());
        // Only while it is built or waits (the earlier skills stop when the button is let go; this one is clicked, so it has its own way out): once the gate
        // fires it cannot be called back (owner, 2026-11-14).
        if (task != null && task.started == startedAt && (task.phase == Phase.BUILD || task.phase == Phase.WAIT)) cancel(task);
    }

    public static void clearAllTasks() { TASKS.clear(); PENDING.clear(); budgetTick = Long.MIN_VALUE; }

    private static boolean holding(ServerPlayer player) {
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_SILENT_GATE) return true;
        }
        return false;
    }

    private static boolean invalid(Task task) {
        return !task.caster.isAlive() || task.caster.hasDisconnected() || task.caster.isSpectator()
                || task.caster.serverLevel() != task.world
                || (task.phase == Phase.BUILD || task.phase == Phase.WAIT) && !holding(task.caster);
    }

    private static void cancel(Task task) {
        TASKS.remove(task.caster.getUUID());
        cancelled(task);
    }

    private static void cancelled(Task task) {
        broadcast(task, SilentGatePayload.CANCEL, new int[0], new double[0]);
        if (cooldownOnCancel(task.phase)) cooldown(task);
    }

    private static void cooldown(Task task) {
        if (!task.caster.hasDisconnected()) SkillCooldowns.start(task.caster, GodWandItem.MODE_SILENT_GATE, GodWandConfig.skillCooldownTicks(GodWandItem.MODE_SILENT_GATE, 1));
    }

    private static void enter(Task task, Phase phase) {
        task.phase = phase;
        task.phaseAt = task.world.getGameTime();
        if (phase == Phase.BARRAGE) task.commandedAt = task.phaseAt;
        broadcast(task, phase.ordinal(), new int[0], new double[0]);
    }

    private static void fx(Task task, int event, float intensity, int ticks) {
        sender.send(task.world, task.anchor, new CinematicFxPayload(event, task.anchor.x, task.anchor.y, task.anchor.z, intensity, ticks));
    }

    private static void broadcast(Task task, int phase, int[] shots, double[] hits) {
        long at = phase == SilentGatePayload.CANCEL ? task.world.getGameTime() : task.phaseAt;
        sender.send(task.world, task.anchor, new SilentGatePayload(task.caster.getId(), task.started, at, phase,
                task.anchor.x, task.anchor.y, task.anchor.z, task.yaw, task.seed,
                task.skyCircles, task.barrageTicks, shots, hits, task.featured == null ? SilentGatePayload.NO_FEATURED : task.featured.getId()));
    }

    public static void tick(ServerLevel world) {
        long now = world.getGameTime();
        for (var it = TASKS.values().iterator(); it.hasNext();) {
            Task task = it.next();
            if (task.world != world) continue;
            if (invalid(task)) { it.remove(); cancelled(task); continue; }
            boolean early = task.phase == Phase.BUILD || task.phase == Phase.WAIT;
            long age = now - (early ? task.started : task.commandedAt);
            if (early) {
                task.caster.setDeltaMovement(Vec3.ZERO);
                task.caster.fallDistance = 0;
                if (task.caster.position().distanceToSqr(task.anchor) > .0001)
                    task.caster.connection.teleport(task.anchor.x, task.anchor.y, task.anchor.z, task.caster.getYRot(), task.caster.getXRot());
                // Gentle: the director shoots the gate on long lenses, which magnify every degree of shake.
                if (task.phase == Phase.BUILD && age == SilentGateSequence.DESCENT_END) fx(task, CinematicFxPayload.EVENT_TRAUMA_SHAKE, .4f, 20);
                if (task.phase == Phase.BUILD && age == SilentGateSequence.SHOCK) {
                    fx(task, CinematicFxPayload.EVENT_TRAUMA_SHAKE, .5f, 24);
                }
            } else if (task.phase == Phase.BARRAGE) barrage(task, age);
            if (!early) runPending(task, now);
            Phase next = next(task.phase, age, task.commanded, GodWandConfig.SERVER.gateWaitTicks, task.barrageTicks);
            if (next == null) { it.remove(); cooldown(task); }
            else if (next != task.phase) {
                if (next == Phase.BARRAGE) task.commanded = true; // wait ran out: same as a command
                enter(task, next);
            }
        }
        drain(world);
    }

    /** The kill beam while its circle gathers light: the damage and crater land when it fires. */
    private record Pending(long at, LivingEntity target, float damage) {}
    /** Per task: circles that are charging or firing (index to the tick they are free again), the kill strike waiting for its beam, the kill beam sent. */
    private static final class Work {
        final Map<Integer, Long> busy = new HashMap<>();
        final List<Pending> pending = new ArrayList<>();
        boolean heavyDone;
    }

    private static void barrage(Task task, long age) {
        var cfg = GodWandConfig.SERVER;
        var work = task.work;
        long now = task.world.getGameTime();
        int barrageAge = (int) age - SilentGateSequence.ACTIVATE_TICKS;
        if (barrageAge < 0) return;
        if (barrageAge % 10 == 0) {
            var box = new AABB(task.anchor, task.anchor).inflate(cfg.gateTargetRadius);
            task.candidates = task.world.getEntitiesOfClass(LivingEntity.class, box,
                    e -> SilentGateTargets.eligible(e, task.caster, cfg.gateTargetMode));
        }
        boolean fresh = false;
        if (!task.featuredPicked && !task.candidates.isEmpty()) {
            task.featuredPicked = fresh = true;
            task.featured = SilentGateTargets.featured(task.candidates, e -> e.getMaxHealth(), e -> e.position().distanceToSqr(task.anchor),
                    e -> task.world.canSeeSky(e.blockPosition()));
        }
        int killAge = SilentGateSequence.killBarrageAge(task.barrageTicks);
        LivingEntity featured = task.featured != null && task.featured.isAlive() ? task.featured : null;
        float damage = cfg.gateBeamDamage * cfg.damageMultiplier;
        var shots = new ArrayList<Integer>();
        var hits = new ArrayList<Double>();
        var used = new HashSet<Integer>();
        // The decisive beam: the circle nearest the featured target gathers light during the first second of the kill shot, then a single heavy beam kills it.
        int heavyStart = killAge - SilentGateSequence.HEAVY_CHARGE;
        if (featured != null && !work.heavyDone && barrageAge >= heavyStart && barrageAge <= killAge - 4) {
            Vec3 centre = featured.getBoundingBox().getCenter();
            int c = SilentGateSequence.nearestCircle(task.anchor, task.yaw, task.seed, task.skyCircles, centre, i -> work.busy.getOrDefault(i, 0L) <= now);
            if (c >= 0) {
                work.heavyDone = true;
                work.busy.put(c, now + SilentGateSequence.HEAVY_CHARGE + SilentGateSequence.BEAM_TICKS);
                used.add(c);
                work.pending.add(new Pending(now + SilentGateSequence.HEAVY_CHARGE, featured, damage * SilentGateSequence.HEAVY_DAMAGE_MULT));
                shots.add(SilentGatePayload.HEAVY + c);
                hits.add(centre.x); hits.add(centre.y); hits.add(centre.z);
            }
        }
        var firing = new LinkedHashSet<Integer>();
        // Only the sky fires: the discs behind the caster are the caster's own.
        for (int i = 0; i < task.skyCircles; i++)
            if (SilentGateSequence.fires(i, barrageAge, task.barrageTicks, task.seed)) firing.add(i);
        var pool = featured != null && SilentGateSequence.withheld(barrageAge, killAge)
                ? task.candidates.stream().filter(e -> e != featured).toList() : task.candidates;
        var picks = SilentGateTargets.pick(pool, task.anchor, firing.size(), task.seed + barrageAge);
        int k = 0;
        for (int index : firing) {
            fire(task, index, k < picks.size() && picks.get(k).isAlive() ? picks.get(k) : null, damage, shots, hits, used, now);
            k++;
        }
        if (shots.isEmpty() && !fresh) return;
        // More beams than one packet may carry go out as several packets (same phase, same tick).
        int max = SilentGatePayload.MAX_SHOTS;
        for (int from = 0; from == 0 || from < shots.size(); from += max) {
            int to = Math.min(shots.size(), from + max);
            broadcast(task, Phase.BARRAGE.ordinal(), shots.subList(from, to).stream().mapToInt(Integer::intValue).toArray(),
                    hits.subList(3 * from, 3 * to).stream().mapToDouble(Double::doubleValue).toArray());
        }
    }

    /**
     * One ordinary beam. No circle ever moves: with a target the beam comes from a free sky circle close above it ({@link SilentGateSequence#beamCircle}) and leans only
     * as much as it must to reach it; without a target the circle fires straight down.
     */
    private static void fire(Task task, int index, LivingEntity target, float damage, List<Integer> shots, List<Double> hits, Set<Integer> used, long now) {
        var work = task.work;
        if (target == null) {
            if (used.contains(index) || work.busy.getOrDefault(index, 0L) > now) return;
            Vec3 above = SilentGateSequence.skyCircle(task.anchor, task.yaw, task.seed, index, task.skyCircles);
            Vec3 ground = groundBelow(task.world, task.caster, above);
            used.add(index);
            shots.add(index);
            // A circle too far out to touch the world still fires, for show: every circle in the sky is seen to shoot (owner, 2026-11-12). It strikes the level
            // of the land the other beams found, not the caster's own height (a caster on a tree saw those beams stop in the air: owner, 2026-11-15).
            if (ground == null) { hits.add(above.x); hits.add(task.groundY); hits.add(above.z); return; }
            task.groundY += (ground.y - task.groundY) * .1;
            hits.add(ground.x); hits.add(ground.y); hits.add(ground.z);
            crater(task, ground);
            return;
        }
        Vec3 centre = target.getBoundingBox().getCenter();
        int c = SilentGateSequence.beamCircle(task.anchor, task.yaw, task.seed, task.skyCircles, centre, i -> !used.contains(i) && work.busy.getOrDefault(i, 0L) <= now);
        // No free circle near this target (others stand close to it): the scheduled circle fires straight down instead. Returning here dropped the shot, so the
        // rain was thin whenever creatures stood in a bunch and thick with none about (owner, 2026-11-14: "each cast fires a different amount").
        if (c < 0) { fire(task, index, null, damage, shots, hits, used, now); return; }
        used.add(c);
        work.busy.put(c, now + SilentGateSequence.BEAM_TICKS);
        shots.add(c);
        hits.add(centre.x); hits.add(centre.y); hits.add(centre.z);
        strike(task, target, damage);
    }

    /** Damage and crater of a beam that has reached its target. */
    private static void strike(Task task, LivingEntity target, float damage) {
        if (!target.isAlive()) return;
        SpellDamage.hurt(target, task.world.damageSources().indirectMagic(task.caster, task.caster), damage);
        Vec3 ground = groundBelow(task.world, task.caster, target.position().add(0, 1, 0));
        if (ground != null && ground.y < target.getY() - 8) ground = null;
        if (ground != null) crater(task, ground);
    }

    private static void runPending(Task task, long now) {
        for (var it = task.work.pending.iterator(); it.hasNext();) {
            var p = it.next();
            if (p.at() > now) continue;
            it.remove();
            strike(task, p.target(), p.damage());
        }
    }

    /** First collidable block straight below, only inside an already loaded chunk; null otherwise. */
    private static Vec3 groundBelow(ServerLevel world, ServerPlayer caster, Vec3 from) {
        var pos = BlockPos.containing(from);
        if (!world.isInWorldBounds(pos) || world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        var hit = world.clip(new ClipContext(from, new Vec3(from.x, world.getMinBuildHeight(), from.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    private static void crater(Task task, Vec3 at) {
        int r = GodWandConfig.SERVER.gateCraterRadius;
        if (!GodWandConfig.SERVER.gateTerrainDamage || r <= 0) return;
        queueCrater(task.world, task.caster, at, r);
    }

    static void queueCrater(ServerLevel world, ServerPlayer caster, Vec3 at, int r) {
        var queue = PENDING.computeIfAbsent(world, w -> new CraterQueue(caster));
        var c = BlockPos.containing(at);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++)
            if (x * x + y * y + z * z <= r * r) queue.pending.add(c.offset(x, y, z).asLong());
    }

    static int pendingCraters(ServerLevel world) {
        var queue = PENDING.get(world);
        return queue == null ? 0 : queue.pending.size();
    }

    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetLeft;

    /** One shared budget of 96 blocks per server tick across all worlds and tasks (every world ticks at the same game time). */
    static int budget(long gameTime) {
        if (gameTime != budgetTick) { budgetTick = gameTime; budgetLeft = CRATER_BLOCKS_PER_TICK; }
        return budgetLeft;
    }

    static void spendBudget(int blocks) { budgetLeft -= blocks; }

    /** Digs queued blocks while the shared budget lasts; the rest wait for later ticks. */
    static void drain(ServerLevel world) {
        var queue = PENDING.get(world);
        if (queue == null) return;
        int budget = budget(world.getGameTime()), scan = CRATER_SCAN_PER_TICK;
        boolean removedDisplacement = false;
        for (var it = queue.pending.iterator(); budget > 0 && scan-- > 0 && it.hasNext();) {
            BlockPos pos = BlockPos.of(it.next());
            it.remove();
            if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos)) continue;
            var state = world.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(world, pos) < 0) continue;
            if (SpatialDisplacementManager.getActiveRecord(world.dimension(), pos) != null) {
                SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                removedDisplacement = true;
            }
            // Same flags as Fuga/Atomic craters: no break level event (64 particles + sound per block on every client), no neighbour-update cascade.
            world.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            budget--;
            spendBudget(1);
        }
        if (queue.pending.isEmpty()) PENDING.remove(world);
        if (removedDisplacement) DisplacementPersistentState.getServerState(world).setDirty();
    }
}
