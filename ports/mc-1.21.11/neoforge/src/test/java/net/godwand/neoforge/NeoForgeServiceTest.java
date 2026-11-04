package net.godwand.neoforge;

import net.godwand.platform.Services;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NeoForgeServiceTest {
    @Test
    void testPlatformHelperLoaded() {
        assertNotNull(Services.PLATFORM);
        assertEquals("NeoForge", Services.PLATFORM.getPlatformName());
    }

    @Test
    void testNetworkHelperLoaded() {
        assertNotNull(Services.NETWORK);
        assertEquals("net.godwand.neoforge.platform.NeoForgeNetworkHelper", Services.NETWORK.getClass().getName());
    }
}
