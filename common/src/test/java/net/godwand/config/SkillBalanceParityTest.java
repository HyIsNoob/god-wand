package net.godwand.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillBalanceParityTest {
    private static java.util.List<String> calls(String source,String helper) {
        return Pattern.compile("GodWandConfig\\."+helper+"\\([^)]*\\)").matcher(source)
                .results().map(m -> m.group().replaceAll("\\s+","")).toList();
    }
    @Test void supportedPortsUseTheSameDamageAndCooldownSettingsAtEveryCallSite() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        for(String port:new String[]{"fabric-1.20.1","forge-1.20.1"}) {
            for(String name:new String[]{"ShockwaveSlicer","RatioCollapseManager","SupernovaManager","StoneCannonManager"}) {
                String path="src/main/java/net/godwand/skill/"+name+".java";
                String shared=Files.readString(root.resolve("common/"+path));
                String copy=Files.readString(root.resolve("build/shared/"+port+"/"+path));
                var expected=calls(shared,"skillDamageMultiplier");
                assertFalse(expected.isEmpty(),name);
                assertEquals(expected,calls(copy,"skillDamageMultiplier"),port+": "+name);
                assertFalse(copy.contains("SERVER.damageMultiplier"),"Every damage stage must use the tier setting: "+name);
            }
            for(String path:new String[]{"item/GodWandItem.java","skill/SpatialPathWalker.java"}) {
                String shared=Files.readString(root.resolve("common/src/main/java/net/godwand/"+path));
                String copy=Files.readString(root.resolve("build/shared/"+port+"/src/main/java/net/godwand/"+path));
                assertEquals(calls(shared,"skillCooldownTicks"),calls(copy,"skillCooldownTicks"),port+": "+path);
                assertFalse(copy.contains("GodWandConfig.cooldownTicks("),path);
            }
        }
    }
}
