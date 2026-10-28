package net.godwand.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.godwand.client.gui.GodWandConfigScreen;

public class GodWandModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return GodWandConfigScreen::new;
    }
}
