package net.godwand.client;

import net.godwand.network.FugaFxPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FugaClockTest {
    @Test void cutsceneNeverReplaysWhenServerTimeIsSetBackButCatchesUpWhenItJumps() {
        FugaRenderer.reset();
        var hold = new FugaFxPayload(1, 0, 0, 0, 0, 0, 0, 1, FugaFxPayload.CHARGE, 0);
        assertEquals(100, FugaRenderer.steadyAge(1, hold, 100, 0), 1e-9);
        // A stalled server's time packet rewinds the game time by 30 ticks: the cutscene must not go back.
        assertEquals(100, FugaRenderer.steadyAge(1, hold, 70, 0), 1e-9);
        // When the server catches up and its time runs ahead, follow it.
        assertEquals(130, FugaRenderer.steadyAge(1, hold, 130, 0), 1e-9);
        // A new cast starts from its own time.
        var next = new FugaFxPayload(1, 500, 0, 0, 0, 0, 0, 1, FugaFxPayload.CHARGE, 500);
        assertEquals(5, FugaRenderer.steadyAge(1, next, 5, 0), 1e-9);
        FugaRenderer.reset();
    }
}
