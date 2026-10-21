package net.godwand;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GodWandMod implements ModInitializer {
    public static final String MOD_ID = "godwand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("God Wand Mod initialized.");
    }
}
