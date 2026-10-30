package net.godwand.skill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import static org.mockito.Mockito.*;
class SpatialPlayerShieldTest {
 @Test void auraPushesOtherPlayersAndMobsButSkipsOwnerAndSpectators() {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var world=mock(ServerLevel.class);var owner=mock(ServerPlayer.class);var target=mock(ServerPlayer.class);
  var spectator=mock(ServerPlayer.class);var mob=mock(Mob.class);var armor=mock(ArmorStand.class);
  when(owner.serverLevel()).thenReturn(world);when(owner.position()).thenReturn(Vec3.ZERO);
  var bounds=new AABB(-.3,0,-.3,.3,1.8,.3);when(owner.getBoundingBox()).thenReturn(bounds);
  for(LivingEntity entity:List.of(owner,target,spectator,mob,armor)) {
   when(entity.isAlive()).thenReturn(true);when(entity.getBoundingBox()).thenReturn(bounds.move(1,0,0));
   when(entity.getDeltaMovement()).thenReturn(Vec3.ZERO);when(entity.getUUID()).thenReturn(UUID.randomUUID());
  }
  when(spectator.isSpectator()).thenReturn(true);
  when(target.getId()).thenReturn(1);when(mob.getId()).thenReturn(2);
  when(world.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any())).thenAnswer(call->{
   Predicate<LivingEntity> filter=call.getArgument(2);return List.of(owner,target,spectator,mob,armor).stream().filter(filter).toList();
  });
  SpatialPathWalker.repelMobs(owner);
  verify(target).setDeltaMovement(any(Vec3.class));verify(mob).setDeltaMovement(any(Vec3.class));
  verify(owner,never()).setDeltaMovement(any(Vec3.class));verify(spectator,never()).setDeltaMovement(any(Vec3.class));
  verify(armor,never()).setDeltaMovement(any(Vec3.class));
 }
}
