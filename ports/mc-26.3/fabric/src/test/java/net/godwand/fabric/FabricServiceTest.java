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
    @Test void packagedItemDefinitionSelectsTheRegisteredWandRenderer() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/godwand/items/god_wand.json")) {
            assertNotNull(input,"The native item definition is required to display the wand");
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("godwand:wand",json.getAsJsonObject("model").get("type").getAsString());
        }
    }

}
