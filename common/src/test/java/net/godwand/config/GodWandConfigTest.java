package net.godwand.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GodWandConfigTest {
    @TempDir Path directory;

    @Test void skillSettingsKeepTierBoundariesZeroDamageAndGlobalScaling() {
        var previous=GodWandConfig.SERVER;
        try {
            var server=new GodWandConfig.Server();GodWandConfig.SERVER=server;
            server.damageMultiplier=.5f;server.cooldownMultiplier=2;
            server.singularityDamageMultipliers=new float[]{0,2,Float.NaN,999};
            server.singularityCooldownTicks=new int[]{0,40,-5,999};
            server.earthDrillDamageMultipliers=null;
            server.earthDrillCooldownTicks=new int[]{10};
            server.stoneCannonDamageMultipliers=new float[]{-1,Float.POSITIVE_INFINITY,3,4};
            server.supernovaDamageMultiplier=2;server.supernovaInstantKillHealthThreshold=0;
            server.validate();
            assertEquals(0,GodWandConfig.skillDamageMultiplier(1,1));
            assertEquals(1,GodWandConfig.skillDamageMultiplier(1,2));
            assertEquals(.5f,GodWandConfig.skillDamageMultiplier(1,3));
            assertEquals(.5f,GodWandConfig.skillDamageMultiplier(1,999));
            assertEquals(0,GodWandConfig.skillCooldownTicks(1,1));
            assertEquals(80,GodWandConfig.skillCooldownTicks(1,2));
            assertEquals(0,GodWandConfig.skillCooldownTicks(1,3));
            assertEquals(20,GodWandConfig.skillCooldownTicks(2,1));
            assertEquals(640,GodWandConfig.skillCooldownTicks(2,4));
            assertEquals(0,GodWandConfig.skillDamageMultiplier(4,1));
            assertEquals(.5f,GodWandConfig.skillDamageMultiplier(4,2));
            assertEquals(1,GodWandConfig.skillDamageMultiplier(3,1));
            assertEquals(0,server.supernovaInstantKillHealthThreshold);
            server.cooldownMultiplier=.1f;server.singularityCooldownTicks[0]=1;
            assertEquals(1,GodWandConfig.skillCooldownTicks(1,1));
            server.damageMultiplier=0;
            for(int skill=1;skill<=5;skill++)for(int tier=1;tier<=4;tier++)assertEquals(0,GodWandConfig.skillDamageMultiplier(skill,tier));
        } finally { GodWandConfig.SERVER=previous; }
    }

    @Test void silentGateSettingsAreBoundedAndCooldownIsRegistered() {
        var previous=GodWandConfig.SERVER;
        try {
            var s=new GodWandConfig.Server();GodWandConfig.SERVER=s;s.cooldownMultiplier=1;
            assertEquals(1200,GodWandConfig.skillCooldownTicks(7,1));
            s.gateCooldownTicks=-1;s.gateBeamDamage=Float.NaN;s.gateTargetRadius=0;s.gateTargetMode="bogus";
            s.gateBarrageTicks=0;s.gateWaitTicks=0;s.gateSkyCircles=0;s.gateCraterRadius=-3;s.validate();
            assertEquals(0,s.gateCooldownTicks);assertEquals(80,s.gateBeamDamage);assertEquals(8,s.gateTargetRadius);
            assertEquals("hostile",s.gateTargetMode);assertEquals(60,s.gateBarrageTicks);assertEquals(40,s.gateWaitTicks);
            assertEquals(8,s.gateSkyCircles);assertEquals(0,s.gateCraterRadius);
            s.gateCooldownTicks=999999;s.gateBeamDamage=2_000_000;s.gateTargetRadius=999;s.gateTargetMode="all";
            s.gateBarrageTicks=9999;s.gateWaitTicks=9999;s.gateSkyCircles=999;s.gateCraterRadius=99;s.validate();
            assertEquals(72000,s.gateCooldownTicks);assertEquals(1_000_000,s.gateBeamDamage);assertEquals(256,s.gateTargetRadius);
            assertEquals("all",s.gateTargetMode);assertEquals(600,s.gateBarrageTicks);assertEquals(1200,s.gateWaitTicks);
            assertEquals(256,s.gateSkyCircles);assertEquals(6,s.gateCraterRadius);
            s.gateTargetMode=null;s.gateBeamDamage=-5;s.validate();
            assertEquals("hostile",s.gateTargetMode);assertEquals(0,s.gateBeamDamage);
        } finally { GodWandConfig.SERVER=previous; }
    }

    @Test void olderPacksGainPerSkillAndChargeTierSettingsWithoutLosingExistingValues() throws Exception {
        Path file=directory.resolve("server.json");
        Files.writeString(file,"{\"damageMultiplier\":0.5,\"atomicDamage\":123,\"earthDrillTerrainDamage\":false}");
        GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server());
        var saved=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(3,saved.getAsJsonArray("singularityDamageMultipliers").size());
        assertEquals(4,saved.getAsJsonArray("earthDrillCooldownTicks").size());
        assertEquals(4,saved.getAsJsonArray("stoneCannonDamageMultipliers").size());
        assertEquals(900,saved.get("supernovaCooldownTicks").getAsInt());
        assertEquals(1200,saved.get("atomicCooldownTicks").getAsInt());
        assertEquals(0.5,saved.get("damageMultiplier").getAsDouble());
        assertEquals(123,saved.get("atomicDamage").getAsInt());
        assertFalse(saved.get("earthDrillTerrainDamage").getAsBoolean());
    }

    @Test void upgradesOldConfigOnDiskWithoutReplacingUserValuesOrUnknownKeys() throws Exception {
        Path file=directory.resolve("godwand-server.json");
        Files.writeString(file,"{\"damageMultiplier\":2.5,\"supernovaTerrainDamage\":false,\"packNote\":\"keep\"}");
        GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server());
        var saved=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(saved.has("atomicTerrainDamage"));
        assertTrue(saved.has("earthDrillBlockLift"));
        assertEquals(2.5,saved.get("damageMultiplier").getAsDouble());
        assertFalse(saved.get("supernovaTerrainDamage").getAsBoolean());
        assertEquals("keep",saved.get("packNote").getAsString());
        String upgraded=Files.readString(file);
        GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server());
        assertEquals(upgraded,Files.readString(file));
    }

    @Test void terrainPresetsDisableFugaBlockDamageWhenLoaded() throws Exception {
        var previous = GodWandConfig.SERVER;
        try {
            Path file = directory.resolve("godwand-server.json");
            for (String preset : new String[]{"preserve-terrain.json", "reversible-block-effects.json"}) {
                Path source = Path.of("..", "docs", "config-presets", preset);
                Files.writeString(file, Files.readString(source));
                GodWandConfig.loadServer(directory);
                assertFalse(GodWandConfig.SERVER.fugaTerrainDamage, preset);
                // Every skill's block damage, so a new skill cannot be left out of the presets again (Fuga 1.4.3, Silent Gate 1.5.0).
                for (var field : GodWandConfig.Server.class.getFields())
                    if (field.getName().endsWith("TerrainDamage")) assertFalse(field.getBoolean(GodWandConfig.SERVER), preset + " " + field.getName());
            }
        } finally {
            GodWandConfig.SERVER = previous;
        }
    }

    @Test void atomicDamageDefaultsMigrateFromPartialConfigsAndRejectInvalidValues() throws Exception {
        Path file=directory.resolve("server.json"); Files.writeString(file,"{\"atomicTerrainRadius\":32}");
        var server=GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server());
        assertEquals(9999,server.atomicDamage); assertEquals(15,server.atomicShockwaveDamage);
        server.atomicDamage=Float.NaN; server.atomicShockwaveDamage=Float.POSITIVE_INFINITY; server.validate();
        assertEquals(9999,server.atomicDamage); assertEquals(15,server.atomicShockwaveDamage);
        server.atomicDamage=-1; server.atomicShockwaveDamage=2_000_000; server.validate();
        assertEquals(0,server.atomicDamage); assertEquals(1_000_000,server.atomicShockwaveDamage);
    }

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
    void brokenServerConfigMustNotEnableDefaultTerrainDestruction() throws Exception {
        Path file=directory.resolve("server.json");
        Files.writeString(file,"{\"atomicTerrainDamage\":false,broken}");
        assertThrows(IllegalStateException.class,() -> GodWandConfig.read(file,GodWandConfig.Server.class,new GodWandConfig.Server()));
        assertTrue(Files.readString(file).contains("false"));
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

    @Test
    void wandSkinAutoSelectsThematicModelOrRespectsManualOverride() {
        var client = new GodWandConfig.Client();
        client.wandSkin = WandSkin.AUTO;
        assertEquals(WandSkin.DEFAULT, client.getActiveSkin(0));
        assertEquals(WandSkin.DEFAULT, client.getActiveSkin(1));
        assertEquals(WandSkin.AQUA_HEARTIA, client.getActiveSkin(2));
        assertEquals(WandSkin.DEFAULT, client.getActiveSkin(3));
        assertEquals(WandSkin.AQUA_HEARTIA, client.getActiveSkin(4));
        assertEquals(WandSkin.SHADOW_BLADE, client.getActiveSkin(5));
        assertEquals(WandSkin.SILENT_WITCH, client.getActiveSkin(7));

        client.wandSkin = WandSkin.AQUA_HEARTIA;
        assertEquals(WandSkin.AQUA_HEARTIA, client.getActiveSkin(0));
        assertEquals(WandSkin.AQUA_HEARTIA, client.getActiveSkin(3));

        client.wandSkin = WandSkin.SHADOW_BLADE;
        assertEquals(WandSkin.SHADOW_BLADE, client.getActiveSkin(1));
    }

    @Test
    void dimensionBlacklistChecksCorrectly() {
        var server = new GodWandConfig.Server();
        assertTrue(server.isDimensionAllowed("minecraft:overworld"));
        server.dimensionBlacklist.add("minecraft:the_end");
        assertTrue(server.isDimensionAllowed("minecraft:overworld"));
        assertFalse(server.isDimensionAllowed("minecraft:the_end"));
    }
}
