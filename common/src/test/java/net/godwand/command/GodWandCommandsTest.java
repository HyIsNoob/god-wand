package net.godwand.command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.godwand.item.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GodWandCommandsTest {
 @Test void permissionsAndCastingGuardProtectTheHeldWand() throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var dispatcher=new CommandDispatcher<CommandSourceStack>();GodWandCommands.register(dispatcher);
  var source=mock(CommandSourceStack.class);var player=mock(ServerPlayer.class);
  player.containerMenu=mock(AbstractContainerMenu.class);
  var stack=spy(new ItemStack(Items.STICK));when(stack.getItem()).thenReturn(mock(GodWandItem.class));
  when(source.getPlayerOrException()).thenReturn(player);when(player.getMainHandItem()).thenReturn(stack);
  assertThrows(CommandSyntaxException.class,()->dispatcher.execute("godwand lock atomic",source));
  assertFalse(WandSkillLock.isLocked(stack));
  when(source.hasPermission(2)).thenReturn(true);
  when(player.isUsingItem()).thenReturn(true);
  assertThrows(CommandSyntaxException.class,()->dispatcher.execute("godwand lock atomic",source));
  assertFalse(WandSkillLock.isLocked(stack));
  when(player.isUsingItem()).thenReturn(false);
  assertEquals(1,dispatcher.execute("godwand lock atomic",source));assertEquals(5,GodWandItem.getSkillMode(stack));
  assertTrue(WandSkillLock.isLocked(stack));
  assertEquals(1,dispatcher.execute("godwand unlock",source));assertFalse(WandSkillLock.isLocked(stack));
  assertEquals(1,dispatcher.execute("godwand lock fuga",source));assertEquals(GodWandItem.MODE_FUGA,GodWandItem.getSkillMode(stack));
  assertTrue(WandSkillLock.isLocked(stack));assertEquals(1,dispatcher.execute("godwand unlock",source));
  assertThrows(CommandSyntaxException.class,()->dispatcher.execute("godwand lock missing",source));
  when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
  assertThrows(CommandSyntaxException.class,()->dispatcher.execute("godwand lock atomic",source));
 }
 @Test void everySkillCanBeLocked() throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var dispatcher=new CommandDispatcher<CommandSourceStack>();GodWandCommands.register(dispatcher);
  var source=mock(CommandSourceStack.class);when(source.hasPermission(2)).thenReturn(true);
  var root=dispatcher.getRoot().getChild("godwand").getChild("lock");
  assertEquals(GodWandItem.TOTAL_MODES,root.getChildren().size(),"a new skill must be lockable");
 }
 @Test void resetCooldownsNeedsOwnerPermission() throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var dispatcher=new CommandDispatcher<CommandSourceStack>();GodWandCommands.register(dispatcher);
  var source=mock(CommandSourceStack.class);var player=mock(ServerPlayer.class);
  when(source.getPlayerOrException()).thenReturn(player);when(player.hasDisconnected()).thenReturn(true);
  when(source.hasPermission(2)).thenReturn(true);
  assertThrows(CommandSyntaxException.class,()->dispatcher.execute("godwand reset_cooldowns",source));
  when(source.hasPermission(4)).thenReturn(true);
  assertEquals(1,dispatcher.execute("godwand reset_cooldowns",source));
 }
}
