package net.godwand.client;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class FugaSoundscapeTest {
    private static byte[] sound(String name) throws IOException {
        try (var in = FugaSoundscapeTest.class.getResourceAsStream("/assets/godwand/sounds/" + name + ".ogg")) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }
    @Test void shippedPartsArePlaceholdersAndTheLoopIsReal() throws IOException {
        // A mismatched hash would mute the vanilla stand-ins (or play them over real audio).
        assertFalse(FugaSoundscape.isCustomAudio(sound(FugaSoundscape.CHARGE)));
        assertFalse(FugaSoundscape.isCustomAudio(sound(FugaSoundscape.RELEASE)));
        assertTrue(FugaSoundscape.isCustomAudio(sound(FugaSoundscape.HOLD)));
        assertFalse(FugaSoundscape.isCustomAudio(new byte[]{1, 2, 3, 4}));
    }
}
