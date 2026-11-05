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
    @Test void everyShippedPartIsRealAudio() throws IOException {
        // A part read as a placeholder would play the vanilla stand-in cues over it.
        for (String part : new String[]{FugaSoundscape.CHARGE, FugaSoundscape.HOLD, FugaSoundscape.RELEASE, FugaSoundscape.END})
            assertTrue(FugaSoundscape.isCustomAudio(sound(part)), part);
        assertFalse(FugaSoundscape.isCustomAudio(new byte[]{1, 2, 3, 4}));
    }
}
