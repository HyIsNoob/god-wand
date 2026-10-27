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
        public boolean atomicCutscene = true;
        public boolean postEffects = true;
        public boolean impactFrames = true;
        public boolean cameraFov = true;
        public boolean skyBlackout = true;
        public boolean targetMarker = true;
        public float cameraShake = 1;
        public float bloomStrength = .85f;
        public int bloomDownsample = 4;

        public WandSkin getActiveSkin(int skillMode) {
            if (wandSkin != null && wandSkin != WandSkin.AUTO) return wandSkin;
            return switch (skillMode) {
                case 2, 4 -> WandSkin.AQUA_HEARTIA;
                case 5 -> WandSkin.SHADOW_BLADE;
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
        public float cooldownMultiplier = 1;
        public float damageMultiplier = 1;
        public boolean supernovaTerrainDamage = true;
        public boolean supernovaBlockLift = true;
        public boolean earthDrillTerrainDamage = true;
        public boolean earthDrillBlockLift = true;
        public boolean stoneCannonTerrainDamage = true;
        public boolean atomicTerrainDamage = true;
        public int atomicTerrainRadius = 98;
        public float atomicDamage = 9999;
        public float atomicShockwaveDamage = 15;
        public int supernovaTerrainRadius = 120;
        public int supernovaMaxDisplays = 1800;
        public List<String> dimensionBlacklist = new ArrayList<>();

        public boolean isDimensionBlacklisted(String dimensionId) {
            return dimensionBlacklist != null && dimensionBlacklist.contains(dimensionId);
        }

        public boolean isDimensionAllowed(String dimensionId) {
            return dimensionBlacklist == null || !dimensionBlacklist.contains(dimensionId);
        }

        public void validate() {
            cooldownMultiplier = finiteClamp(cooldownMultiplier, .1f, 10, 1);
            damageMultiplier = finiteClamp(damageMultiplier, 0, 100, 1);
            atomicTerrainRadius = Mth.clamp(atomicTerrainRadius, 0, 98);
            atomicDamage = finiteClamp(atomicDamage, 0, 1_000_000, 9999);
            atomicShockwaveDamage = finiteClamp(atomicShockwaveDamage, 0, 1_000_000, 15);
            supernovaTerrainRadius = Mth.clamp(supernovaTerrainRadius, 16, 180);
            supernovaMaxDisplays = Mth.clamp(supernovaMaxDisplays, 0, 1800);
            if (dimensionBlacklist == null) dimensionBlacklist = new ArrayList<>();
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

    static <T> T read(Path file, Class<T> type, T defaults) {
        try {
            if (Files.exists(file)) {
                var object = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                T value = JSON.fromJson(object, type);
                if (value == null) throw new IllegalArgumentException("Expected a JSON object");
                boolean upgraded = false;
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

    public static int cooldownTicks(int ticks) {
        return Math.max(1, Math.round(ticks * SERVER.cooldownMultiplier));
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }
}
