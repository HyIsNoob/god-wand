package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.FugaFxPayload;
import net.godwand.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server owns every flight segment, impact and terrain slice. */
public final class FugaManager {
    private static final Map<UUID,Task> TASKS=new LinkedHashMap<>();
    private static final class Task {
        final ServerPlayer caster;
        final ServerLevel world;
        final InteractionHand hand;
        final ItemStack wand;
        final long started;
        final Vec3 anchor;
        Vec3 origin,target,position;
        long phaseAt;
        int phase=FugaFxPayload.CHARGE;
        FugaCrater crater;
        boolean detonated;
        final Set<UUID> struck=new HashSet<>();
        Task(ServerPlayer player,InteractionHand hand) {
            caster=player;world=player.serverLevel();this.hand=hand;wand=player.getItemInHand(hand);
            started=phaseAt=world.getGameTime();anchor=origin=position=player.position();
            target=FugaSequence.launchTarget(player.getEyePosition(),player.getLookAngle());
        }
    }
    private FugaManager() {}
    public static boolean beginCharge(ServerPlayer player,InteractionHand hand) {
        if(!player.isAlive() || player.isSpectator() || player.isPassenger()
                || !GodWandConfig.SERVER.isDimensionAllowed(player.level().dimension().location().toString())
                || TASKS.containsKey(player.getUUID()) || TASKS.values().stream().filter(t->t.world==player.serverLevel()).count()>=4)return false;
        if(!(player.getItemInHand(hand).getItem() instanceof GodWandItem)
                || GodWandItem.getSkillMode(player.getItemInHand(hand))!=GodWandItem.MODE_FUGA)return false;
        var task=new Task(player,hand);TASKS.put(player.getUUID(),task);broadcast(task,FugaFxPayload.CHARGE);
        task.world.playSound(null,task.anchor.x,task.anchor.y,task.anchor.z,SoundEvents.BLAZE_AMBIENT,SoundSource.PLAYERS,1.5f,.65f);
        return true;
    }
    public static boolean movementLocked(ServerPlayer player) {
        Task task=TASKS.get(player.getUUID());
        return task!=null && FugaSequence.movementLocked(task.phase) && player.isAlive() && !player.isSpectator() && task.world==player.serverLevel();
    }
    public static boolean invulnerable(ServerPlayer player) {
        Task task=TASKS.get(player.getUUID());return task!=null && !invalid(task);
    }
    public static void cancelCharge(ServerPlayer player) {
        Task task=TASKS.remove(player.getUUID());if(task!=null)broadcast(task,FugaFxPayload.CANCEL);
    }
    public static void releaseCharge(ServerPlayer player) {
        Task task=TASKS.get(player.getUUID());
        if(task==null || task.phase!=FugaFxPayload.CHARGE)return;
        if(invalid(task) || !FugaSequence.ready(task.world.getGameTime(),task.started)) {cancelCharge(player);return;}
        task.origin=task.position=player.getEyePosition();
        task.target=FugaSequence.launchTarget(task.origin,player.getLookAngle());
        task.phase=FugaFxPayload.FIRE;task.phaseAt=task.world.getGameTime();broadcast(task,FugaFxPayload.FIRE);
        task.world.playSound(null,task.origin.x,task.origin.y,task.origin.z,SoundEvents.FIRECHARGE_USE,SoundSource.PLAYERS,3,.6f);
    }
    private static boolean invalid(Task task) {
        return !task.caster.isAlive() || task.caster.hasDisconnected() || task.caster.isSpectator()
                || task.caster.serverLevel()!=task.world || task.caster.getItemInHand(task.hand)!=task.wand
                || GodWandItem.getSkillMode(task.wand)!=GodWandItem.MODE_FUGA;
    }
    public static void tick(ServerLevel world) {
        int budget=32768;long deadline=System.nanoTime()+8_000_000L;
        for(var iterator=TASKS.values().iterator();iterator.hasNext();) {
            Task task=iterator.next();if(task.world!=world)continue;
            if(invalid(task) || task.phase==FugaFxPayload.CHARGE && !task.caster.isUsingItem()) {
                broadcast(task,FugaFxPayload.CANCEL);iterator.remove();continue;
            }
            if(FugaSequence.movementLocked(task.phase)) {
                task.caster.setDeltaMovement(Vec3.ZERO);task.caster.fallDistance=0;
                if(task.caster.position().distanceToSqr(task.anchor)>.0001)
                    task.caster.connection.teleport(task.anchor.x,task.anchor.y,task.anchor.z,task.caster.getYRot(),task.caster.getXRot());
                if(world.getGameTime()-task.started==FugaSequence.CHARGE_TICKS)
                    world.playSound(null,task.anchor.x,task.anchor.y,task.anchor.z,SoundEvents.BLAZE_SHOOT,SoundSource.PLAYERS,1,.7f);
            }
            if(task.phase==FugaFxPayload.FIRE)advance(task);
            if(task.phase!=FugaFxPayload.IMPACT)continue;
            long age=world.getGameTime()-task.phaseAt;
            if(age>=FugaSequence.END_TICK) {broadcast(task,FugaFxPayload.END);iterator.remove();continue;}
            if(age>=FugaSequence.ORB_TICKS) {
                if(!task.detonated) {
                    task.detonated=true;
                    world.playSound(null,task.position.x,task.position.y,task.position.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,6,.55f);
                }
                damage(task,age);
            }
            if(FugaSequence.terrainActive(age) && budget>0 && GodWandConfig.SERVER.fugaTerrainDamage)
                budget-=task.crater.carve(world,budget,deadline,age);
        }
    }
    private static void advance(Task task) {
        Vec3 from=task.position;
        Vec3 desired=FugaSequence.arrowPosition(task.origin,task.target,task.world.getGameTime()-task.phaseAt);
        Vec3 end=loadedEndpoint(task.world,from,desired);
        boolean boundary=end.distanceToSqr(desired)>1e-12;
        var block=task.world.clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,task.caster));
        boolean collided=block.getType()!=HitResult.Type.MISS;
        if(collided)end=block.getLocation();
        double nearest=from.distanceToSqr(end);
        for(var entity:task.world.getEntities(task.caster,new AABB(from,end).inflate(1),e->e.isAlive() && e.isPickable())) {
            var box=entity.getBoundingBox().inflate(.5);
            var hit=box.contains(from)?Optional.of(from):box.clip(from,end);
            if(hit.isPresent() && from.distanceToSqr(hit.get())<=nearest) {
                end=hit.get();nearest=from.distanceToSqr(end);collided=true;
            }
        }
        task.position=end;
        if(collided || boundary || end.distanceToSqr(task.target)<1e-6) {
            var ground=task.world.getChunkSource().getChunkNow(net.minecraft.util.Mth.floor(end.x)>>4,net.minecraft.util.Mth.floor(end.z)>>4);
            if(ground!=null) {
                int surface=ground.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,net.minecraft.util.Mth.floor(end.x)&15,net.minecraft.util.Mth.floor(end.z)&15)+1;
                end=new Vec3(end.x,Math.max(task.world.getMinBuildHeight(),surface),end.z);task.position=end;
            }
            task.origin=end;task.phase=FugaFxPayload.IMPACT;task.phaseAt=task.world.getGameTime();
            task.crater=new FugaCrater(BlockPos.containing(end),GodWandConfig.SERVER.fugaTerrainRadius);
            broadcast(task,FugaFxPayload.IMPACT);
            task.world.playSound(null,end.x,end.y,end.z,SoundEvents.BLAZE_SHOOT,SoundSource.PLAYERS,3,.5f);
        }
    }
    /** Clip to the first unloaded chunk without asking a chunk manager to load it. */
    static Vec3 loadedEndpoint(ServerLevel world,Vec3 from,Vec3 to) {
        if(!loaded(world,from))return from;
        var crossings=new TreeSet<Double>();crossings.add(0d);crossings.add(1d);
        double[] starts={from.x,from.y,from.z},ends={to.x,to.y,to.z};
        for(int axis=0;axis<3;axis++) {
            double delta=ends[axis]-starts[axis];if(Math.abs(delta)<1e-12)continue;
            int first=(int)Math.floor(Math.min(starts[axis],ends[axis])/16)+1;
            int last=(int)Math.floor(Math.max(starts[axis],ends[axis])/16);
            for(int plane=first;plane<=last;plane++) {
                double t=(plane*16d-starts[axis])/delta;
                if(t>0 && t<1)crossings.add(t);
            }
        }
        double previous=0;
        for(double next:crossings) {
            if(next<=previous)continue;
            if(!loaded(world,from.lerp(to,(previous+next)*.5)))
                return from.lerp(to,Math.max(0,previous-1e-8));
            previous=next;
        }
        return loaded(world,to)?to:from.lerp(to,1-1e-8);
    }
    private static boolean loaded(ServerLevel world,Vec3 point) {
        BlockPos pos=BlockPos.containing(point);
        return world.isInWorldBounds(pos) && world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;
    }
    private static void damage(Task task,long age) {
        double radius=FugaSequence.columnRadius(age);
        if(radius<=0)return;
        var p=task.position;
        var area=new AABB(p.x-radius,task.world.getMinBuildHeight(),p.z-radius,p.x+radius,p.y+FugaSequence.COLUMN_HEIGHT,p.z+radius);
        for(var entity:task.world.getEntitiesOfClass(LivingEntity.class,area,e->e!=task.caster && e.isAlive()
                && !(e instanceof Player player && (player.isCreative() || player.isSpectator()))))hit(task,entity,radius);
        for(var entity:SpellDamage.extraTargets(task.world,area))hit(task,entity,radius);
    }
    private static void hit(Task task,net.minecraft.world.entity.Entity entity,double radius) {
        UUID id=SpellDamage.identity(entity);
        if(Math.hypot(entity.getX()-task.position.x,entity.getZ()-task.position.z)>radius || task.struck.contains(id))return;
        float amount=GodWandConfig.SERVER.fugaDamage*GodWandConfig.SERVER.damageMultiplier;
        if(SpellDamage.hurt(entity,task.world.damageSources().indirectMagic(task.caster,task.caster),amount))task.struck.add(id);
    }
    private static void broadcast(Task task,int phase) {
        Vec3 point=task.origin;
        Services.NETWORK.sendToNearby(task.world,point,256,new FugaFxPayload(task.caster.getId(),task.started,
                point.x,point.y,point.z,task.target.x,task.target.y,task.target.z,phase,
                phase==FugaFxPayload.CANCEL || phase==FugaFxPayload.END?task.world.getGameTime():task.phaseAt));
    }
    public static void clearAllTasks() {TASKS.clear();}
}
