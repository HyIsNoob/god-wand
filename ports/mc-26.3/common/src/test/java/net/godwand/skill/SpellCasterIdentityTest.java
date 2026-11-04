package net.godwand.skill;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SpellCasterIdentityTest {
 @Test void respawnedCasterRemainsExcludedButOtherPlayersAreTargets() {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var caster=mock(ServerPlayer.class);var respawned=mock(ServerPlayer.class);var other=mock(ServerPlayer.class);
  var id=java.util.UUID.randomUUID();when(caster.getUUID()).thenReturn(id);when(respawned.getUUID()).thenReturn(id);
  when(other.getUUID()).thenReturn(java.util.UUID.randomUUID());
  assertNotSame(caster,respawned);assertTrue(SpellDamage.isCaster(respawned,caster));
  assertFalse(SpellDamage.isCaster(other,caster));assertFalse(SpellDamage.isCaster(other,null));
 }
}
