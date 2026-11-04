package net.godwand.item;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupernovaUsePoseTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void supernovaKeepsChargingWithoutTheVanillaSpearPose() {
        // Exercise the real wand methods without registering an item in the frozen vanilla registry.
        var item = mock(GodWandItem.class, CALLS_REAL_METHODS);
        var stack = mock(ItemStack.class);
        try(var modes=mockStatic(GodWandItem.class,CALLS_REAL_METHODS)) {
        modes.when(() -> GodWandItem.getSkillMode(stack)).thenReturn(GodWandItem.MODE_SUPERNOVA);
        assertEquals(ItemUseAnimation.NONE, item.getUseAnimation(stack));
        assertEquals(72000, item.getUseDuration(stack, null));
        for (int skill : new int[]{1, 2, 4}) {
            modes.when(() -> GodWandItem.getSkillMode(stack)).thenReturn(skill);
            assertEquals(ItemUseAnimation.BOW, item.getUseAnimation(stack));
        }
        }
    }
}
