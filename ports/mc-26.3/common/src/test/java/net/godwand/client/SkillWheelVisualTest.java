package net.godwand.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillWheelVisualTest {
    @Test void spritesAreSmallPixelArtWithSixDistinctSkillsAndThreeStates() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/godwand/textures/gui/skill_wheel.png")) {
            assertNotNull(stream);
            var image=ImageIO.read(stream);
            assertEquals(240,image.getWidth());assertEquals(120,image.getHeight());
            Set<Integer> skills=new HashSet<>();
            for(int mode=0;mode<6;mode++) {
                skills.add(image.getRGB(mode*40+4,4));
                assertEquals(0,image.getRGB(mode*40,0)>>>24,"Cut corners stay transparent");
                assertNotEquals(image.getRGB(mode*40+2,10),image.getRGB(mode*40+2,50));
                assertNotEquals(image.getRGB(mode*40+2,50),image.getRGB(mode*40+2,90));
                int opaque=0;
                for(int y=8;y<32;y++)for(int x=8;x<32;x++)if((image.getRGB(mode*40+x,y)>>>24)==255)opaque++;
                assertEquals(24*24,opaque,"Icon backing must remain readable over bright terrain");
            }
            assertEquals(6,skills.size());
        }
    }

    @Test void wheelUsesSixUnscaledSpritesWithSeparateHoverSelectionAndBoundedCooldown() throws Exception {
        var context=mock(GuiGraphicsExtractor.class);
        List<int[]> sprites=new ArrayList<>(),fills=new ArrayList<>();
        doAnswer(call -> {
            assertEquals("godwand:textures/gui/skill_wheel.png",call.getArgument(1).toString());
            int[] args=new int[8];
            for(int i=0;i<8;i++)args[i]=((Number)call.getArgument(i+2)).intValue();
            sprites.add(args);return null;
        }).when(context).blit(eq(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED),any(Identifier.class),anyInt(),anyInt(),anyFloat(),anyFloat(),anyInt(),anyInt(),anyInt(),anyInt());
        doAnswer(call -> {
            int[] args=new int[5];for(int i=0;i<5;i++)args[i]=call.getArgument(i);
            fills.add(args);return null;
        }).when(context).fill(anyInt(),anyInt(),anyInt(),anyInt(),anyInt());
        SkillWheelScreen.drawWheel(context,5,1,2);
        int[][] positions={{-20,-102},{51,-61},{51,21},{-20,62},{-91,21},{-91,-61}};
        assertEquals(6,sprites.size());
        for(int mode=0;mode<6;mode++) {
            int[] a=sprites.get(mode);
            assertArrayEquals(new int[]{positions[mode][0],positions[mode][1],mode*40,mode==1?80:mode==5?40:0,40,40,240,120},a);
        }
        assertTrue(fills.stream().allMatch(a -> a[2]-a[0]<=28&&a[3]-a[1]<=3),"No opaque wheel plate or overflowing cooldown");
        sprites.clear();fills.clear();SkillWheelScreen.drawWheel(context,5,5,.35f);
        Path path=Path.of("build/previews/skill-wheel-draws.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new com.google.gson.Gson().toJson(Map.of("sprites",sprites,"fills",fills)));
    }
}
