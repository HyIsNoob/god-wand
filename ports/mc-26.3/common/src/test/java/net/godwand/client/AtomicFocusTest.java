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
    @Test void skippingOnlyUnlocksMovementAfterDetonationAndResetsNextCast() {
        var client=mock(Minecraft.class); client.player=mock(LocalPlayer.class);
        when(client.player.isAlive()).thenReturn(true);
        try(var minecraft=mockStatic(Minecraft.class);var renderer=mockStatic(AtomicRenderer.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            AtomicCinematic.reset();
            renderer.when(() -> AtomicRenderer.localFrame(0)).thenReturn(new AtomicRenderer.Frame(1,1,Vec3.ZERO,300,0,0,false));
            AtomicCinematic.skip(); assertTrue(AtomicCinematic.movementLocked());
            AtomicCinematic.onCastStart();
            renderer.when(() -> AtomicRenderer.localFrame(0)).thenReturn(new AtomicRenderer.Frame(1,2,Vec3.ZERO,500,0,0,false));
            assertTrue(AtomicCinematic.movementLocked());
            AtomicCinematic.skip(); assertFalse(AtomicCinematic.movementLocked());
            AtomicCinematic.onCastStart(); assertTrue(AtomicCinematic.movementLocked());
        } finally { AtomicCinematic.reset(); }
    }

    @Test void cinematicCameraDoesNotSnapBackWhenTerrainBlocksTheView() throws Exception {
        var client=mock(Minecraft.class); client.player=mock(LocalPlayer.class);
        var gui=Minecraft.class.getDeclaredField("gui");gui.setAccessible(true);gui.set(client,mock(net.minecraft.client.gui.Gui.class));
        client.level=mock(net.minecraft.client.multiplayer.ClientLevel.class);
        var options=Minecraft.class.getDeclaredField("options");options.setAccessible(true);
        options.set(client,mock(net.minecraft.client.Options.class));
        when(client.options.getEffectiveRenderDistance()).thenReturn(8);
        var chunks=mock(net.minecraft.client.multiplayer.ClientChunkCache.class);
        when(client.level.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(client.player.isAlive()).thenReturn(true);when(client.isWindowActive()).thenReturn(true);
        var prior=GodWandConfig.CLIENT;GodWandConfig.CLIENT=new GodWandConfig.Client();
        try(var minecraft=mockStatic(Minecraft.class);var renderer=mockStatic(AtomicRenderer.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            renderer.when(() -> AtomicRenderer.localFrame(0)).thenReturn(new AtomicRenderer.Frame(1,1,Vec3.ZERO,300,0,0,false));
            AtomicCinematic.reset();
            var pose=AtomicCinematic.camera(0,Vec3.ZERO,0,0);
            assertNotNull(pose);
            assertTrue(pose.position().distanceTo(net.godwand.skill.AtomicSequence.shot(300,0).offset())<.001);
            verify(client.level,never()).clip(any());
        } finally { AtomicCinematic.reset();GodWandConfig.CLIENT=prior; }
    }
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void focusLossAndScreensSuspendWithoutPoisoningThisOrTheNextCast() throws Exception {
        Minecraft client=mock(Minecraft.class);
        var gui=Minecraft.class.getDeclaredField("gui");gui.setAccessible(true);gui.set(client,mock(net.minecraft.client.gui.Gui.class));
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
            when(client.gui.screen()).thenReturn(mock(Screen.class));
            AtomicCinematic.tick(client); assertFalse(AtomicCinematic.active());
            when(client.gui.screen()).thenReturn(null); assertTrue(AtomicCinematic.active(),"Closing the focus-loss pause screen must resume the camera");
            AtomicCinematic.skip(); assertFalse(AtomicCinematic.active());
            renderer.when(() -> AtomicRenderer.localFrame(0)).thenReturn(new AtomicRenderer.Frame(1,1001,Vec3.ZERO,0,0,0,false));
            AtomicCinematic.onCastStart(); assertTrue(AtomicCinematic.active(),"Immediate next cast must not inherit the previous skip");
        } finally { AtomicCinematic.reset(); GodWandConfig.CLIENT=previous; }
    }
}
