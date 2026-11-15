package net.godwand.client;

import org.junit.jupiter.api.Test;
import static net.godwand.client.BlueRedInput.edge;
import static org.junit.jupiter.api.Assertions.*;

class BlueRedInputTest {
    // edge(wasHeld, attackDown, eligible, screenOpen, focused)
    @Test void pressStarts() { assertEquals(Boolean.TRUE, edge(false, true, true, false, true)); }
    @Test void holdIsQuiet() { assertNull(edge(true, true, true, false, true)); }
    @Test void releaseStops() { assertEquals(Boolean.FALSE, edge(true, false, true, false, true)); }
    @Test void switchingItemReleases() { assertEquals(Boolean.FALSE, edge(true, true, false, false, true)); }
    @Test void openingScreenReleases() { assertEquals(Boolean.FALSE, edge(true, true, true, true, true)); }
    @Test void focusLossReleases() { assertEquals(Boolean.FALSE, edge(true, true, true, false, false)); }
    @Test void notEligibleNeverStarts() {
        assertNull(edge(false, true, false, false, true));
        assertNull(edge(false, true, true, true, true));
        assertNull(edge(false, false, true, false, true));
    }
}
