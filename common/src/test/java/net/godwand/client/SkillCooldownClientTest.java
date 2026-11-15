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
   when(client.player.getHealth()).thenReturn(0f);
   client.player=mock(LocalPlayer.class);when(client.player.getHealth()).thenReturn(20f);
   SkillCooldownClient.tick(client);assertEquals(0,SkillCooldownClient.remaining(5));assertEquals(0,SkillCooldownClient.remaining(1));
   when(client.getConnection()).thenReturn(null);client.player=null;client.level=null;SkillCooldownClient.tick(client);assertEquals(0,SkillCooldownClient.remaining(5));
  }
 }
 @Test void blueRedShowsLongerSlotAndRightClickChecksRed() {
  var client=mock(Minecraft.class);client.level=mock(ClientLevel.class);client.player=mock(LocalPlayer.class);
  when(client.player.getHealth()).thenReturn(20f);when(client.getConnection()).thenReturn(mock(ClientPacketListener.class));
  try(var minecraft=mockStatic(Minecraft.class)) {
   minecraft.when(Minecraft::getInstance).thenReturn(client);
   SkillCooldownClient.tick(client);
   SkillCooldownClient.receive(new SkillCooldownPayload(8,60,20,false));
   assertEquals(20,SkillCooldownClient.shown(8));assertTrue(SkillCooldownClient.canUse(8));
   SkillCooldownClient.receive(new SkillCooldownPayload(9,80,70,false));
   assertEquals(70,SkillCooldownClient.shown(8));assertEquals(70/80f,SkillCooldownClient.fraction(8),1e-6);
   assertFalse(SkillCooldownClient.canUse(8));
  }
 }
}
