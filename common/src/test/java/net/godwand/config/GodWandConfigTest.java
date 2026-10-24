package net.godwand.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GodWandConfigTest {
    @TempDir Path directory;

    @Test
    void partialFilesKeepDefaultsAndInvalidNumbersAreBounded() throws Exception {
        Path file = directory.resolve("client.json");
        Files.writeString(file, "{\"cameraShake\":0,\"bloomDownsample\":999,\"bloomStrength\":-5}");
        var client = GodWandConfig.read(file, GodWandConfig.Client.class, new GodWandConfig.Client());
        client.validate();
        assertTrue(client.postEffects && client.impactFrames && client.targetMarker);
        assertEquals(0, client.cameraShake);
        assertEquals(8, client.bloomDownsample);
        assertEquals(0, client.bloomStrength);
        client.cameraShake = Float.NaN;
        client.validate();
        assertEquals(1, client.cameraShake);
        var server = new GodWandConfig.Server();
        server.supernovaTerrainRadius = Integer.MAX_VALUE;
        server.supernovaMaxDisplays = -1;
        server.cooldownMultiplier = Float.POSITIVE_INFINITY;
        server.validate();
        assertEquals(180, server.supernovaTerrainRadius);
        assertEquals(0, server.supernovaMaxDisplays);
        assertEquals(1, server.cooldownMultiplier);
    }

    @Test
    void defaultsAreWrittenAndBrokenUserFilesArePreserved() throws Exception {
        Path file = directory.resolve("client.json");
        var defaults = new GodWandConfig.Client();
        GodWandConfig.read(file, GodWandConfig.Client.class, defaults);
        assertTrue(Files.readString(file).contains("impactFrames"));
        Files.writeString(file, "{broken");
        assertSame(defaults, GodWandConfig.read(file, GodWandConfig.Client.class, defaults));
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void serverSettingsScaleCooldownAndTerrainWithoutChangingDamageRange() {
        var previous = GodWandConfig.SERVER;
        try {
            GodWandConfig.SERVER = new GodWandConfig.Server();
            GodWandConfig.SERVER.cooldownMultiplier = 2;
            GodWandConfig.SERVER.supernovaTerrainRadius = 32;
            GodWandConfig.SERVER.supernovaMaxDisplays = 256;
            var task = new net.godwand.skill.SupernovaManager.SupernovaTask(null, null,
                    net.minecraft.core.BlockPos.ZERO, net.minecraft.world.phys.Vec3.ZERO);
            assertEquals(40, GodWandConfig.cooldownTicks(20));
            assertEquals(32, task.terrainRadius);
            assertEquals(32, task.maxRadius);
            assertEquals(256, task.displayLimit);
            assertEquals(96, task.blastRadius);
        } finally {
            GodWandConfig.SERVER = previous;
        }
    }
}
