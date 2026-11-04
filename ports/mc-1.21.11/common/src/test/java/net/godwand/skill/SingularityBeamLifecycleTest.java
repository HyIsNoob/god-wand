package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SingularityBeamLifecycleTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { SpatialDisplacementManager.clearRuntimeState(); }

    @Test void collapseDiscardsReloadedBeamAndRetiresItsTracking() throws Exception {
        var world=mock(ServerLevel.class);
        var old=mock(BlockDisplay.class);
        var loaded=mock(BlockDisplay.class);
        var uuid=UUID.randomUUID();
        when(old.getUUID()).thenReturn(uuid);
        when(old.level()).thenReturn(world);
        when(old.isAlive()).thenReturn(false);
        when(world.getEntity(uuid)).thenReturn(loaded);
        SpatialDisplacementManager.trackEntity(uuid,old);
        var task=new ShockwaveSlicer.SingularityTask(world,BlockPos.ZERO,16,Vec3.ZERO);
        task.beams.put(BlockPos.ZERO,old);
        var clear=ShockwaveSlicer.class.getDeclaredMethod("clearBeams",ShockwaveSlicer.SingularityTask.class);
        clear.setAccessible(true);
        clear.invoke(null,task);
        verify(loaded).discard();
        assertTrue(task.beams.isEmpty());
        assertNull(SpatialDisplacementManager.getActiveEntity(uuid));
    }
}
