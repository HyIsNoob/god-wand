package net.godwand.neoforge;

import org.junit.jupiter.api.Test;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeNativePortTest {
    @Test void nativeWandAndLicenseResourcesArePresent() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/godwand/items/god_wand.json")) {
            assertNotNull(input);
            var model=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
            assertEquals("godwand:wand",model.getAsJsonObject("model").get("type").getAsString());
        }
        try(var input=getClass().getResourceAsStream("/godwand.neoforge.mixins.json")) {
            var mixins=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
            assertEquals("JAVA_25",mixins.get("compatibilityLevel").getAsString());
            assertTrue(mixins.getAsJsonArray("client").toString().contains("AtomicPoseCaptureMixin"));
            assertTrue(mixins.getAsJsonArray("client").toString().contains("PostPassUniformsAccessor"));
        }
    }
    @Test void neoWorldGeometryCompletesBeforeTheSharedCompositeAndHud() throws Exception {
        try(var input=getClass().getResourceAsStream("/net/minecraft/client/renderer/GameRenderer.class")) {
            assertNotNull(input);
            var model=ClassFile.of().parse(input.readAllBytes());
            var method=model.methods().stream().filter(m -> m.methodName().equalsString("renderLevel")).findFirst().orElseThrow();
            int world=-1,event=-1,hud=-1,index=0;
            for(var element:method.code().orElseThrow()) {
                if(element instanceof InvokeInstruction call) {
                    if(call.owner().asInternalName().equals("net/minecraft/client/renderer/LevelRenderer")&&call.name().equalsString("renderLevel"))world=index;
                    if(call.owner().asInternalName().endsWith("RenderLevelStageEvent$AfterLevel")&&call.name().equalsString("<init>"))event=index;
                    if(call.name().equalsString("renderItemInHand")) {
                        hud=index;
                        assertEquals("(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",call.type().stringValue());
                    }
                }
                index++;
            }
            assertTrue(world>=0&&hud>world,"Native AfterLevel geometry must draw before the before-HUD composite hook");
        }
    }
    @Test void afterLevelDrawUsesTheCameraViewAfterVanillaPopsItsStack() throws Exception {
        try(var input=getClass().getResourceAsStream("/net/godwand/neoforge/client/GodWandNeoForgeClientMod$GameBusEvents.class")) {
            var model=ClassFile.of().parse(input.readAllBytes());
            var method=model.methods().stream().filter(m -> m.methodName().equalsString("onRenderLevelStage")).findFirst().orElseThrow();
            boolean cameraView=false,flush=false;
            for(var element:method.code().orElseThrow()) if(element instanceof InvokeInstruction call) {
                if(call.name().equalsString("getModelViewMatrix"))cameraView=true;
                if(call.name().equalsString("flush")) {
                    assertEquals("(Lorg/joml/Matrix4fc;)V",call.type().stringValue());
                    assertTrue(cameraView);flush=true;
                }
            }
            assertTrue(flush);
        }
    }
}
