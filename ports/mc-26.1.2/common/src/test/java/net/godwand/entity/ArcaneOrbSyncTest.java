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
    @Test void projectileValueStorageRetainsChargeAndAgeAcrossReload() {
        var world = mock(Level.class);
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var source = new ArcaneOrbEntity(EntityType.SNOWBALL, world);
        source.setChargeLevel(3); source.tickCount = 137;
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries);
        source.addAdditionalSaveData(output);
        var restored = new ArcaneOrbEntity(EntityType.SNOWBALL, world);
        restored.readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, output.buildResult()));
        assertEquals(3, restored.getChargeLevel());
        assertEquals(137, restored.tickCount);
        var cannon = new StoneCannonProjectileEntity(EntityType.SNOWBALL, world);
        cannon.setChargeLevel(4); cannon.tickCount = 219;
        var cannonOutput = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries);
        cannon.addAdditionalSaveData(cannonOutput);
        var restoredCannon = new StoneCannonProjectileEntity(EntityType.SNOWBALL, world);
        restoredCannon.readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, cannonOutput.buildResult()));
        assertEquals(4, restoredCannon.getChargeLevel());
        assertEquals(219, restoredCannon.tickCount);
    }

}

