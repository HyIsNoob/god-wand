package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.AtomicFxPayload;
import net.godwand.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class AtomicManager {
    private static final Map<UUID, Task> TASKS = new LinkedHashMap<>();
    private static final class Task {
        final ServerLevel world;
        final ServerPlayer caster;
        long started, releasedAt=-1;
        final InteractionHand hand;
        final ItemStack wand;
        Vec3 center;
        boolean armed, exploded, movementReleased;
        final Set<UUID> struck=new HashSet<>();
        final Map<UUID,Long> pressureHits=new HashMap<>();
        AtomicCrater crater;
        Task(ServerPlayer player,InteractionHand hand) {
            this.hand=hand; wand=player.getItemInHand(hand);
            world = player.level(); caster = player;
            started = world.getGameTime(); center = player.position();
        }
    }
    public static boolean invulnerable(ServerPlayer player) {
        Task task=TASKS.get(player.getUUID());
        return task!=null && player.isAlive() && task.world==player.level()
                && AtomicSequence.protectedDuringCast(AtomicSequence.castAge(task.world.getGameTime(),task.started,task.releasedAt));
    }
    public static boolean movementLocked(ServerPlayer player) {
        Task task=TASKS.get(player.getUUID());
        return task!=null && player.isAlive() && !player.isSpectator() && task.world==player.level()
                && !task.movementReleased && AtomicSequence.movementLocked(AtomicSequence.castAge(task.world.getGameTime(),task.started,task.releasedAt));
    }
    public static boolean releaseMovement(ServerPlayer player,long startedAt) {
        Task task=TASKS.get(player.getUUID());
        if(task==null||task.started!=startedAt||task.world!=player.level()||!player.isAlive()||player.isSpectator()||task.releasedAt<0)return false;
        double age=AtomicSequence.castAge(task.world.getGameTime(),task.started,task.releasedAt);
        if(age<AtomicSequence.DETONATION||age>=AtomicSequence.END)return false;
        task.movementReleased=true;
        return true;
    }
    public static boolean beginCharge(ServerPlayer player,InteractionHand hand) {
        if(player.isPassenger() || !GodWandConfig.SERVER.isDimensionAllowed(player.level().dimension().identifier().toString()))return false;
        if (TASKS.containsKey(player.getUUID()) || TASKS.values().stream().filter(t -> t.world == player.level()).count() >= 4) return false;
        Task task = new Task(player,hand);
        TASKS.put(player.getUUID(), task);
        broadcast(task, AtomicFxPayload.CHARGE);
        return true;
    }
    public static boolean arm(ServerPlayer player) {
        Task task = TASKS.get(player.getUUID());
        if (task == null || task.armed || task.world != player.level()) return false;
        task.center = player.position(); task.armed = true;
        task.started = task.world.getGameTime() - AtomicSequence.CHARGE_TICKS;
        task.crater = new AtomicCrater(BlockPos.containing(task.center).below(), GodWandConfig.SERVER.atomicTerrainDamage ? GodWandConfig.SERVER.atomicTerrainRadius : 0);
        broadcast(task, AtomicFxPayload.ARMED);
        return true;
    }
    public static void cancelCharge(ServerPlayer player) {
        Task task = TASKS.get(player.getUUID());
        if (task != null && !task.exploded) { broadcast(task, AtomicFxPayload.CANCEL); TASKS.remove(player.getUUID()); }
    }
    public static void releaseCharge(ServerPlayer player) {
        Task task=TASKS.get(player.getUUID());
        if(task==null||task.world!=player.level()||task.releasedAt>=0)return;
        if(!task.armed) { cancelCharge(player); return; }
        task.releasedAt=AtomicSequence.releaseTime(task.world.getGameTime(),task.started);
        broadcast(task,AtomicFxPayload.RELEASE);
    }
    public static void tick(ServerLevel world) {
        int terrainBudget = 8192;
        for (var iterator = TASKS.values().iterator(); iterator.hasNext();) {
            Task task = iterator.next();
            if (task.world != world) continue;
            long age = (long)AtomicSequence.castAge(world.getGameTime(),task.started,task.releasedAt);
            boolean invalidCaster = !task.caster.isAlive() || task.caster.hasDisconnected() || task.caster.level() != world || task.caster.isSpectator()
                    || task.caster.getItemInHand(task.hand)!=task.wand || GodWandItem.getSkillMode(task.wand)!=GodWandItem.MODE_ATOMIC
                    || task.armed&&task.releasedAt<0&&!task.caster.isUsingItem();
            if ((!task.exploded && invalidCaster) || (age >= AtomicSequence.END && (!task.exploded || task.crater.finished()))) {
                broadcast(task, AtomicFxPayload.CANCEL); iterator.remove(); continue;
            }
            if(movementLocked(task.caster)) {
                task.caster.setDeltaMovement(Vec3.ZERO);
                task.caster.fallDistance=0;
                if(task.caster.position().distanceToSqr(task.center)>.0001)
                    task.caster.connection.teleport(task.center.x,task.center.y,task.center.z,task.caster.getYRot(),task.caster.getXRot());
            }
            if (!task.armed) {
                var stack = task.caster.getUseItem();
                if (!task.caster.isUsingItem() || !(stack.getItem() instanceof GodWandItem) || GodWandItem.getSkillMode(stack) != GodWandItem.MODE_ATOMIC) {
                    broadcast(task, AtomicFxPayload.CANCEL); iterator.remove();
                } else task.center = task.caster.position();
                continue;
            }
            if (!task.exploded && age >= AtomicSequence.DETONATION) {
                task.exploded = true;
                broadcast(task, AtomicFxPayload.BURST);
            }
            if(task.exploded) {
                float radius=AtomicSequence.shockRadius(age);
                var area=new AABB(task.center.x-radius,task.center.y-14,task.center.z-radius,
                        task.center.x+radius,task.center.y+AtomicSequence.BEAM_HEIGHT,task.center.z+radius);
                for(LivingEntity entity:world.getEntitiesOfClass(LivingEntity.class,area,
                        e -> AtomicSequence.beamRadius(age)>0 && e!=task.caster && e.isAlive() && !(e instanceof Player p && (p.isCreative()||p.isSpectator())))) {
                    double distance=Math.hypot(entity.getX()-task.center.x,entity.getZ()-task.center.z);
                    float hit=damage(distance);
                    if(distance>AtomicSequence.beamRadius(age) || hit<=0 || !task.struck.add(entity.getUUID()))continue;
                    entity.hurtServer(world, world.damageSources().indirectMagic(task.caster,task.caster),hit);
                    Vec3 push=entity.position().subtract(task.center).normalize().scale(1.8*(1-distance/AtomicSequence.MAX_RADIUS));
                    entity.push(push.x,Math.min(.8,push.length()*.4),push.z); entity.hurtMarked=true;
                }
                if(AtomicSequence.pressure(age)>.15f&&GodWandConfig.SERVER.atomicShockwaveDamage>0&&GodWandConfig.SERVER.damageMultiplier>0) {
                    double reach=AtomicSequence.MAX_RADIUS+32;
                    var groundFront=new AABB(task.center.x-reach,task.center.y-2,task.center.z-reach,
                            task.center.x+reach,task.center.y+3,task.center.z+reach);
                    for(LivingEntity entity:world.getEntitiesOfClass(Mob.class,groundFront,LivingEntity::isAlive)) {
                        if(age-task.pressureHits.getOrDefault(entity.getUUID(),-100L)<10)continue;
                        double distance=Math.hypot(entity.getX()-task.center.x,entity.getZ()-task.center.z);
                        for(int wave=0;wave<4;wave++)if(AtomicSequence.pressureCrosses(age,wave,distance)) {
                            task.pressureHits.put(entity.getUUID(),age);
                            if(entity.hurtServer(world, world.damageSources().indirectMagic(task.caster,task.caster),GodWandConfig.SERVER.atomicShockwaveDamage * GodWandConfig.SERVER.damageMultiplier)) {
                                Vec3 outward=new Vec3(entity.getX()-task.center.x,0,entity.getZ()-task.center.z).normalize().scale(.45);
                                entity.push(outward.x,.12,outward.z); entity.hurtMarked=true;
                            }
                            break;
                        }
                    }
                }
                if(terrainBudget>0)terrainBudget-=task.crater.carve(world,terrainBudget,radius);
            }
        }
    }
    static float damage(double distance) { return (float) Math.max(0, GodWandConfig.SERVER.atomicDamage * GodWandConfig.SERVER.damageMultiplier * (1 - Math.clamp(distance, 0, AtomicSequence.MAX_RADIUS) / AtomicSequence.MAX_RADIUS)); }
    private static void broadcast(Task task, int phase) {
        Services.NETWORK.sendToNearby(task.world, task.center, 256,
                new AtomicFxPayload(task.caster.getId(), task.started, task.center.x, task.center.y, task.center.z, phase,task.releasedAt));
    }
    public static void clearAllTasks() { TASKS.clear(); }
}
