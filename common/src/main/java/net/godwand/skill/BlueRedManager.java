package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.BlueRedPayload;
import net.godwand.platform.Services;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        Vec3 pos;
        BlueState state = BlueState.CHARGING;
        int age, anchoredAge;
        float credited;
        final List<BlueEater.Eaten> eaten = new ArrayList<>();
        Blue(ServerPlayer owner, ServerLevel level, Vec3 pos) { this.owner = owner; this.level = level; this.pos = pos; }
        boolean held() { return state != BlueState.ANCHORED; }
    }

    private static final List<Blue> BLUES = new ArrayList<>();

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
                else blue.pos = aim(blue.owner);
            } else blue.anchoredAge++;
            BlueState before = blue.state;
            blue.state = BlueRedRules.atChargeLimit(blue.state, blue.age, cfg.blueChargeTicks);
            if (BlueRedRules.expired(blue.state, blue.age, blue.anchoredAge, cfg.blueChargeTicks, cfg.blueAnchorTicks)) { remove(blue); continue; }
            pullAndHurt(blue, blue.age % BlueRedRules.CORE_PERIOD == 0);
            budget -= BlueEater.eat(level, blue.pos, cfg.blueEatRadius, budget, deadline, blue.eaten);
            BlueEater.animate(blue.eaten, blue.pos);
            if (blue.state == BlueState.CHARGING && blue.credited >= cfg.blueReadyDamage) blue.state = BlueState.READY;
            if (blue.state != before || blue.age % 2 == 0) broadcast(blue);
        }
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
                if (hurter.hurt(e, blue.owner, damage)) blue.credited += BlueRedRules.credit(healthBefore, living.getHealth());
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
        Blue blue = blueOf(player);
        if (blue == null) return;
        BLUES.remove(blue);
        BlueEater.retireAll(blue.eaten);
        send(blue.level, blue.pos, new BlueRedPayload(BlueRedPayload.BLUE_GONE, blue.owner.getId(), -1, 0, blue.pos.x, blue.pos.y, blue.pos.z, 0, 0, 0, 0));
    }

    public static void clearAll() {
        for (Blue blue : BLUES) BlueEater.retireAll(blue.eaten);
        BLUES.clear();
    }
}
