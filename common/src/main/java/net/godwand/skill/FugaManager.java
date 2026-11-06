package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.FugaFxPayload;
import net.godwand.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    /** Craters whose rim is still settling after the cast ended; terrain work only, no caster state. */
    private record Settling(ServerLevel world,FugaCrater crater,long impactAt,List<Long> forced) {}
    private static final List<Settling> SETTLING=new ArrayList<>();
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
        final Set<UUID> struck=new HashSet<>();
        /** Chunks this cast force-loaded around its far impact; released once the crater work is done. */
        List<Long> forced=new ArrayList<>();
        /** Chunks still to force, nearest first: a few per tick, since forcing them all at once stalled the server. */
        final java.util.ArrayDeque<Long> toForce=new java.util.ArrayDeque<>();
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
        // Recoil: a short shove back along the shot line, never stronger than a knockback hit.
        Vec3 kick=player.getLookAngle().scale(-.55);
        player.setDeltaMovement(kick.x,Math.max(.12,kick.y*.3),kick.z);player.hurtMarked=true;
    }
    private static boolean invalid(Task task) {
        return !task.caster.isAlive() || task.caster.hasDisconnected() || task.caster.isSpectator()
                || task.caster.serverLevel()!=task.world || task.caster.getItemInHand(task.hand)!=task.wand
                || GodWandItem.getSkillMode(task.wand)!=GodWandItem.MODE_FUGA;
    }
    public static void tick(ServerLevel world) {
        // Every loader calls this world tick after all other skills, so it also pools what any skill's kills dropped.
        SpellDamage.poolExperience(world);
        int budget=32768;long deadline=System.nanoTime()+8_000_000L;
        for(var iterator=TASKS.values().iterator();iterator.hasNext();) {
            Task task=iterator.next();if(task.world!=world)continue;
            if(invalid(task) || task.phase==FugaFxPayload.CHARGE && !task.caster.isUsingItem()) {
                broadcast(task,FugaFxPayload.CANCEL);release(task.world,task.forced);iterator.remove();continue;
            }
            if(FugaSequence.movementLocked(task.phase)) {
                task.caster.setDeltaMovement(Vec3.ZERO);task.caster.fallDistance=0;
                if(task.caster.position().distanceToSqr(task.anchor)>.0001)
                    task.caster.connection.teleport(task.anchor.x,task.anchor.y,task.anchor.z,task.caster.getYRot(),task.caster.getXRot());
            }
            if(task.phase==FugaFxPayload.FIRE)advance(task);
            if(task.phase!=FugaFxPayload.IMPACT)continue;
            forceSome(world,task.toForce,task.forced,FORCE_PER_TICK);
            long age=world.getGameTime()-task.phaseAt;
            if(age>=FugaSequence.END_TICK) {
                broadcast(task,FugaFxPayload.END);iterator.remove();
                if(task.crater!=null && !task.crater.finished() && GodWandConfig.SERVER.fugaTerrainDamage)SETTLING.add(new Settling(world,task.crater,task.phaseAt,task.forced));
                else release(world,task.forced);
                continue;
            }
            if(age>=FugaSequence.ORB_TICKS) {
                damage(task,age);
                outflow(task,age);
            }
            // The ground heaves up into rock spires under the descending mass, before the crater tears them out.
            if(age>=8 && age<FugaSequence.ORB_TICKS-8 && budget>0 && task.crater!=null && GodWandConfig.SERVER.fugaTerrainDamage)
                budget-=task.crater.upheave(world,budget,age);
            // The ground sinks and buckles under the sphere as it presses down, just before the blast tears it out.
            if(age>=FugaSequence.ORB_TICKS-36 && age<FugaSequence.ORB_TICKS+4 && budget>0 && task.crater!=null && GodWandConfig.SERVER.fugaTerrainDamage)
                budget-=task.crater.press(world,budget,age);
            if(FugaSequence.terrainActive(age) && budget>0 && GodWandConfig.SERVER.fugaTerrainDamage)
                budget-=task.crater.carve(world,budget,deadline,age);
        }
        settle(world,budget,deadline);
    }
    private static void settle(ServerLevel world,int budget,long deadline) {
        for(var iterator=SETTLING.iterator();iterator.hasNext();) {
            var s=iterator.next();if(s.world()!=world)continue;
            double age=world.getGameTime()-s.impactAt();
            if(s.crater().finished() || !FugaSequence.terrainActive(age) || !GodWandConfig.SERVER.fugaTerrainDamage) {release(s.world(),s.forced());iterator.remove();continue;}
            if(budget>0)budget-=s.crater().carve(world,budget,deadline,age);
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
            task.origin=end;task.phase=FugaFxPayload.IMPACT;task.phaseAt=task.world.getGameTime()+FugaSequence.TRANSITION_TICKS;
            task.crater=new FugaCrater(BlockPos.containing(end),GodWandConfig.SERVER.fugaTerrainRadius);
            // The shot lands far from the caster, often past their loaded area: keep the whole crater and blast ring
            // loaded while it is carved, or it is left full of missing chunks.
            if(GodWandConfig.SERVER.fugaTerrainDamage)task.toForce.addAll(forceOrder(BlockPos.containing(end),FugaCrater.reach(GodWandConfig.SERVER.fugaTerrainRadius)));
            broadcast(task,FugaFxPayload.IMPACT);
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
    public static void clearAllTasks() {
        for(var task:TASKS.values())release(task.world,task.forced);
        for(var s:SETTLING)release(s.world(),s.forced());
        TASKS.clear();SETTLING.clear();
    }
    static final int FORCE_PER_TICK=2;
    /** Chunk keys within {@code radius} blocks of {@code center}, nearest first (the sphere lands on the first ones). */
    static List<Long> forceOrder(BlockPos center,int radius) {
        var keys=new ArrayList<long[]>();
        int r=(radius>>4)+1,cx=center.getX()>>4,cz=center.getZ()>>4;
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)
            if(x*x+z*z<=r*r+1)keys.add(new long[]{x*x+z*z,((long)(cx+x)<<32)|((cz+z)&0xFFFFFFFFL)});
        keys.sort(java.util.Comparator.comparingLong(k->k[0]));
        var order=new ArrayList<Long>();for(var k:keys)order.add(k[1]);
        return order;
    }
    /** Forces up to {@code count} queued chunks; remembers only those it changed, so release never unforces others'. */
    private static void forceSome(ServerLevel world,java.util.ArrayDeque<Long> queue,List<Long> forced,int count) {
        for(int i=0;i<count&&!queue.isEmpty();i++) {
            long key=queue.poll();
            if(world.setChunkForced((int)(key>>32),(int)key,true))forced.add(key);
        }
    }
    private static void release(ServerLevel world,List<Long> forced) {
        for(long key:forced)world.setChunkForced((int)(key>>32),(int)key,false);
        forced.clear();
    }
    /** The pillar's hot outflow rolling out over the ground (the shader aura): it keeps burning and shoving whatever
     *  it reaches, and sets fire to flammable things in its path, until the column fades. */
    private static void outflow(Task task,long age) {
        double visual=FugaSequence.visualAge(age);
        if(visual<40 || visual>=150)return;
        var p=task.position;
        double inner=FugaSequence.RADIUS*.6,reach=Math.min(230,FugaSequence.RADIUS*.75+10+(visual-40)*7);
        if(age%10==0) {
            var area=new AABB(p.x-reach,p.y-6,p.z-reach,p.x+reach,p.y+40,p.z+reach);
            float amount=GodWandConfig.SERVER.fugaDamage*GodWandConfig.SERVER.damageMultiplier*.08f;
            for(var entity:task.world.getEntitiesOfClass(LivingEntity.class,area,e->e!=task.caster && e.isAlive()
                    && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
                double dx=entity.getX()-p.x,dz=entity.getZ()-p.z,d=Math.hypot(dx,dz);
                if(d<inner || d>reach)continue;
                SpellDamage.hurt(entity,task.world.damageSources().indirectMagic(task.caster,task.caster),amount);
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(),100));
                entity.setDeltaMovement(entity.getDeltaMovement().add(dx/Math.max(1,d)*.6,.15,dz/Math.max(1,d)*.6));
                entity.hurtMarked=true;
            }
        }
        if(!GodWandConfig.SERVER.fugaTerrainDamage)return;
        // Only flammable things it touches catch: plants, wood, leaves. Loaded chunks only, protected blocks kept.
        var random=task.world.getRandom();
        for(int i=0;i<6;i++) {
            double a=random.nextDouble()*Math.PI*2,r=reach-random.nextDouble()*14;
            int x=net.minecraft.util.Mth.floor(p.x+Math.cos(a)*r),z=net.minecraft.util.Mth.floor(p.z+Math.sin(a)*r);
            var chunk=task.world.getChunkSource().getChunkNow(x>>4,z>>4);
            if(chunk==null)continue;
            int y=chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x&15,z&15);
            var top=new BlockPos(x,y,z);var state=chunk.getBlockState(top);
            if(!state.ignitedByLava())continue;
            var at=state.canBeReplaced()?top:top.above();
            var there=chunk.getBlockState(at);
            if(!there.canBeReplaced() || !net.godwand.spatial.SpatialDisplacementManager.canDisplace(task.world,at,there))continue;
            task.world.setBlock(at,net.minecraft.world.level.block.BaseFireBlock.getState(task.world,at),3);
        }
    }
}
