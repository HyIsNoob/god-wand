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
        }
    }

    public static final class Server {
        // Grouped by skill: the file is written in this order, a blank line between skills (see format).
        public int cooldownDefaultsVersion = 1;
        public float cooldownMultiplier = 1;
        public float damageMultiplier = 1;

        public int spatialPathCooldownTicks = 20;

        public int[] singularityCooldownTicks = {60,120,200};
        public float[] singularityDamageMultipliers = {1,1,1};

        public int[] earthDrillCooldownTicks = {80,140,220,320};
        public float[] earthDrillDamageMultipliers = {1,1,1,1};
        public boolean earthDrillTerrainDamage = true;
        public boolean earthDrillBlockLift = true;

        public int[] stoneCannonCooldownTicks = {40,80,140,220};
        public float[] stoneCannonDamageMultipliers = {1,1,1,1};
        public boolean stoneCannonTerrainDamage = true;

        public int supernovaCooldownTicks = 900;
        public float supernovaDamageMultiplier = 1;
        public float supernovaInstantKillHealthThreshold = 50;
        public boolean supernovaTerrainDamage = true;
        public boolean supernovaBlockLift = true;
        public int supernovaTerrainRadius = 120;
        public int supernovaMaxDisplays = 1800;

        public int atomicCooldownTicks = 1200;
        public float atomicDamage = 9999;
        public float atomicShockwaveDamage = 15;
        public boolean atomicTerrainDamage = true;
        public int atomicTerrainRadius = 98;

        public int fugaCooldownTicks = 1200;
        public float fugaDamage = 9999;
        public float fugaShockwaveDamageMultiplier = .08f;
        public boolean fugaIgniteEntities = true;
        public boolean fugaShockwaveKnockback = true;
        public boolean fugaSlowness = true;
        public boolean fugaTerrainDamage = true;
        public int fugaTerrainRadius = (int)net.godwand.skill.FugaSequence.RADIUS;

        public int gateCooldownTicks = 1200;
        public float gateBeamDamage = 80;
        public String gateTargetMode = "hostile";
        public int gateTargetRadius = 160;
        public int gateBarrageTicks = 400;
        public int gateWaitTicks = 300;
        public int gateSkyCircles = 220;
        public boolean gateTerrainDamage = true;
        public int gateCraterRadius = 3;

        public List<String> dimensionBlacklist = new ArrayList<>();

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
            fugaShockwaveDamageMultiplier=finiteClamp(fugaShockwaveDamageMultiplier,0,1000,.08f);
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
        if(object.has("cooldownDefaultsVersion")&&object.get("cooldownDefaultsVersion").getAsInt()>=1)return false;
        var defaults=new Server();
        String[] names={"singularityCooldownTicks","earthDrillCooldownTicks","stoneCannonCooldownTicks","supernovaCooldownTicks","atomicCooldownTicks"};
        Object[] old={new int[]{20,20,20},new int[]{35,35,35,35},new int[]{30,30,30,30},520,600};
        var next=JSON.toJsonTree(defaults).getAsJsonObject();
        for(int i=0;i<names.length;i++)if(object.has(names[i])&&object.get(names[i]).equals(JSON.toJsonTree(old[i])))
            object.add(names[i],next.get(names[i]));
        object.addProperty("cooldownDefaultsVersion",1);
        return true;
    }

    static <T> T read(Path file, Class<T> type, T defaults) {
        try {
            if (Files.exists(file)) {
                var object = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                boolean upgraded = type==Server.class && upgradeCooldownDefaults(object);
                T value = JSON.fromJson(object, type);
                if (value == null) throw new IllegalArgumentException("Expected a JSON object");
                // New options are added with their defaults and the file is laid out like a new one (grouped, short lists); every value the player
                // set and every unknown key a modpack added is kept as it was.
                String text = format(canonical(object, defaults), type == Server.class) + System.lineSeparator();
                if (upgraded || !text.equals(Files.readString(file))) {
                    // Replace only after a complete write so an interrupted upgrade preserves the old config.
                    Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName().toString(), ".tmp");
                    try {
                        Files.writeString(temporary, text);
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
            Files.writeString(file, format(JSON.toJsonTree(defaults).getAsJsonObject(), type == Server.class) + System.lineSeparator());
        } catch (IOException | RuntimeException exception) {
            if(type==Server.class)throw new IllegalStateException("Cannot load server config "+file+"; refusing to enable default terrain destruction. Fix the file before restarting.",exception);
            GodWandMod.LOGGER.warn("Cannot load {}; using defaults. Existing file is preserved.", file, exception);
        }
        return defaults;
    }

    static <T> void write(Path file, T value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, format(JSON.toJsonTree(value).getAsJsonObject(), value instanceof Server) + System.lineSeparator());
        } catch (IOException exception) {
            GodWandMod.LOGGER.error("Cannot save {}", file, exception);
        }
    }

    /** The keys of {@code defaults} in their order, with the file's value where it has one, then any key only the file has. */
    static com.google.gson.JsonObject canonical(com.google.gson.JsonObject file, Object defaults) {
        var out = new com.google.gson.JsonObject();
        for (var entry : JSON.toJsonTree(defaults).getAsJsonObject().entrySet())
            out.add(entry.getKey(), file.has(entry.getKey()) ? file.get(entry.getKey()) : entry.getValue());
        for (var entry : file.entrySet()) if (!out.has(entry.getKey())) out.add(entry.getKey(), entry.getValue());
        return out;
    }

    /** Skill prefixes of the server file, in order; a blank line separates one skill's keys from the next. */
    private static final String[] GROUPS = {"spatialPath", "singularity", "earthDrill", "stoneCannon", "supernova", "atomic", "fuga", "gate", "dimension"};
    private static final java.util.regex.Pattern LIST = java.util.regex.Pattern.compile("\\[([^\\[\\]{}]*)\\]");
    private static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("^  \"(\\w+)\":");

    /** Pretty JSON with every list of plain values on one line (the per-level cooldowns ran one number per line), and for the server file a blank
     *  line between skills (owner, 2026-11-16: shorter, and each skill's settings together). */
    static String format(com.google.gson.JsonObject object, boolean grouped) {
        String text = LIST.matcher(JSON.toJson(object)).replaceAll(m -> java.util.regex.Matcher.quoteReplacement("[" + m.group(1).trim().replaceAll("\\s*\\n\\s*", " ") + "]"));
        if (!grouped) return text;
        var out = new StringBuilder();
        String last = null;
        for (String line : text.split("\n", -1)) {
            var key = KEY.matcher(line);
            if (key.find()) {
                String group = "general";
                for (String g : GROUPS) if (key.group(1).startsWith(g)) { group = g; break; }
                if (last != null && !group.equals(last)) out.append('\n');
                last = group;
            }
            out.append(line).append('\n');
        }
        return out.substring(0, out.length() - 1);
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
