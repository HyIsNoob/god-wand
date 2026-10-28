package net.godwand.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AtomicPortParityTest {
    private static String normalized(String text) {
        return text.replaceAll("ResourceLocation\\.fromNamespaceAndPath\\(","new ResourceLocation(").replace("net.minecraft.util.Mth.clamp","Math.clamp").replaceAll("\\s+", "");
    }
    private static String method(String text,String name) {
        var match=java.util.regex.Pattern.compile("(?:(?:public|private) )?static [^\\n]+\\b"+name+"\\(").matcher(text);
        assertTrue(match.find(),"Missing method: "+name);
        int start=text.indexOf('{',match.start()),end=start+1,depth=1;
        while(depth>0) { char c=text.charAt(end++); if(c=='{')depth++; else if(c=='}')depth--; }
        return text.substring(match.start(),end);
    }
    @Test void portsKeepTheSameAtomicChoreographyGeometryAndShaders() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path common=root.resolve("common/src/main/java/net/godwand");
        for(String loader:new String[]{"fabric-1.20.1","forge-1.20.1"}) {
            Path port=root.resolve(loader+"/src/main/java/net/godwand");
            for(String file:new String[]{"skill/AtomicSequence.java","mixin/AtomicPoseMixin.java"})
                assertEquals(normalized(Files.readString(common.resolve(file))),normalized(Files.readString(port.resolve(file))),loader+": "+file);
            assertEquals(normalized(method(Files.readString(common.resolve("config/GodWandConfig.java")),"read")),
                    normalized(method(Files.readString(port.resolve("config/GodWandConfig.java")),"read")),loader+": config migration");
            for(String name:new String[]{"releaseMovement","movementLocked"})
                assertEquals(normalized(method(Files.readString(common.resolve("skill/AtomicManager.java")),name)),
                        normalized(method(Files.readString(port.resolve("skill/AtomicManager.java")),name)),loader+": server "+name);
            for(String name:new String[]{"skillCooldownTicks","skillDamageMultiplier","validatedCooldowns","validatedDamage"})
                assertEquals(normalized(method(Files.readString(common.resolve("config/GodWandConfig.java")),name)),
                        normalized(method(Files.readString(port.resolve("config/GodWandConfig.java")),name)),loader+": balance "+name);
            String source=Files.readString(common.resolve("client/AtomicRenderer.java")),copy=Files.readString(port.resolve("client/AtomicRenderer.java"));
            for(String name:new String[]{"emit","cylinder","charge","fortress","blast","pressureAura","captureSwordTip","sampleSwordTip","swordTrail","bladeConvergence","receive","stop","tick","reset"})
                assertEquals(normalized(method(source,name)),normalized(method(copy,name)),loader+": "+name);
            String cinematic=Files.readString(common.resolve("client/AtomicCinematic.java"));
            String portCinematic=Files.readString(port.resolve("client/AtomicCinematic.java"));
            for(String name:new String[]{"active","movementLocked","skip","reset","onCastStart","camera","wideClearance","fov"})
                assertEquals(normalized(method(cinematic,name)),normalized(method(portCinematic,name)),loader+": "+name);
            String held=Files.readString(common.resolve("client/render/GodWandItemRenderer.java"));
            String portHeld=Files.readString(port.resolve("client/render/GodWandItemRenderer.java"));
            for(String name:new String[]{"applySwordDisplayTransform","applyAtomicSwordHoldTransform"})
                assertEquals(normalized(method(held,name)),normalized(method(portHeld,name)),loader+": "+name);
            for(String file:new String[]{"shaders/program/godwand_atomic.fsh","shaders/program/godwand_atomic.json","shaders/post/godwand_atomic.json"})
                assertEquals(Files.readString(root.resolve("common/src/main/resources/assets/minecraft/"+file)),
                        Files.readString(root.resolve(loader+"/src/main/resources/assets/minecraft/"+file)),loader+": "+file);
        }
    }
}
