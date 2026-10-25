package net.godwand.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArcaneOrbSyncTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void remoteOrbReceivesChargedLevelAndBoundsInvalidValues() {
        var world = mock(Level.class);
        var serverOrb = new ArcaneOrbEntity(EntityType.SNOWBALL, world);
        var clientOrb = new ArcaneOrbEntity(EntityType.SNOWBALL, world);
        serverOrb.setChargeLevel(3);
        var update = serverOrb.getEntityData().packDirty();
        assertNotNull(update, "Charging must produce a network update");
        clientOrb.getEntityData().assignValues(update);
        assertEquals(3, clientOrb.getChargeLevel());
        serverOrb.setChargeLevel(99);
        assertEquals(3, serverOrb.getChargeLevel());
        serverOrb.setChargeLevel(0);
        assertEquals(1, serverOrb.getChargeLevel());
    }
}
