package net.godwand.skill;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShieldSoundTest {
    @Test
    void aProjectileVolleyGetsOneChimeAndRespawnDoesNotMuteItForever() {
        UUID owner=UUID.randomUUID();
        assertTrue(SpatialPathWalker.shouldPlayShieldSound(owner,100));
        for(int tick=100;tick<106;tick++) assertFalse(SpatialPathWalker.shouldPlayShieldSound(owner,tick));
        assertTrue(SpatialPathWalker.shouldPlayShieldSound(owner,106));
        assertTrue(SpatialPathWalker.shouldPlayShieldSound(UUID.randomUUID(),106));
        assertTrue(SpatialPathWalker.shouldPlayShieldSound(owner,0));
    }
}
