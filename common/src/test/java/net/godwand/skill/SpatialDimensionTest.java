package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class SpatialDimensionTest {
    @Test void activePathStopsBeforeAnyBridgeOrTerrainWorkInABlacklistedDimension() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var previous=GodWandConfig.SERVER;
        try(var path=mockStatic(SpatialPathWalker.class,CALLS_REAL_METHODS);var bridge=mockStatic(SpatialBridge.class)) {
            GodWandConfig.SERVER=new GodWandConfig.Server();
            GodWandConfig.SERVER.dimensionBlacklist.add("minecraft:the_nether");
            var player=mock(ServerPlayer.class);var world=mock(ServerLevel.class);
            when(player.serverLevel()).thenReturn(world);when(world.dimension()).thenReturn(Level.NETHER);
            when(player.isAlive()).thenReturn(true);
            path.when(() -> SpatialPathWalker.isActive(player)).thenReturn(true);
            path.when(() -> SpatialPathWalker.hasGodWand(player)).thenReturn(true);
            path.when(() -> SpatialPathWalker.deactivate(player)).thenAnswer(c -> null);
            bridge.clearInvocations();
            SpatialPathWalker.tickPlayer(player);
            path.verify(() -> SpatialPathWalker.deactivate(player));
            bridge.verifyNoInteractions();
        } finally { GodWandConfig.SERVER=previous; }
    }
}
