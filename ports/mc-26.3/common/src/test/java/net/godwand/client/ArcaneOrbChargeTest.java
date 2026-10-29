package net.godwand.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArcaneOrbChargeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void chargingKeepsSoundAndMeterWithoutBroadcastingLooseParticles() {
        var world=mock(net.minecraft.server.level.ServerLevel.class);
        var player=mock(net.minecraft.server.level.ServerPlayer.class);
        var stack=mock(net.minecraft.world.item.ItemStack.class);
        var item=mock(net.godwand.item.GodWandItem.class,CALLS_REAL_METHODS);
        try(var modes=mockStatic(net.godwand.item.GodWandItem.class,CALLS_REAL_METHODS)) {
            modes.when(() -> net.godwand.item.GodWandItem.getSkillMode(stack)).thenReturn(net.godwand.item.GodWandItem.MODE_ARCANE_ORB);
            for(int ticks:new int[]{0,2,8,20,45,64})item.onUseTick(world,player,stack,72000-ticks);
            verify(player,times(6)).sendOverlayMessage(any(net.minecraft.network.chat.Component.class));
            var calls=mockingDetails(world).getInvocations();
            assertFalse(calls.stream().anyMatch(call -> call.getMethod().getName().equals("sendParticles")));
            assertTrue(calls.stream().anyMatch(call -> call.getMethod().getName().equals("playSound")));
        }
    }

    @Test void glowFadesSmoothlyFromBrightCoreToTransparentEdge() {
        assertEquals(1, ArcaneOrbModel.chargeGlowAlpha(0));
        float previous = 1;
        for (int i = 1; i <= 100; i++) {
            float alpha = ArcaneOrbModel.chargeGlowAlpha(i / 100f);
            assertTrue(alpha >= 0 && alpha <= previous);
            previous = alpha;
        }
        assertEquals(0, previous);
        assertEquals(0, ArcaneOrbModel.chargeGlowAlpha(2));
    }
}
