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

    @org.junit.jupiter.api.Test void proceduralGpuCommandsRunAfterTheVanillaMainPassCloses() throws Exception {
        try(var input=getClass().getResourceAsStream("/net/godwand/fabric/client/GodWandFabricClientMod.class")) {
            assertNotNull(input);
            var model=java.lang.classfile.ClassFile.of().parse(input.readAllBytes());
            var events=model.methods().stream().filter(m -> m.methodName().equalsString("onInitializeClient"))
                    .flatMap(m -> m.code().stream()).flatMap(code -> java.util.stream.StreamSupport.stream(code.spliterator(),false))
                    .filter(e -> e instanceof java.lang.classfile.instruction.FieldInstruction)
                    .map(e -> (java.lang.classfile.instruction.FieldInstruction)e)
                    .filter(f -> f.owner().asInternalName().equals("net/fabricmc/fabric/api/client/rendering/v1/level/LevelRenderEvents"))
                    .map(f -> f.name().stringValue()).toList();
            assertTrue(events.contains("END_MAIN"),"Uploads and fences need the vanilla render pass to be closed");
            assertFalse(events.contains("AFTER_TRANSLUCENT_TERRAIN"));
        }
    }

}
