package net.godwand.client;

import net.godwand.config.GodWandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtomicFocusTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void focusLossAndScreensSuspendWithoutPoisoningThisOrTheNextCast() {
        Minecraft client=mock(Minecraft.class);
        client.player=mock(LocalPlayer.class);
        when(client.player.isAlive()).thenReturn(true);
        when(client.isWindowActive()).thenReturn(true);
        var previous=GodWandConfig.CLIENT;
        GodWandConfig.CLIENT=new GodWandConfig.Client();
        try(var minecraft=mockStatic(Minecraft.class);var renderer=mockStatic(AtomicRenderer.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            renderer.when(() -> AtomicRenderer.localFrame(0)).thenReturn(new AtomicRenderer.Frame(1,1000,Vec3.ZERO,300,0,0,false));
            AtomicCinematic.reset(); assertTrue(AtomicCinematic.active());
            when(client.isWindowActive()).thenReturn(false);
            AtomicCinematic.tick(client); assertFalse(AtomicCinematic.active());
            when(client.isWindowActive()).thenReturn(true);
            client.screen=mock(Screen.class);
            AtomicCinematic.tick(client); assertFalse(AtomicCinematic.active());
            client.screen=null; assertTrue(AtomicCinematic.active(),"Closing the focus-loss pause screen must resume the camera");
            AtomicCinematic.skip(); assertFalse(AtomicCinematic.active());
            renderer.when(() -> AtomicRenderer.localFrame(0)).thenReturn(new AtomicRenderer.Frame(1,1001,Vec3.ZERO,0,0,0,false));
            AtomicCinematic.onCastStart(); assertTrue(AtomicCinematic.active(),"Immediate next cast must not inherit the previous skip");
        } finally { AtomicCinematic.reset(); GodWandConfig.CLIENT=previous; }
    }
}
