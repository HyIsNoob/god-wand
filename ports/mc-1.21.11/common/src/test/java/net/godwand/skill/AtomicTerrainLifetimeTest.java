package net.godwand.skill;

import net.godwand.item.GodWandItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtomicTerrainLifetimeTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void unfinishedTerrainStopsBeforeRetractionAndCannotKeepTheCastAlive() throws Exception {
        var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
        var wand=mock(ItemStack.class); var crater=mock(AtomicCrater.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.level()).thenReturn(world); when(player.isAlive()).thenReturn(true);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(wand);
        when(player.position()).thenReturn(Vec3.ZERO);
        var type=Class.forName("net.godwand.skill.AtomicManager$Task");
        var ctor=type.getDeclaredConstructor(ServerPlayer.class,InteractionHand.class); ctor.setAccessible(true);
        Object task=ctor.newInstance(player,InteractionHand.MAIN_HAND);
        for(String name:new String[]{"armed","exploded","movementReleased"}) {
            var field=type.getDeclaredField(name); field.setAccessible(true); field.setBoolean(task,true);
        }
        var release=type.getDeclaredField("releasedAt"); release.setAccessible(true); release.setLong(task,0);
        var terrain=type.getDeclaredField("crater"); terrain.setAccessible(true); terrain.set(task,crater);
        var field=AtomicManager.class.getDeclaredField("TASKS"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var tasks=(Map<UUID,Object>)field.get(null);
        tasks.put(player.getUUID(),task);
        @SuppressWarnings("unchecked") var platforms=(java.util.ServiceLoader<net.godwand.platform.IPlatformHelper>)mock(java.util.ServiceLoader.class);
        @SuppressWarnings("unchecked") var networks=(java.util.ServiceLoader<net.godwand.platform.INetworkHelper>)mock(java.util.ServiceLoader.class);
        when(platforms.findFirst()).thenReturn(java.util.Optional.of(mock(net.godwand.platform.IPlatformHelper.class)));
        var network=mock(net.godwand.platform.INetworkHelper.class);
        when(networks.findFirst()).thenReturn(java.util.Optional.of(network));
        try(var loaders=mockStatic(java.util.ServiceLoader.class);var modes=mockStatic(GodWandItem.class)) {
            loaders.when(() -> java.util.ServiceLoader.load(net.godwand.platform.IPlatformHelper.class)).thenReturn(platforms);
            loaders.when(() -> java.util.ServiceLoader.load(net.godwand.platform.INetworkHelper.class)).thenReturn(networks);
            modes.when(() -> GodWandItem.getSkillMode(wand)).thenReturn(GodWandItem.MODE_ATOMIC);
            when(world.getGameTime()).thenReturn(573L-AtomicSequence.WAIT_TICK);
            AtomicManager.tick(world);
            verify(crater).carve(eq(world),anyInt(),anyFloat(),anyLong());
            clearInvocations(crater);
            for(long age:new long[]{574,575,600,734,800,AtomicSequence.END,AtomicSequence.END+1200}) {
                when(world.getGameTime()).thenReturn(age-AtomicSequence.WAIT_TICK);
                AtomicManager.tick(world);
            }
            verify(crater,never()).carve(any(),anyInt(),anyFloat(),anyLong());
            assertTrue(tasks.isEmpty(),"Unfinished terrain must not retain a finished cast");
        } finally {tasks.clear();}
    }
}
