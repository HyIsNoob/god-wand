package net.godwand.fabric;

import net.godwand.platform.Services;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FabricServiceTest {
    @Test
    void testPlatformHelperLoaded() {
        assertNotNull(Services.PLATFORM);
        assertEquals("Fabric", Services.PLATFORM.getPlatformName());
    }

    @Test
    void testNetworkHelperLoaded() {
        assertNotNull(Services.NETWORK);
        assertEquals("net.godwand.fabric.platform.FabricNetworkHelper", Services.NETWORK.getClass().getName());
    }
}
