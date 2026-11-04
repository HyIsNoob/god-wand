package net.godwand.item;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WandSkillLockTest {
 @org.junit.jupiter.api.BeforeAll static void bootstrap() {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
 }
 @Test void lockSurvivesCopiesRejectsSelectionAndUnlockPreservesOtherData() {
  var wand=org.mockito.Mockito.mock(GodWandItem.class);
  var stack=new ItemStack(net.minecraft.core.Holder.direct((Item)wand));
  WandSkillLock.lock(stack,5);assertTrue(WandSkillLock.isLocked(stack));assertEquals(5,GodWandItem.getSkillMode(stack));
  GodWandItem.setSkillMode(stack,1);assertEquals(5,GodWandItem.getSkillMode(stack));
  var copy=stack.copy();assertTrue(WandSkillLock.isLocked(copy));
  WandSkillLock.lock(copy,2);assertEquals(2,GodWandItem.getSkillMode(copy));assertEquals(5,GodWandItem.getSkillMode(stack));
  WandSkillLock.unlock(copy);assertFalse(WandSkillLock.isLocked(copy));assertEquals(2,GodWandItem.getSkillMode(copy));
  GodWandItem.setSkillMode(copy,1);assertEquals(1,GodWandItem.getSkillMode(copy));
  assertThrows(IllegalArgumentException.class,()->WandSkillLock.lock(stack,6));assertEquals(5,GodWandItem.getSkillMode(stack));
 }
}
