package net.godwand.client;
import net.godwand.network.SkillCooldownPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SkillCooldownClientTest {
 @Test void dimensionTransferKeepsTimersHudReadsDontAdvanceTimeAndNewSessionsReset() {
  var client=mock(Minecraft.class);client.level=mock(ClientLevel.class);client.player=mock(LocalPlayer.class);
  when(client.player.getHealth()).thenReturn(20f);when(client.getConnection()).thenReturn(mock(ClientPacketListener.class));
  try(var minecraft=mockStatic(Minecraft.class)) {
   minecraft.when(Minecraft::getInstance).thenReturn(client);
   SkillCooldownClient.tick(client);SkillCooldownClient.receive(new SkillCooldownPayload(5,100,80,false));
   assertFalse(SkillCooldownClient.canUse(5));assertTrue(SkillCooldownClient.canUse(0));
   for(int i=0;i<100;i++)assertEquals(80,SkillCooldownClient.remaining(5));
   for(int i=0;i<20;i++)SkillCooldownClient.tick(client);assertEquals(60,SkillCooldownClient.remaining(5));
   client.player=mock(LocalPlayer.class);when(client.player.getHealth()).thenReturn(20f);
   SkillCooldownClient.tick(client);assertEquals(59,SkillCooldownClient.remaining(5));
   when(client.isPaused()).thenReturn(true);for(int i=0;i<20;i++)SkillCooldownClient.tick(client);assertEquals(59,SkillCooldownClient.remaining(5));
   SkillCooldownClient.receive(new SkillCooldownPayload(1,200,100,true));assertTrue(SkillCooldownClient.canUse(1));
   SkillCooldownClient.receive(new SkillCooldownPayload(1,200,90,false));assertFalse(SkillCooldownClient.canUse(1));
   when(client.getConnection()).thenReturn(null);client.player=null;client.level=null;SkillCooldownClient.tick(client);assertEquals(0,SkillCooldownClient.remaining(5));
  }
 }
}
