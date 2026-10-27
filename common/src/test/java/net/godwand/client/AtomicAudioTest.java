package net.godwand.client;

import net.godwand.network.AtomicFxPayload;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtomicAudioTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static AtomicFxPayload payload(int phase, long release) {
        return new AtomicFxPayload(42, 0, 0, 0, 0, phase, release);
    }

    @Test void fullTracksSurviveHoldAndCutsceneEndButCancelAndResetStopThem() {
        var client=mock(Minecraft.class);
        var sounds=mock(SoundManager.class);
        client.level=mock(ClientLevel.class);
        client.player=mock(LocalPlayer.class);
        when(client.player.getId()).thenReturn(42);
        when(client.player.position()).thenReturn(Vec3.ZERO);
        when(client.getSoundManager()).thenReturn(sounds);
        try(var minecraft=mockStatic(Minecraft.class);var camera=mockStatic(AtomicCinematic.class);var post=mockStatic(AtomicPostFx.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            AtomicRenderer.reset();
            AtomicRenderer.receive(payload(AtomicFxPayload.CHARGE,-1));
            var tracks=org.mockito.ArgumentCaptor.forClass(SoundInstance.class);
            verify(sounds).play(tracks.capture());
            SoundInstance charge=tracks.getValue();
            assertEquals("godwand:atomic_charge",charge.getLocation().toString());
            when(sounds.isActive(charge)).thenReturn(true);
            AtomicRenderer.receive(payload(AtomicFxPayload.ARMED,-1));
            when(client.level.getGameTime()).thenReturn(500L);
            AtomicRenderer.tick(client);
            verify(sounds,never()).stop(charge);
            verify(sounds).play(argThat(s -> s.getLocation().toString().equals("minecraft:entity.warden.heartbeat")));
            AtomicRenderer.receive(payload(AtomicFxPayload.RELEASE,500));
            verify(sounds).play(argThat(s -> s.getLocation().toString().equals("godwand:atomic_blast")));
            AtomicRenderer.receive(payload(AtomicFxPayload.BURST,500));
            verify(sounds,times(1)).play(argThat(s -> s.getLocation().toString().equals("godwand:atomic_blast")));
            when(client.level.getGameTime()).thenReturn(500L+AtomicSequence.END-AtomicSequence.WAIT_TICK);
            AtomicRenderer.tick(client);
            assertNull(AtomicRenderer.localFrame(0),"Audio tails must not prolong the cinematic");
            verify(sounds,never()).stop(charge);
            AtomicRenderer.receive(payload(AtomicFxPayload.CANCEL,500));
            verify(sounds).stop(charge);
            verify(sounds).stop(argThat(s -> s.getLocation().toString().equals("godwand:atomic_blast")));
            clearInvocations(sounds);
            AtomicRenderer.receive(payload(AtomicFxPayload.CHARGE,-1));
            AtomicRenderer.reset();
            verify(sounds).stop(argThat(s -> s.getLocation().toString().equals("godwand:atomic_charge")));
        }
    }
}
