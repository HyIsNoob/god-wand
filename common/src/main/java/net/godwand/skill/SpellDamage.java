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

    public static boolean hurt(Entity target,DamageSource original,float amount) {
        if(!Float.isFinite(amount)||amount<=0)return false;
        // Keep PvP, creative immunity and player-protection hooks on the original damage path.
        if(target instanceof Player player)return player.hurt(original,amount);
        if(target instanceof LivingEntity living) {
            if(!(living.level() instanceof ServerLevel world))return false;
            var type=world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE);
            var source=new DamageSource(type,original.getDirectEntity(),original.getEntity());
            if(living instanceof EnderDragon dragon)return dragon.hurt(dragon.head,source,amount);
            return living.hurt(source,amount);
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
