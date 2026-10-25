package net.godwand.item;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupernovaUsePoseTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void supernovaUsesSpearPose() {
        // Exercise the real wand methods without registering an item in the frozen vanilla registry.
        var item = mock(GodWandItem.class, CALLS_REAL_METHODS);
        var stack = new ItemStack(Items.STICK);
        GodWandItem.setSkillMode(stack, GodWandItem.MODE_SUPERNOVA);
        assertEquals(UseAnim.SPEAR, item.getUseAnimation(stack));
        assertEquals(72000, item.getUseDuration(stack, null));
        for (int skill : new int[]{1, 2, 4}) {
            GodWandItem.setSkillMode(stack, skill);
            assertEquals(UseAnim.BOW, item.getUseAnimation(stack));
        }
    }
}
