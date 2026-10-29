package net.godwand.client;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SkillWheelVisualTest {
    @Test void sixColoredSealsLeaveTheWorldVisibleAndKeepAllStatesBounded() throws Exception {
        var colors=new java.util.HashSet<Integer>();
        for(int mode=0;mode<6;mode++) {
            colors.add(SkillWheelScreen.skillColor(mode));
            for(int hovered:new int[]{-1,mode,(mode+1)%6}) {
                var mesh=new AtomicVisualTest.Mesh();
                SkillWheelScreen.emitWheel(mesh,new Matrix4f(),mode,hovered,.5f);
                assertEquals(0,mesh.vertices.size()%4);
                assertTrue(mesh.vertices.size()<24000);
                for(var p:mesh.vertices) {
                    assertTrue(Float.isFinite(p[0])&&Float.isFinite(p[1]));
                    assertTrue(Math.abs(p[0])<=115&&Math.abs(p[1])<=115);
                }
                // This space was covered by the old opaque wheel plate.
                for(int i=0;i<mesh.vertices.size();i+=4) {
                    var polygon=new Path2D.Float();
                    for(int j=0;j<4;j++) {
                        var p=mesh.vertices.get(i+j);
                        if(j==0)polygon.moveTo(p[0],p[1]);else polygon.lineTo(p[0],p[1]);
                    }
                    polygon.closePath();assertFalse(polygon.contains(45,0));
                }
            }
        }
        assertEquals(6,colors.size());
        preview();
    }

    private static void preview() throws Exception {
        var image=new BufferedImage(1080,900,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        g.setColor(new Color(0x302C32));g.fillRect(0,0,1080,900);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        var mesh=new AtomicVisualTest.Mesh();
        SkillWheelScreen.emitWheel(mesh,new Matrix4f().translate(540,420,0).scale(3),5,5,.35f);
        for(int i=0;i<mesh.vertices.size();i+=4) {
            var path=new Path2D.Float();
            for(int j=0;j<4;j++) {
                var p=mesh.vertices.get(i+j);
                if(j==0)path.moveTo(p[0],p[1]);else path.lineTo(p[0],p[1]);
            }
            path.closePath();var p=mesh.vertices.get(i);
            g.setColor(new Color((int)p[3],(int)p[4],(int)p[5],(int)p[6]));g.fill(path);
        }
        g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,27));
        g.setColor(new Color(SkillWheelScreen.skillColor(5)));
        String name="I am Atomic";g.drawString(name,540-g.getFontMetrics().stringWidth(name)/2,430);
        g.setColor(new Color(0xE0D5C8));
        String hint="Release V to select";g.drawString(hint,540-g.getFontMetrics().stringWidth(hint)/2,825);
        g.dispose();Path out=Path.of("build/previews/skill-wheel.png");Files.createDirectories(out.getParent());
        ImageIO.write(image,"png",out.toFile());
    }
}
