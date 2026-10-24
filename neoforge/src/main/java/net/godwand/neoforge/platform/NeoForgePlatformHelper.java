package net.godwand.neoforge.platform;

import net.godwand.platform.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigDirectory() {
        if (FMLPaths.CONFIGDIR != null && FMLPaths.CONFIGDIR.get() != null) {
            return FMLPaths.CONFIGDIR.get();
        }
        return Path.of("config");
    }
}
