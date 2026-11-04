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
    @Test void sharedLoaderGameplayUsesTierDamageAndCooldownSettingsAtEveryCallSite() throws Exception {
        Path source=Path.of("../../../build/shared/mc-26.1.2/src/main/java/net/godwand");
        for(String name:new String[]{"ShockwaveSlicer","RatioCollapseManager","SupernovaManager","StoneCannonManager"}) {
            String shared=Files.readString(source.resolve("skill/"+name+".java"));
            assertFalse(calls(shared,"skillDamageMultiplier").isEmpty(),name);
            assertFalse(shared.contains("SERVER.damageMultiplier"),"Every damage stage must use the tier setting: "+name);
        }
        for(String path:new String[]{"item/GodWandItem.java","skill/SpatialPathWalker.java"}) {
            String shared=Files.readString(source.resolve(path));
            assertFalse(calls(shared,"skillCooldownTicks").isEmpty(),path);
            assertFalse(shared.contains("GodWandConfig.cooldownTicks("),path);
        }
    }
}
