package net.godwand.skill;

import net.godwand.GodWandMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Keeps configured spell damage out of ordinary boss caps without replacing their death hooks. */
public final class SpellDamage {
    static final ResourceKey<DamageType> TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("godwand","spell"));
    private static final String WORM="net.jelly.sandworm_mod.entity.IK.worm.";
    private static final Set<Class<?>> WARNED=new HashSet<>();

    public static boolean isCaster(Entity target, Entity caster) {
        return target != null && caster != null && target.getUUID().equals(caster.getUUID());
    }

    public static boolean hurt(Entity target,DamageSource original,float amount) {
        if(!Float.isFinite(amount)||amount<=0)return false;
        // Keep PvP, creative immunity and player-protection hooks on the original damage path.
        if(target instanceof Player player)return player.hurt(original,amount);
        if(target instanceof LivingEntity living) {
            if(!(living.level() instanceof ServerLevel world))return false;
            var type=world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE);
            var source=new DamageSource(type,original.getDirectEntity(),original.getEntity());
            boolean hit=living instanceof EnderDragon dragon?dragon.hurt(dragon.head,source,amount):living.hurt(source,amount);
            if(hit&&living.isDeadOrDying())noteKill(world,living);
            return hit;
        }
        Entity owner=wormOwner(target);
        if(owner==null)return false;
        try {
            // Sandworm has a separate blast counter, not LivingEntity health; keep its escape/drop logic.
            try { owner.getClass().getMethod("blastHit",int.class).invoke(owner,Math.max(1,(int)Math.min(Integer.MAX_VALUE,amount))); }
            catch(NoSuchMethodException legacy) { owner.getClass().getMethod("blastHit").invoke(owner); }
            return true;
        } catch(ReflectiveOperationException error) {
            warn(target,error);return false;
        }
    }

    /** 32-block columns where spells killed something recently: {kill height, last kill tick}. */
    private static final Map<ServerLevel,Map<Long,double[]>> KILLS=new WeakHashMap<>();
    private static void noteKill(ServerLevel world,Entity dead) {
        long key=(long)Math.floor(dead.getX()/32)<<32^((long)Math.floor(dead.getZ()/32)&0xFFFFFFFFL);
        KILLS.computeIfAbsent(world,w->new HashMap<>()).put(key,new double[]{dead.getY(),world.getGameTime()});
    }
    /** Mass spell kills leave thousands of experience orbs, each scanning block collisions and its neighbours every
     *  tick on every client (most of the frame on large packs). Once a second, where spells killed recently, pool them
     *  per 8-block cell into vanilla's few large orbs, keeping every point and roughly where it fell. */
    public static void poolExperience(ServerLevel world) {
        var spots=KILLS.get(world);
        long now=world.getGameTime();
        if(spots==null||spots.isEmpty()||now%20!=10)return;
        spots.values().removeIf(s->now-(long)s[1]>200);
        for(var spot:spots.entrySet()) {
            double x=(int)(spot.getKey()>>32)*32+16,z=(int)(long)spot.getKey()*32+16,y=spot.getValue()[0];
            var orbs=world.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,new AABB(x-40,y-48,z-40,x+40,y+48,z+40),Entity::isAlive);
            if(orbs.size()<24)continue;
            Map<Long,List<net.minecraft.world.entity.ExperienceOrb>> cells=new LinkedHashMap<>();
            for(var orb:orbs)
                cells.computeIfAbsent((long)Math.floor(orb.getX()/8)<<32^((long)Math.floor(orb.getZ()/8)&0xFFFFFFFFL),c->new ArrayList<>()).add(orb);
            for(var cell:cells.values()) {
                // A cell already pooled holds one orb: leave it be, or it would be re-spawned (and bounce) every second.
                if(cell.size()<2)continue;
                long total=0;
                for(var orb:cell) { total+=(long)orb.getValue()*((net.godwand.mixin.ExperienceOrbAccessor)orb).godwand$getCount(); orb.discard(); }
                var first=cell.get(0);
                var pooled=new net.minecraft.world.entity.ExperienceOrb(world,first.getX(),first.getY(),first.getZ(),(int)Math.min(Integer.MAX_VALUE,total));
                pooled.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                world.addFreshEntity(pooled);
            }
        }
    }
    private static boolean wormPart(Entity target) {
        // Also match subclasses used by proxies, without linking the optional mod at startup.
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals(WORM+"WormSegment")||type.getName().equals(WORM+"WormHeadSegment"))return true;
        return false;
    }
    private static Entity wormOwner(Entity target) {
        if(!wormPart(target))return null;
        try { return (Entity)target.getClass().getMethod("getOwner").invoke(target); }
        catch(ReflectiveOperationException error) { warn(target,error);return null; }
    }
    public static UUID identity(Entity target) {
        Entity owner=wormOwner(target);
        return (owner==null?target:owner).getUUID();
    }
    public static List<Entity> extraTargets(ServerLevel world,AABB area) {
        return world.getEntities((Entity)null,area,SpellDamage::wormPart);
    }
    public static void hurtExtras(ServerLevel world,AABB area,DamageSource source,ToDoubleFunction<Entity> damage) {
        Set<UUID> struck=new HashSet<>();
        for(Entity part:extraTargets(world,area)) {
            UUID id=identity(part);
            float amount=(float)damage.applyAsDouble(part);
            if(!struck.contains(id)&&hurt(part,source,amount))struck.add(id);
        }
    }
    private static void warn(Entity target,ReflectiveOperationException error) {
        if(WARNED.add(target.getClass()))GodWandMod.LOGGER.warn("God Wand could not damage optional boss part {}",target.getClass().getName(),error instanceof InvocationTargetException?error.getCause():error);
    }
}
