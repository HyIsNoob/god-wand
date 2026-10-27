package net.godwand.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtomicMovementTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void onlyTheCurrentCastersDetonatedSpellCanReleaseMovement() throws Exception {
        var player=mock(ServerPlayer.class);var world=mock(ServerLevel.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());when(player.serverLevel()).thenReturn(world);
        when(player.isAlive()).thenReturn(true);when(player.position()).thenReturn(Vec3.ZERO);
        var type=Class.forName("net.godwand.skill.AtomicManager$Task");
        var constructor=type.getDeclaredConstructor(ServerPlayer.class,InteractionHand.class);constructor.setAccessible(true);
        Object task=constructor.newInstance(player,InteractionHand.MAIN_HAND);
        var started=type.getDeclaredField("started");started.setAccessible(true);started.setLong(task,1000);
        var release=type.getDeclaredField("releasedAt");release.setAccessible(true);release.setLong(task,1500);
        var field=AtomicManager.class.getDeclaredField("TASKS");field.setAccessible(true);
        @SuppressWarnings("unchecked") var tasks=(Map<UUID,Object>)field.get(null);
        tasks.put(player.getUUID(),task);
        try {
            when(world.getGameTime()).thenReturn(1500L);
            assertTrue(AtomicManager.movementLocked(player));
            assertFalse(AtomicManager.releaseMovement(player,1000));
            when(world.getGameTime()).thenReturn(1510L);
            assertFalse(AtomicManager.releaseMovement(player,999));
            when(player.isSpectator()).thenReturn(true);
            assertFalse(AtomicManager.releaseMovement(player,1000));
            when(player.isSpectator()).thenReturn(false);
            assertTrue(AtomicManager.releaseMovement(player,1000));
            assertFalse(AtomicManager.movementLocked(player));
            assertTrue(AtomicManager.invulnerable(player),"Skipping the camera must not change cast protection");
        } finally { tasks.clear(); }
    }
}
