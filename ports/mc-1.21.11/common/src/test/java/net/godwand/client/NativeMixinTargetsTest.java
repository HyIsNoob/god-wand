package net.godwand.client;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMixinTargetsTest {
    private static ClassModel model(String name) throws Exception {
        try(var in=NativeMixinTargetsTest.class.getResourceAsStream("/"+name.replace('.','/')+".class")) {
            assertNotNull(in,name);return ClassFile.of().parse(in.readAllBytes());
        }
    }
    @Test void everyConfiguredMixinHasRealNativeTargets() throws Exception {
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../fabric/src/main/resources/godwand.mixins.json"))).getAsJsonObject();
        assertEquals("JAVA_21",config.get("compatibilityLevel").getAsString());
        for(String side:new String[]{"mixins","client"}) for(var entry:config.getAsJsonArray(side)) {
            String source=Files.readString(Path.of("../../../build/shared/mc-1.21.11/src/main/java/net/godwand/mixin/"+entry.getAsString()+".java"));
            var target=Pattern.compile("@Mixin\\(\\s*(?:value\\s*=\\s*)?(\\w+)\\.class").matcher(source);
            assertTrue(target.find(),entry.toString());
            var imp=Pattern.compile("import (net\\.minecraft\\.[\\w.]+\\."+target.group(1)+");").matcher(source);
            String name=null;
            if(imp.find()) name=imp.group(1);
            else {
                var wildcard=Pattern.compile("import (net\\.minecraft\\.[\\w.]+)\\.\\*;").matcher(source);
                while(wildcard.find()) {
                    String candidate=wildcard.group(1)+"."+target.group(1);
                    if(getClass().getResource("/"+candidate.replace('.','/')+".class")!=null) {name=candidate;break;}
                }
            }
            assertNotNull(name,entry.toString());
            final String owner=name;
            var nativeClass=model(name);
            var selectors=Pattern.compile("@(?:Inject|ModifyArg|ModifyArgs)\\(method\\s*=\\s*(\\{[^}]+}|\"[^\"]+\")").matcher(source);
            while(selectors.find()) {
                var quoted=Pattern.compile("\"([^\"]+)\"").matcher(selectors.group(1));
                while(quoted.find()) {
                    String selector=quoted.group(1);int descriptor=selector.indexOf('(');
                    assertTrue(nativeClass.methods().stream().anyMatch(m -> descriptor<0
                        ?m.methodName().equalsString(selector)
                        :m.methodName().equalsString(selector.substring(0,descriptor))&&m.methodType().equalsString(selector.substring(descriptor))),owner+"#"+selector);
                }
            }
            var fields=Pattern.compile("@Accessor\\(\"([^\"]+)\"\\)").matcher(source);
            while(fields.find()) {
                String field=fields.group(1);assertTrue(nativeClass.fields().stream().anyMatch(f -> f.fieldName().equalsString(field)),owner+"#"+field);
            }
        }
    }
    @Test void proceduralGeometryUsesVanillaForwardDepth() throws Exception {
        assertEquals(com.mojang.blaze3d.platform.DepthTestFunction.LEQUAL_DEPTH_TEST,net.minecraft.client.renderer.RenderPipelines.SOLID_BLOCK.getDepthTestFunction());
        for(String type:new String[]{"net.godwand.client.compat.GodWandGeometry"}) {
            var fields=model(type).methods().stream().flatMap(m -> m.code().stream()).flatMap(code -> java.util.stream.StreamSupport.stream(code.spliterator(),false))
                .filter(e -> e instanceof java.lang.classfile.instruction.FieldInstruction)
                .map(e -> (java.lang.classfile.instruction.FieldInstruction)e)
                .filter(f -> f.owner().asInternalName().equals("com/mojang/blaze3d/platform/DepthTestFunction"))
                .map(f -> f.name().stringValue()).toList();
            assertTrue(fields.contains("LEQUAL_DEPTH_TEST"),type);
            assertFalse(fields.contains("GREATER_DEPTH_TEST"),type);
        }
    }

}
