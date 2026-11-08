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
        /** Config snapshot taken at click 1: server tick and client payload must keep agreeing after a reload. */
        final int skyCircles = GodWandConfig.SERVER.gateSkyCircles, barrageTicks = GodWandConfig.SERVER.gateBarrageTicks;
        Task(ServerPlayer player) {
            caster = player; world = player.serverLevel(); started = phaseAt = world.getGameTime();
            anchor = player.position(); yaw = player.getYRot();
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
                task.skyCircles, task.barrageTicks, shots, hits));
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
                if (task.phase == Phase.BUILD && age == 190) fx(task, CinematicFxPayload.EVENT_TRAUMA_SHAKE, .6f, 20);
                if (task.phase == Phase.BUILD && age == 206) {
                    fx(task, CinematicFxPayload.EVENT_TRAUMA_SHAKE, 1f, 24);
                    fx(task, CinematicFxPayload.EVENT_FOV_PUNCH, .9f, 8);
                }
            } else if (task.phase == Phase.BARRAGE) barrage(task, age);
            Phase next = next(task.phase, age, task.commanded, GodWandConfig.SERVER.gateWaitTicks, task.barrageTicks);
            if (next == null) { it.remove(); cooldown(task); }
            else if (next != task.phase) {
                if (next == Phase.BARRAGE) task.commanded = true; // wait ran out: same as a command
                enter(task, next);
            }
        }
        drain(world);
    }

    private static void barrage(Task task, long age) {
        var cfg = GodWandConfig.SERVER;
        int sky = task.skyCircles, barrageAge = (int) age - SilentGateSequence.ACTIVATE_TICKS;
        if (barrageAge < 0) return;
        if (barrageAge % 10 == 0) {
            var box = new AABB(task.anchor, task.anchor).inflate(cfg.gateTargetRadius);
            task.candidates = task.world.getEntitiesOfClass(LivingEntity.class, box,
                    e -> SilentGateTargets.eligible(e, task.caster, cfg.gateTargetMode));
        }
        var firing = new ArrayList<Integer>();
        for (int i = 0; i < sky + SilentGateSequence.GROUND_RING; i++)
            if (SilentGateSequence.fires(i, barrageAge, task.barrageTicks, task.seed)) firing.add(i);
        if (firing.isEmpty()) return;
        var picks = SilentGateTargets.pick(task.candidates, task.anchor, firing.size(), task.seed + barrageAge);
        var shots = new ArrayList<Integer>();
        var hits = new ArrayList<Double>();
        float damage = cfg.gateBeamDamage * cfg.damageMultiplier;
        for (int k = 0; k < firing.size() && shots.size() < SilentGatePayload.MAX_SHOTS; k++) {
            int index = firing.get(k);
            Vec3 from = index < sky ? SilentGateSequence.skyCircle(task.anchor, task.yaw, task.seed, index, sky)
                    : SilentGateSequence.groundCircle(task.anchor, task.yaw, index - sky);
            LivingEntity target = k < picks.size() && picks.get(k).isAlive() ? picks.get(k) : null;
            Vec3 hit, ground;
            if (target != null) {
                hit = target.getBoundingBox().getCenter();
                SpellDamage.hurt(target, task.world.damageSources().indirectMagic(task.caster, task.caster), damage);
                ground = groundBelow(task.world, task.caster, target.position().add(0, 1, 0));
                if (ground != null && ground.y < target.getY() - 8) ground = null;
            } else {
                ground = hit = groundBelow(task.world, task.caster, from);
                if (hit == null) continue;
            }
            shots.add(index);
            hits.add(hit.x); hits.add(hit.y); hits.add(hit.z);
            if (ground != null) crater(task, ground);
        }
        if (shots.isEmpty()) return;
        broadcast(task, Phase.BARRAGE.ordinal(), shots.stream().mapToInt(Integer::intValue).toArray(),
                hits.stream().mapToDouble(Double::doubleValue).toArray());
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
            world.destroyBlock(pos, false, queue.breaker);
            budget--;
            spendBudget(1);
        }
        if (queue.pending.isEmpty()) PENDING.remove(world);
        if (removedDisplacement) DisplacementPersistentState.getServerState(world).setDirty();
    }
}
