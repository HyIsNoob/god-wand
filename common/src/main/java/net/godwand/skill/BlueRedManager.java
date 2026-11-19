package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.BlueRedPayload;
import net.godwand.platform.Services;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Server authority for Blue, Red and Purple (mode 8). Nothing here is saved: a server restart clears it. */
public final class BlueRedManager {
    interface Sender { void send(ServerLevel level, Vec3 pos, CustomPacketPayload payload); }
    interface Finder { List<Entity> find(ServerLevel level, AABB box); }
    interface Hurter { boolean hurt(Entity target, ServerPlayer caster, float amount); }

    /** Test seams: the real ones fan out to players within 256 blocks, scan the level and route through SpellDamage. */
    static Sender sender = (level, pos, payload) -> Services.NETWORK.sendToNearby(level, pos, 256, payload);
    static Finder finder = (level, box) -> level.getEntities((Entity) null, box, Entity::isAlive);
    static Hurter hurter = (target, caster, amount) ->
            SpellDamage.hurt(target, caster.serverLevel().damageSources().indirectMagic(caster, caster), amount);

    public static final class Blue {
        final ServerPlayer owner;
        final ServerLevel level;
        Vec3 pos, vel = Vec3.ZERO;
        BlueState state = BlueState.CHARGING;
        int age, anchoredAge;
        float credited;
        int kills;
        final List<BlueEater.Eaten> eaten = new ArrayList<>();
        Blue(ServerPlayer owner, ServerLevel level, Vec3 pos) { this.owner = owner; this.level = level; this.pos = pos; }
        boolean held() { return state != BlueState.ANCHORED; }
        public BlueState state() { return state; }
        public Vec3 pos() { return pos; }
    }

    static final class Red {
        final ServerPlayer shooter;
        final ServerLevel level;
        Vec3 origin = Vec3.ZERO, dir = Vec3.ZERO;
        int age, charged;
        double power = 1;
        boolean fired;
        final Set<UUID> struck = new HashSet<>();
        Red(ServerPlayer shooter, ServerLevel level) { this.shooter = shooter; this.level = level; }
    }

    public static final class Purple {
        final ServerLevel level;
        final Vec3 center;
        final ServerPlayer owner, shooter;
        int age, cursor, stain;
        /** When the crater was dug and the land stained (ages), -1 until then. */
        public int dugAt = -1, stainedAt = -1;
        final List<BlueEater.Eaten> lifted = new ArrayList<>();
        Purple(ServerLevel level, Vec3 center, ServerPlayer owner, ServerPlayer shooter) { this.level = level; this.center = center; this.owner = owner; this.shooter = shooter; }
        public int age() { return age; }
        public Vec3 center() { return center; }
        boolean participant(Entity e) { return SpellDamage.isCaster(e, owner) || SpellDamage.isCaster(e, shooter); }
    }

    private static final List<Blue> BLUES = new ArrayList<>();
    private static final List<Purple> PURPLES = new ArrayList<>();
    private static final List<Red> REDS = new ArrayList<>();

    private BlueRedManager() {}

    public static List<Blue> blues(ServerLevel level) {
        List<Blue> out = new ArrayList<>();
        for (Blue b : BLUES) if (b.level == level) out.add(b);
        return out;
    }

    private static Blue blueOf(ServerPlayer player) {
        for (Blue b : BLUES) if (SpellDamage.isCaster(b.owner, player)) return b;
        return null;
    }

    static boolean holdsBlueRed(ServerPlayer player) {
        var stack = player.getMainHandItem();
        return stack.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_BLUE_RED;
    }

    private static Vec3 aim(ServerPlayer player) {
        return player.getEyePosition().add(player.getLookAngle().scale(BlueRedRules.BLUE_DISTANCE));
    }

    private static Red chargingRedOf(ServerPlayer player) {
        for (Red r : REDS) if (!r.fired && SpellDamage.isCaster(r.shooter, player)) return r;
        return null;
    }

    private static BlueRedPayload event(int kind, ServerPlayer who, Vec3 at, Vec3 dir) {
        return new BlueRedPayload(kind, who.getId(), -1, 0, at.x, at.y, at.z, dir.x, dir.y, dir.z, 0);
    }

    /** Right click on Blue/Red: Red starts charging in front of the caster. Refused while the caster still holds Blue. */
    public static boolean beginRed(ServerPlayer player) {
        Blue own = blueOf(player);
        if (own != null && own.held() || chargingRedOf(player) != null || !player.isAlive() || player.isSpectator()) return false;
        ServerLevel level = player.serverLevel();
        if (!GodWandConfig.SERVER.isDimensionAllowed(level.dimension().location().toString())) return false;
        if (!SkillCooldowns.ready(player, SkillCooldownState.RED_SLOT)) return false;
        REDS.add(new Red(player, level));
        send(level, player.getEyePosition(), event(BlueRedPayload.RED_CHARGE, player, player.getEyePosition(), Vec3.ZERO));
        return true;
    }

    /** Called every use tick (owner, 2026-11-19): Red is held as long as the player likes, gathering power; sneaking calls it off. */
    public static void chargeTick(ServerPlayer player, int usedTicks) {
        Red red = chargingRedOf(player);
        if (red == null) return;
        if (player.isShiftKeyDown()) { cancelRed(player); player.stopUsingItem(); return; }
        red.charged = usedTicks;
    }

    /** Right click let go: Red fires with the power it has built (BlueRedRules.redPower); a tap is called off with no cooldown. */
    public static void releaseRed(ServerPlayer player) {
        Red red = chargingRedOf(player);
        if (red == null) return;
        if (red.charged < BlueRedRules.RED_MIN_CHARGE) { cancelRed(player); return; }
        red.power = BlueRedRules.redPower(red.charged, GodWandConfig.SERVER.redChargeTicks);
        red.fired = true;
        red.dir = player.getLookAngle().normalize();
        red.origin = player.getEyePosition().add(red.dir.scale(1.5));
        send(red.level, red.origin, new BlueRedPayload(BlueRedPayload.RED_FIRE, player.getId(), -1, 0, red.origin.x, red.origin.y, red.origin.z,
                red.dir.x, red.dir.y, red.dir.z, (int) Math.round(red.power * 100)));
        SkillCooldowns.start(player, SkillCooldownState.RED_SLOT, GodWandConfig.skillCooldownTicks(SkillCooldownState.RED_SLOT, 1));
    }

    /** Called off (sneak, a tap, or switching skill): an unfired Red goes with no cooldown; a fired one flies on. */
    public static void cancelRed(ServerPlayer player) {
        Red red = chargingRedOf(player);
        if (red == null) return;
        REDS.remove(red);
        send(red.level, player.getEyePosition(), event(BlueRedPayload.RED_CANCEL, player, player.getEyePosition(), Vec3.ZERO));
    }

    /** One tick of Red's flight; returns terrain inspections used. */
    private static int fly(Red red, int budget, long deadline) {
        var cfg = GodWandConfig.SERVER;
        double range = cfg.redRange;
        Vec3 from = BlueRedRules.redAt(red.origin, red.dir, BlueRedRules.RED_SPEED, red.age);
        red.age++;
        boolean last = red.age * BlueRedRules.RED_SPEED >= range;
        Vec3 to = last ? red.origin.add(red.dir.scale(range)) : BlueRedRules.redAt(red.origin, red.dir, BlueRedRules.RED_SPEED, red.age);
        // Never load terrain: Red stops where loaded chunks stop.
        if (red.level.getChunkSource().getChunkNow(Mth.floor(to.x) >> 4, Mth.floor(to.z) >> 4) == null) { endRed(red, from); return 0; }
        Blue hit = null;
        double first = 2;
        for (Blue b : blues(red.level)) {
            double t = BlueRedRules.segmentSphere(from, to, b.pos, BlueRedRules.BLUE_RADIUS + 1);
            if (t >= 0 && t < first) { first = t; hit = b; }
        }
        if (hit != null) {
            if (BlueRedRules.meet(hit.state) == BlueRedRules.RedMeetsBlue.PURPLE) { REDS.remove(red); startPurple(red.level, hit, red.shooter); return 0; }
            shatter(hit);
        }
        float damage = (float) (cfg.redDamage * cfg.damageMultiplier * red.power);
        for (Entity e : finder.find(red.level, new AABB(from, to).inflate(BlueRedRules.RED_HIT_RADIUS + 1))) {
            if (!(e instanceof LivingEntity) || SpellDamage.isCaster(e, red.shooter) || e.isSpectator()) continue;
            Vec3 middle = e.position().add(0, e.getBbHeight() / 2, 0);
            if (BlueRedRules.distanceToSegment(middle, from, to) <= BlueRedRules.RED_HIT_RADIUS + e.getBbWidth() / 2 && red.struck.add(e.getUUID())) {
                hurter.hurt(e, red.shooter, damage);
                // Owner, 2026-11-18: the target is visibly blown away along the shot, in the same tick the hit shows.
                e.setDeltaMovement(e.getDeltaMovement().add(red.dir.scale(1.8)).add(0, 0.45, 0));
                e.hurtMarked = true;
                // The blast shows where it struck, in the same tick as the knockback.
                send(red.level, middle, event(BlueRedPayload.RED_HIT, red.shooter, middle, red.dir));
            }
        }
        int used = 0;
        double length = from.distanceTo(to);
        for (double step = 0; step <= length && used < budget; step += 1)
            used += BlueEater.carve(red.level, from.add(red.dir.scale(step)), cfg.redTunnelRadius, budget - used, deadline, Integer.MAX_VALUE, null, cfg.redTerrainDamage);
        if (last) { used += explode(red, to, budget - used, deadline); endRed(red, to); }
        return used;
    }

    private static int explode(Red red, Vec3 at, int budget, long deadline) {
        var cfg = GodWandConfig.SERVER;
        double radius = Math.max(cfg.redCraterRadius, 2);
        for (Entity e : finder.find(red.level, new AABB(at, at).inflate(radius))) {
            if (SpellDamage.isCaster(e, red.shooter) || e.isSpectator() || e instanceof Display) continue;
            Vec3 out = e.position().subtract(at);
            if (out.length() > radius) continue;
            if (e instanceof LivingEntity) hurter.hurt(e, red.shooter, (float) (cfg.redDamage * cfg.damageMultiplier * red.power));
            e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(1.0)).add(red.dir.scale(1.4)).add(0, 0.5, 0));
            e.hurtMarked = true;
        }
        int crater = cfg.redCraterRadius <= 0 ? 0 : Math.max(2, (int) Math.round(cfg.redCraterRadius * (.5 + .5 * red.power)));
        return BlueEater.carve(red.level, at, crater, budget, deadline, Integer.MAX_VALUE, null, cfg.redTerrainDamage);
    }

    private static void endRed(Red red, Vec3 at) {
        REDS.remove(red);
        send(red.level, at, event(BlueRedPayload.RED_END, red.shooter, at, red.dir));
    }

    /** Red through an unready Blue: Blue bursts outward and is gone; Red flies on. */
    private static void shatter(Blue blue) {
        send(blue.level, blue.pos, new BlueRedPayload(BlueRedPayload.BLUE_SHATTER, blue.owner.getId(), -1, 0, blue.pos.x, blue.pos.y, blue.pos.z, 0, 0, 0, 0));
        for (Entity e : finder.find(blue.level, new AABB(blue.pos, blue.pos).inflate(GodWandConfig.SERVER.bluePullRadius))) {
            if (e.isSpectator() || e instanceof Display) continue;
            Vec3 out = e.position().subtract(blue.pos);
            if (out.lengthSqr() < 1e-6) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(1.2)).add(0, 0.3, 0));
            e.hurtMarked = true;
        }
        remove(blue);
    }

    /** Red met a ready Blue: Purple at the Blue. Both players watch it and both lose the whole skill for purpleCooldownTicks. */
    static void startPurple(ServerLevel level, Blue blue, ServerPlayer shooter) {
        BLUES.remove(blue);
        BlueEater.retireAll(blue.eaten);
        Vec3 sky = blue.pos.add(0, BlueRedRules.PURPLE_LIFT, 0);
        PURPLES.add(new Purple(level, sky, blue.owner, shooter));
        BlueEater.prepare(GodWandConfig.SERVER.purpleRadius);
        send(level, sky, new BlueRedPayload(BlueRedPayload.PURPLE, blue.owner.getId(), shooter.getId(), 0, sky.x, sky.y, sky.z, 0, 0, 0, 0));
        int lock = GodWandConfig.cooldownTicks(GodWandConfig.SERVER.purpleCooldownTicks);
        for (ServerPlayer p : SpellDamage.isCaster(blue.owner, shooter) ? List.of(shooter) : List.of(blue.owner, shooter)) {
            SkillCooldowns.start(p, GodWandItem.MODE_BLUE_RED, lock);
            SkillCooldowns.start(p, SkillCooldownState.RED_SLOT, lock);
        }
    }

    public static List<Purple> purples(ServerLevel level) {
        List<Purple> out = new ArrayList<>();
        for (Purple p : PURPLES) if (p.level == level) out.add(p);
        return out;
    }

    private static boolean inCinematic(ServerPlayer player) {
        for (Purple p : PURPLES) if (p.age < BlueRedRules.PURPLE_CINEMATIC && p.participant(player)) return true;
        return false;
    }
    /** Purple's two players cannot be hurt or move while their cutscene plays. */
    public static boolean invulnerable(ServerPlayer player) { return inCinematic(player); }
    public static boolean movementLocked(ServerPlayer player) { return inCinematic(player); }

    private static int tickPurple(Purple p, int budget, long deadline) {
        var cfg = GodWandConfig.SERVER;
        p.age++;
        if (p.age == BlueRedRules.PURPLE_DETONATE) detonate(p);
        int used = 0;
        for (int k = 0; k < BlueRedRules.PURPLE_STRIKES; k++) if (p.age == BlueRedRules.strikeTick(k) && p.level.getChunkSource() != null) {
            double[] o = BlueRedRules.strikeOffset(k);
            int x = (int) Math.floor(p.center.x + o[0]), z = (int) Math.floor(p.center.z + o[1]);
            int y = p.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            used += BlueEater.carve(p.level, new Vec3(x + .5, y - 1, z + .5), 4, budget, deadline, Integer.MAX_VALUE, null, cfg.purpleTerrainDamage);
        }
        // The ground round it charges and chunks of it tear loose and float, crackling, until the burst takes them (owner, 2026-11-19).
        if (p.age > BlueRedRules.PURPLE_MERGE && p.age < BlueRedRules.PURPLE_DETONATE && p.age % 2 == 0 && cfg.purpleTerrainDamage && p.level.getChunkSource() != null)
            for (int i = 0; i < 3 && p.lifted.size() < BlueRedRules.LIFT_MAX; i++) {
                double[] o = BlueRedRules.strikeOffset(100 + p.age * 3 + i);
                int x = (int) Math.floor(p.center.x + o[0]), z = (int) Math.floor(p.center.z + o[1]);
                int y = p.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                var chunk = BlueEater.lift(p.level, new net.minecraft.core.BlockPos(x, y, z));
                if (chunk != null) p.lifted.add(chunk);
            }
        BlueEater.hover(p.lifted);
        if (p.age >= BlueRedRules.PURPLE_DETONATE && !p.lifted.isEmpty()) { p.lifted.forEach(BlueEater::retire); p.lifted.clear(); }
        budget -= used;
        if (p.age > BlueRedRules.PURPLE_TERRAIN_END) { PURPLES.remove(p); return used; }
        if (p.age < BlueRedRules.PURPLE_DETONATE || cfg.purpleRadius <= 0) return used;
        // Owner, 2026-11-19: the crater must be dug while the blast is still going, not long after it. For the blast's first 40 ticks Purple
        // gets a much larger share of the tick (its own 30 ms and a bigger cap); the shared 8 ms after that.
        boolean rush = p.age < BlueRedRules.PURPLE_DETONATE + 40;
        long until = rush ? Math.max(deadline, System.nanoTime() + 30_000_000L) : deadline;
        int allowance = rush ? Math.max(budget, 120_000) : budget;
        int[][] cells = BlueEater.sphere(cfg.purpleRadius);
        if (p.cursor < cells.length) {
            int before = p.cursor;
            p.cursor = BlueEater.carveFrom(p.level, p.center, cells, p.cursor, allowance, until, cfg.purpleTerrainDamage, rush ? 9000 : BlueEater.PURPLE_CLEAR_PER_TICK);
            if (!cfg.purpleTerrainDamage) p.cursor = cells.length;
            if (p.cursor >= cells.length) p.dugAt = p.age;
            return Math.min(budget, p.cursor - before) + used;
        }
        // Once the crater is dug, the land round it is left stained violet.
        if (p.stain == Integer.MAX_VALUE || p.level.getChunkSource() == null) return used;
        int before = p.stain;
        p.stain = BlueEater.stainFrom(p.level, p.center, cfg.purpleRadius, p.stain, allowance, until, cfg.purpleTerrainDamage, rush ? 4000 : BlueEater.STAIN_PER_TICK);
        if (p.stain == Integer.MAX_VALUE) p.stainedAt = p.age;
        return Math.min(budget, p.stain == Integer.MAX_VALUE ? 0 : p.stain - before) + used;
    }

    private static void detonate(Purple p) {
        var cfg = GodWandConfig.SERVER;
        // Owner, 2026-11-18: the blast reaches half again further than the crater it digs.
        double radius = Math.max(cfg.purpleRadius, 2) * 1.5;
        for (Entity e : finder.find(p.level, new AABB(p.center, p.center).inflate(radius))) {
            if (p.participant(e) || e.isSpectator() || e instanceof Display) continue;
            Vec3 out = e.position().subtract(p.center);
            if (out.length() > radius) continue;
            if (e instanceof LivingEntity) hurter.hurt(e, p.shooter, cfg.purpleDamage * cfg.damageMultiplier);
            if (out.lengthSqr() > 1e-6) { e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(2.5)).add(0, 0.8, 0)); e.hurtMarked = true; }
        }
    }

    /** Left click started or stopped on the client. */
    public static void input(ServerPlayer player, boolean held) {
        Blue existing = blueOf(player);
        if (!held) {
            if (existing != null && existing.held()) release(existing);
            return;
        }
        // One Blue each; an anchored one waiting for Red is never replaced by a stray click.
        if (existing != null || !player.isAlive() || player.isSpectator() || !holdsBlueRed(player)) return;
        ServerLevel level = player.serverLevel();
        if (!GodWandConfig.SERVER.isDimensionAllowed(level.dimension().location().toString())) return;
        if (!SkillCooldowns.ready(player, GodWandItem.MODE_BLUE_RED)) return;
        Blue blue = new Blue(player, level, aim(player));
        BLUES.add(blue);
        broadcast(blue);
    }

    private static void release(Blue blue) {
        BlueState next = BlueRedRules.onRelease(blue.state);
        if (next == null) { remove(blue); return; }
        blue.state = next;
        blue.anchoredAge = 0;
        broadcast(blue);
    }

    /** Blue is gone: tell clients and start the Blue cooldown for its owner. */
    static void remove(Blue blue) {
        BLUES.remove(blue);
        BlueEater.retireAll(blue.eaten);
        send(blue.level, blue.pos, new BlueRedPayload(BlueRedPayload.BLUE_GONE, blue.owner.getId(), -1, 0, blue.pos.x, blue.pos.y, blue.pos.z, 0, 0, 0, 0));
        if (!blue.owner.hasDisconnected())
            SkillCooldowns.start(blue.owner, GodWandItem.MODE_BLUE_RED, GodWandConfig.skillCooldownTicks(GodWandItem.MODE_BLUE_RED, 1));
    }

    public static void tick(ServerLevel level) {
        var cfg = GodWandConfig.SERVER;
        // Shared terrain allowance for every Blue in this level this tick, like Fuga and Atomic.
        int budget = 32768;
        long deadline = System.nanoTime() + 8_000_000L;
        for (Blue blue : blues(level)) {
            blue.age++;
            if (blue.held()) {
                if (!blue.owner.isAlive() || !holdsBlueRed(blue.owner)) { release(blue); if (!BLUES.contains(blue)) continue; }
                else { Vec3[] next = BlueRedRules.follow(blue.pos, blue.vel, aim(blue.owner)); blue.pos = next[0]; blue.vel = next[1]; }
            } else blue.anchoredAge++;
            BlueState before = blue.state;
            blue.state = BlueRedRules.atChargeLimit(blue.state, blue.age, cfg.blueChargeTicks);
            if (BlueRedRules.expired(blue.state, blue.age, blue.anchoredAge, cfg.blueChargeTicks, cfg.blueAnchorTicks)) { remove(blue); continue; }
            pullAndHurt(blue, blue.age % BlueRedRules.CORE_PERIOD == 0);
            budget -= BlueEater.eat(level, blue.pos, cfg.blueEatRadius, budget, deadline, blue.eaten);
            BlueEater.animate(blue.eaten, blue.pos);
            // Owner, 2026-11-19: ready at the damage threshold, or after killing enough mobs, however weak (three chickens count).
            if (blue.state == BlueState.CHARGING && (blue.credited >= cfg.blueReadyDamage || blue.kills >= cfg.blueReadyKills)) blue.state = BlueState.READY;
            // Every tick while it moves, so the inertia and the smears behind it read smoothly; every other tick once it is still.
            if (blue.state != before || blue.age % 2 == 0 || blue.held() && blue.vel.lengthSqr() > 1e-4) broadcast(blue);
        }
        for (Red red : new ArrayList<>(REDS))
            if (red.fired && red.level == level && REDS.contains(red)) budget -= fly(red, budget, deadline);
        for (Purple p : purples(level)) budget -= tickPurple(p, budget, deadline);
    }

    private static void pullAndHurt(Blue blue, boolean strike) {
        double radius = GodWandConfig.SERVER.bluePullRadius;
        float damage = GodWandConfig.SERVER.blueCoreDamage * GodWandConfig.SERVER.damageMultiplier;
        for (Entity e : finder.find(blue.level, new AABB(blue.pos, blue.pos).inflate(radius))) {
            if (SpellDamage.isCaster(e, blue.owner) || e.isSpectator() || e instanceof Display) continue;
            Vec3 middle = e.position().add(0, e.getBbHeight() / 2, 0), toward = blue.pos.subtract(middle);
            double distance = toward.length();
            if (distance > radius) continue;
            if (distance > 0.3) {
                double strength = 0.12 + 0.38 * (1 - distance / radius);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.75).add(toward.scale(strength / distance)));
                e.hurtMarked = true;
            }
            if (strike && damage > 0 && e instanceof LivingEntity living && distance <= BlueRedRules.CORE_RADIUS + e.getBbWidth() / 2) {
                float healthBefore = living.getHealth();
                if (hurter.hurt(e, blue.owner, damage)) {
                    blue.credited += BlueRedRules.credit(healthBefore, living.getHealth());
                    if (healthBefore > 0 && !living.isAlive()) blue.kills++;
                }
            }
        }
    }

    private static void broadcast(Blue blue) {
        send(blue.level, blue.pos, new BlueRedPayload(BlueRedPayload.BLUE, blue.owner.getId(), -1, blue.age,
                blue.pos.x, blue.pos.y, blue.pos.z, 0, 0, 0, blue.state.ordinal()));
    }

    static void send(ServerLevel level, Vec3 pos, BlueRedPayload payload) { sender.send(level, pos, payload); }

    /** Owner disconnected, died or changed dimension: their Blue goes with them. */
    public static void onLeave(ServerPlayer player) {
        REDS.removeIf(r -> SpellDamage.isCaster(r.shooter, player));
        Blue blue = blueOf(player);
        if (blue == null) return;
        BLUES.remove(blue);
        BlueEater.retireAll(blue.eaten);
        send(blue.level, blue.pos, new BlueRedPayload(BlueRedPayload.BLUE_GONE, blue.owner.getId(), -1, 0, blue.pos.x, blue.pos.y, blue.pos.z, 0, 0, 0, 0));
    }

    public static void clearAll() {
        for (Blue blue : BLUES) BlueEater.retireAll(blue.eaten);
        BLUES.clear();
        REDS.clear();
        PURPLES.clear();
    }
}
