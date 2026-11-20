package net.godwand.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.godwand.GodWandMod;
import net.godwand.platform.Services;
import net.minecraft.util.Mth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GodWandConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Client CLIENT = new Client();
    public static Server SERVER = new Server();

    public static final class Client {
        public WandSkin wandSkin = WandSkin.AUTO;
        public boolean postEffects = true;
        public boolean impactFrames = true;
        public boolean cameraFov = true;
        public boolean skyBlackout = true;
        public boolean targetMarker = true;
        public boolean atomicCutscene = true;
        public boolean fugaCutscene = true;
        public boolean gateCutscene = true;
        /** Switch to third person while casting Blue/Red, and back after (owner, 2026-11-20). */
        public boolean blueRedThirdPerson = false;
        /** Purple's own cutscene switch (the screen's Cutscenes switch sets this and the others). */
        public boolean purpleCutscene = true;
        /** The mod's own sounds (godwand:*), 0..1, on top of the game's own volume sliders. */
        public float soundVolume = 1;
        /** The Blue/Red rings round the crosshair. */
        public boolean skillHud = true;
        /** The bare hand in first person for Purple and Fuga (off: the wand, as for the other skills). */
        public boolean bareHand = true;
        /** A fully charged Red throws its shooter back. */
        public boolean redRecoil = true;
        public boolean cutscenes() { return atomicCutscene && fugaCutscene && gateCutscene && purpleCutscene; }
        public void cutscenes(boolean on) { atomicCutscene = fugaCutscene = gateCutscene = purpleCutscene = on; }
        public float cameraShake = 1;
        public float bloomStrength = .85f;
        public int bloomDownsample = 4;

        public WandSkin getActiveSkin(int skillMode) {
            if (wandSkin != null && wandSkin != WandSkin.AUTO) return wandSkin;
            return switch (skillMode) {
                case 2, 4 -> WandSkin.AQUA_HEARTIA;
                case 5 -> WandSkin.SHADOW_BLADE;
                case 7 -> WandSkin.SILENT_WITCH;
                default -> WandSkin.DEFAULT;
            };
        }

        public void validate() {
            if (wandSkin == null) wandSkin = WandSkin.AUTO;
            cameraShake = finiteClamp(cameraShake, 0, 1, 1);
            bloomStrength = finiteClamp(bloomStrength, 0, 1.5f, .85f);
            bloomDownsample = Mth.clamp(bloomDownsample, 2, 8);
            soundVolume = finiteClamp(soundVolume, 0, 1, 1);
        }
    }

    public static final class Server {
        public int cooldownDefaultsVersion = 2;
        public float cooldownMultiplier = 1;
        public float damageMultiplier = 1;
        public int spatialPathCooldownTicks = 20;
        public int[] singularityCooldownTicks = {60,120,200};
        public int[] earthDrillCooldownTicks = {80,140,220,320};
        public int[] stoneCannonCooldownTicks = {40,80,140,220};
        public int supernovaCooldownTicks = 900;
        public int atomicCooldownTicks = 1200;
        public int fugaCooldownTicks = 1200;
        public int gateCooldownTicks = 1200;
        public float[] singularityDamageMultipliers = {1,1,1};
        public float[] earthDrillDamageMultipliers = {1,1,1,1};
        public float[] stoneCannonDamageMultipliers = {1,1,1,1};
        public float supernovaDamageMultiplier = 1;
        public float supernovaInstantKillHealthThreshold = 50;

        public boolean supernovaTerrainDamage = true;
        public boolean supernovaBlockLift = true;
        public boolean earthDrillTerrainDamage = true;
        public boolean earthDrillBlockLift = true;
        public boolean stoneCannonTerrainDamage = true;
        public int supernovaTerrainRadius = 120;
        public int supernovaMaxDisplays = 1800;
        public int atomicTerrainRadius = 98;
        public float atomicDamage = 9999;
        public float atomicShockwaveDamage = 15;
        public boolean atomicTerrainDamage = true;
        public boolean fugaTerrainDamage = true;
        public int fugaTerrainRadius = (int)net.godwand.skill.FugaSequence.RADIUS;
        public float fugaDamage = 9999;
        public float gateBeamDamage = 80;
        public int gateTargetRadius = 160;
        public String gateTargetMode = "hostile";
        public int gateBarrageTicks = 400;
        public int gateWaitTicks = 300;
        public int gateSkyCircles = 220;
        public boolean gateTerrainDamage = true;
        public int gateCraterRadius = 3;
        public int blueCooldownTicks = 60;
        public int redCooldownTicks = 80;
        public int purpleCooldownTicks = 1200;
        public float blueReadyDamage = 50;
        public int blueReadyKills = 3;
        public float blueCoreDamage = 20;
        public int bluePullRadius = 8;
        public int blueEatRadius = 5;
        public int blueChargeTicks = 160;
        /** How long a ready Blue hangs after it is let go (owner, 2026-11-20: 15 s was far too long). */
        public int blueAnchorTicks = 100;
        public int redChargeTicks = 50;
        public float redDamage = 500;
        public int redRange = 100;
        public int redTunnelRadius = 2;
        public int redCraterRadius = 5;
        public int purpleRadius = 48;
        public float purpleDamage = 9999;
        public boolean blueTerrainDamage = true;
        public boolean redTerrainDamage = true;
        public boolean purpleTerrainDamage = true;
        public List<String> dimensionBlacklist = new ArrayList<>();
        /** Master switch over every skill's own terrain damage (owner, 2026-11-20: one switch for servers). */
        public boolean terrainDamage = true;
        public boolean terrain(boolean skill) { return terrainDamage && skill; }
        /** Spells hurting other players. */
        public boolean pvp = true;
        /** Who spells hurt and push: "all"; "no_pets" (spares tamed, named and leashed mobs); "hostile" (monsters only). Other
         *  players follow pvp. */
        public String spellTargets = "no_pets";
        /** Red into an unready Blue (or an early Red) makes the small Purple; off, Red passes the Blue by. */
        public boolean smallPurple = true;
        public float smallPurpleStrength = 1;
        /** How far a ready Blue reaches out to catch a passing Red, in blocks. */
        public float purpleAssist = 2;
        /** The cutscene Purple needs a fully charged Red (otherwise any Red into a ready Blue). */
        public boolean purpleNeedsFullRed = true;

        public boolean isDimensionBlacklisted(String dimensionId) { return !isDimensionAllowed(dimensionId); }
        public boolean isDimensionAllowed(String dimensionId) { return dimensionBlacklist==null||!dimensionBlacklist.contains(dimensionId); }

        public void validate() {
            spatialPathCooldownTicks=Mth.clamp(spatialPathCooldownTicks,0,72000);
            supernovaCooldownTicks=Mth.clamp(supernovaCooldownTicks,0,72000);
            atomicCooldownTicks=Mth.clamp(atomicCooldownTicks,0,72000);
            fugaCooldownTicks=Mth.clamp(fugaCooldownTicks,0,72000);
            fugaTerrainRadius=Mth.clamp(fugaTerrainRadius,0,64);
            fugaDamage=finiteClamp(fugaDamage,0,1_000_000,9999);
            gateCooldownTicks=Mth.clamp(gateCooldownTicks,0,72000);
            gateBeamDamage=finiteClamp(gateBeamDamage,0,1_000_000,80);
            gateTargetRadius=Mth.clamp(gateTargetRadius,8,256);
            if(!"hostile".equals(gateTargetMode)&&!"all".equals(gateTargetMode))gateTargetMode="hostile";
            gateBarrageTicks=Mth.clamp(gateBarrageTicks,60,600);
            gateWaitTicks=Mth.clamp(gateWaitTicks,40,1200);
            gateSkyCircles=Mth.clamp(gateSkyCircles,8,256);
            gateCraterRadius=Mth.clamp(gateCraterRadius,0,6);
            blueCooldownTicks=Mth.clamp(blueCooldownTicks,0,72000);
            redCooldownTicks=Mth.clamp(redCooldownTicks,0,72000);
            purpleCooldownTicks=Mth.clamp(purpleCooldownTicks,0,72000);
            blueReadyDamage=finiteClamp(blueReadyDamage,1,1_000_000,50);
            blueReadyKills=Mth.clamp(blueReadyKills,1,1000);
            blueCoreDamage=finiteClamp(blueCoreDamage,0,1_000_000,20);
            bluePullRadius=Mth.clamp(bluePullRadius,2,16);
            blueEatRadius=Mth.clamp(blueEatRadius,0,8);
            blueChargeTicks=Mth.clamp(blueChargeTicks,20,600);
            blueAnchorTicks=Mth.clamp(blueAnchorTicks,20,1200);
            redChargeTicks=Mth.clamp(redChargeTicks,10,200);
            redDamage=finiteClamp(redDamage,0,1_000_000,500);
            redRange=Mth.clamp(redRange,8,160);
            redTunnelRadius=Mth.clamp(redTunnelRadius,0,4);
            redCraterRadius=Mth.clamp(redCraterRadius,0,8);
            purpleRadius=Mth.clamp(purpleRadius,0,64);
            purpleDamage=finiteClamp(purpleDamage,0,1_000_000,9999);
            singularityCooldownTicks=validatedCooldowns(singularityCooldownTicks,new int[]{60,120,200});
            earthDrillCooldownTicks=validatedCooldowns(earthDrillCooldownTicks,new int[]{80,140,220,320});
            stoneCannonCooldownTicks=validatedCooldowns(stoneCannonCooldownTicks,new int[]{40,80,140,220});
            singularityDamageMultipliers=validatedDamage(singularityDamageMultipliers,3);
            earthDrillDamageMultipliers=validatedDamage(earthDrillDamageMultipliers,4);
            stoneCannonDamageMultipliers=validatedDamage(stoneCannonDamageMultipliers,4);
            supernovaDamageMultiplier=finiteClamp(supernovaDamageMultiplier,0,1000,1);
            supernovaInstantKillHealthThreshold=finiteClamp(supernovaInstantKillHealthThreshold,0,1_000_000,50);
            cooldownMultiplier=finiteClamp(cooldownMultiplier,.1f,10,1);
            damageMultiplier=finiteClamp(damageMultiplier,0,100,1);
            supernovaTerrainRadius=Mth.clamp(supernovaTerrainRadius,16,180);
            supernovaMaxDisplays=Mth.clamp(supernovaMaxDisplays,0,1800);
            atomicTerrainRadius=Mth.clamp(atomicTerrainRadius,0,98);
            atomicDamage=finiteClamp(atomicDamage,0,1_000_000,9999);
            atomicShockwaveDamage=finiteClamp(atomicShockwaveDamage,0,1_000_000,15);
            if(dimensionBlacklist==null)dimensionBlacklist=new ArrayList<>();
        }
    }

    public static void loadClient(Path directory) {
        CLIENT = read(directory.resolve("godwand-client.json"), Client.class, new Client());
        CLIENT.validate();
    }

    public static void loadServer(Path directory) {
        SERVER = read(directory.resolve("godwand-server.json"), Server.class, new Server());
        SERVER.validate();
    }

    public static void saveClient() {
        saveClient(Services.PLATFORM.getConfigDirectory());
    }

    public static void saveClient(Path directory) {
        write(directory.resolve("godwand-client.json"), CLIENT);
    }

    public static void saveServer() {
        saveServer(Services.PLATFORM.getConfigDirectory());
    }

    public static void saveServer(Path directory) {
        write(directory.resolve("godwand-server.json"), SERVER);
    }

    static boolean upgradeCooldownDefaults(com.google.gson.JsonObject object) {
        int version=object.has("cooldownDefaultsVersion")?object.get("cooldownDefaultsVersion").getAsInt():0;
        if(version>=2)return false;
        // 1.6.0 test builds wrote a ready Blue's old 15 s hang; a value someone chose is kept.
        if(object.has("blueAnchorTicks")&&object.get("blueAnchorTicks").getAsInt()==300)object.addProperty("blueAnchorTicks",100);
        object.addProperty("cooldownDefaultsVersion",2);
        if(version>=1)return true;
        var defaults=new Server();
        String[] names={"singularityCooldownTicks","earthDrillCooldownTicks","stoneCannonCooldownTicks","supernovaCooldownTicks","atomicCooldownTicks"};
        Object[] old={new int[]{20,20,20},new int[]{35,35,35,35},new int[]{30,30,30,30},520,600};
        var next=JSON.toJsonTree(defaults).getAsJsonObject();
        for(int i=0;i<names.length;i++)if(object.has(names[i])&&object.get(names[i]).equals(JSON.toJsonTree(old[i])))
            object.add(names[i],next.get(names[i]));
        return true;
    }

    static <T> T read(Path file, Class<T> type, T defaults) {
        try {
            if (Files.exists(file)) {
                var object = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                boolean upgraded = type==Server.class && upgradeCooldownDefaults(object);
                T value = JSON.fromJson(object, type);
                if (value == null) throw new IllegalArgumentException("Expected a JSON object");

                for (var entry : JSON.toJsonTree(defaults).getAsJsonObject().entrySet()) {
                    if (!object.has(entry.getKey())) {
                        object.add(entry.getKey(), entry.getValue()); upgraded = true;
                    }
                }
                if (upgraded) {
                    // Replace only after a complete write so an interrupted upgrade preserves the old config.
                    Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName().toString(), ".tmp");
                    try {
                        Files.writeString(temporary, JSON.toJson(object) + System.lineSeparator());
                        try {
                            Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally { Files.deleteIfExists(temporary); }
                }
                return value;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.toJson(defaults) + System.lineSeparator());
        } catch (IOException | RuntimeException exception) {
            if(type==Server.class)throw new IllegalStateException("Cannot load server config "+file+"; refusing to enable default terrain destruction. Fix the file before restarting.",exception);
            GodWandMod.LOGGER.warn("Cannot load {}; using defaults. Existing file is preserved.", file, exception);
        }
        return defaults;
    }

    static <T> void write(Path file, T value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.toJson(value) + System.lineSeparator());
        } catch (IOException exception) {
            GodWandMod.LOGGER.error("Cannot save {}", file, exception);
        }
    }

    private static int[] validatedCooldowns(int[] values,int[] defaults) {
        int[] result=defaults.clone();
        if(values!=null)for(int i=0;i<Math.min(values.length,result.length);i++)result[i]=Mth.clamp(values[i],0,72000);
        return result;
    }
    private static float[] validatedDamage(float[] values,int count) {
        float[] result=new float[count];java.util.Arrays.fill(result,1);
        if(values!=null)for(int i=0;i<Math.min(values.length,count);i++)result[i]=finiteClamp(values[i],0,1000,1);
        return result;
    }
    private static int tierIndex(int level,int length) { return Mth.clamp(level-1,0,length-1); }
    public static int skillCooldownTicks(int skill,int level) {
        int ticks=switch(skill) {
            case 0 -> SERVER.spatialPathCooldownTicks;
            case 1 -> SERVER.singularityCooldownTicks[tierIndex(level,3)];
            case 2 -> SERVER.earthDrillCooldownTicks[tierIndex(level,4)];
            case 3 -> SERVER.supernovaCooldownTicks;
            case 4 -> SERVER.stoneCannonCooldownTicks[tierIndex(level,4)];
            case 5 -> SERVER.atomicCooldownTicks;
            case 6 -> SERVER.fugaCooldownTicks;
            case 7 -> SERVER.gateCooldownTicks;
            case 8 -> SERVER.blueCooldownTicks;
            case 9 -> SERVER.redCooldownTicks;
            default -> 0;
        };
        return ticks==0?0:cooldownTicks(ticks);
    }
    public static float skillDamageMultiplier(int skill,int level) {
        float multiplier=switch(skill) {
            case 1 -> SERVER.singularityDamageMultipliers[tierIndex(level,3)];
            case 2 -> SERVER.earthDrillDamageMultipliers[tierIndex(level,4)];
            case 3 -> SERVER.supernovaDamageMultiplier;
            case 4 -> SERVER.stoneCannonDamageMultipliers[tierIndex(level,4)];
            default -> 1;
        };
        return multiplier*SERVER.damageMultiplier;
    }

    public static int cooldownTicks(int ticks) {
        return Math.max(1, Math.round(ticks * SERVER.cooldownMultiplier));
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }
}
