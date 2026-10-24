package net.godwand.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.godwand.GodWandMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GodWandConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Client CLIENT = new Client();
    public static Server SERVER = new Server();

    public static final class Client {
        public boolean postEffects = true;
        public boolean impactFrames = true;
        public boolean cameraFov = true;
        public boolean skyBlackout = true;
        public boolean targetMarker = true;
        public float cameraShake = 1;
        public float bloomStrength = .85f;
        public int bloomDownsample = 4;

        public void validate() {
            cameraShake = finiteClamp(cameraShake, 0, 1, 1);
            bloomStrength = finiteClamp(bloomStrength, 0, 1.5f, .85f);
            bloomDownsample = Math.clamp(bloomDownsample, 2, 8);
        }
    }

    public static final class Server {
        public float cooldownMultiplier = 1;
        public int supernovaTerrainRadius = 96;
        public int supernovaMaxDisplays = 1800;

        public void validate() {
            cooldownMultiplier = finiteClamp(cooldownMultiplier, .1f, 10, 1);
            supernovaTerrainRadius = Math.clamp(supernovaTerrainRadius, 16, 96);
            supernovaMaxDisplays = Math.clamp(supernovaMaxDisplays, 0, 1800);
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

    static <T> T read(Path file, Class<T> type, T defaults) {
        try {
            if (Files.exists(file)) {
                T value = JSON.fromJson(Files.readString(file), type);
                if (value == null) throw new IllegalArgumentException("Expected a JSON object");
                return value;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.toJson(defaults) + System.lineSeparator());
        } catch (IOException | RuntimeException exception) {
            GodWandMod.LOGGER.warn("Cannot load {}; using defaults. Existing file is preserved.", file, exception);
        }
        return defaults;
    }

    public static int cooldownTicks(int ticks) {
        return Math.max(1, Math.round(ticks * SERVER.cooldownMultiplier));
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
