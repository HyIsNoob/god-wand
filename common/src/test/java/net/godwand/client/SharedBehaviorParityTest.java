package net.godwand.client;

import java.nio.file.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedBehaviorParityTest {
    private static String body(String source,String name) {
        var match=Pattern.compile("(?:public|protected|private) (?:static )?[^\\n]+\\b"+name+"\\(").matcher(source);
        assertTrue(match.find(),"Missing method: "+name);
        int start=source.indexOf('{',match.start()),end=start+1,depth=1;
        while(depth>0) { char c=source.charAt(end++); if(c=='{')depth++; else if(c=='}')depth--; }
        return source.substring(start,end).replaceAll("//[^\\n]*", "").replaceAll("\\s+", "")
                .replace("net.minecraft.util.Mth.clamp","Math.clamp").replace("Mth.clamp","Math.clamp")
                .replace(".rotate(",".mulPose(")
                .replace(".withInitialValue(GodWandConfig.CLIENT.wandSkin)","")
                .replace("builder(WandSkin::getDisplayName,GodWandConfig.CLIENT.wandSkin)","builder(WandSkin::getDisplayName)");
    }
    @Test void versionAdaptersDoNotChangeWandPoseColorsModelGeometryOrSettingsLayout() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        String[][] methods={
            {"client/render/GodWandItemRenderer.java","coreColor","applyUseTransform","applyAtomicHoldTransform"},
            {"client/model/AquaHeartiaModel.java","getBandCorners"},
            {"client/gui/GodWandConfigScreen.java","init"},
            {"client/SkillWheelScreen.java","icon"}
        };
        for(String profile:new String[]{"fabric-1.20.1","forge-1.20.1","mc-26.3"})
            for(String[] entry:methods) {
                String common=Files.readString(root.resolve("common/src/main/java/net/godwand/"+entry[0]));
                String port=Files.readString(root.resolve("build/shared/"+profile+"/src/main/java/net/godwand/"+entry[0]));
                for(int i=1;i<entry.length;i++)assertEquals(body(common,entry[i]),body(port,entry[i]),profile+": "+entry[0]+"#"+entry[i]);
            }
    }
}
