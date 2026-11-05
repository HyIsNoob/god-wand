package net.godwand.client;

import net.godwand.client.gui.GodWandConfigScreen;
import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Cycling the wand model button walks through every skin without throwing. */
class ConfigScreenSkinTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void wandModelButtonCyclesThroughEverySkin() throws Exception {
        var client=mock(Minecraft.class,RETURNS_DEEP_STUBS);
        var previous=GodWandConfig.CLIENT;
        GodWandConfig.CLIENT=new GodWandConfig.Client();
        try(var clients=mockStatic(Minecraft.class)) {
            clients.when(Minecraft::getInstance).thenReturn(client);
            var screen=new GodWandConfigScreen(null);
            screen.init(400,300);
            CycleButton<?> skins=screen.children().stream().filter(c->c instanceof CycleButton<?> b&&b.getValue() instanceof WandSkin)
                    .map(c->(CycleButton<?>)c).findFirst().orElseThrow();
            var seen=new java.util.HashSet<WandSkin>();
            var cycle=CycleButton.class.getDeclaredMethod("cycleValue",int.class);
            cycle.setAccessible(true);
            for(int i=0;i<WandSkin.values().length;i++) {
                try { cycle.invoke(skins,1); }
                catch(java.lang.reflect.InvocationTargetException e) { fail("cycling skins: "+e.getCause(),e.getCause()); }
                seen.add(GodWandConfig.CLIENT.wandSkin);
            }
            assertEquals(WandSkin.values().length,seen.size());
        } finally {GodWandConfig.CLIENT=previous;}
    }
}
