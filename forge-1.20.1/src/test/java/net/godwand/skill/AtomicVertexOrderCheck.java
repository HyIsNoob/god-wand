package net.godwand.skill;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.client.AtomicRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class AtomicVertexOrderCheck {
    public static void verify() {
        for(int age:new int[]{36,174,245,398,474,674}) {
            Mesh mesh=new Mesh();
            AtomicRenderer.emit(mesh,new Matrix4f(),age,new Vec3(0,2,160));
            if(mesh.stage!=0 || mesh.vertices==0)throw new AssertionError("Incomplete Atomic mesh at "+age);
        }
    }
    // Minecraft 1.20.1's POSITION_TEX_COLOR builder consumes attributes in this exact order.
    private static final class Mesh implements VertexConsumer {
        int stage, vertices;
        private void next(int expected) { if(stage!=expected)throw new AssertionError("Vertex attribute order: expected "+stage+", got "+expected); stage++; }
        public VertexConsumer vertex(double x,double y,double z){next(0);return this;}
        public VertexConsumer uv(float u,float v){next(1);return this;}
        public VertexConsumer color(int r,int g,int b,int a){next(2);return this;}
        public void endVertex(){next(3);stage=0;vertices++;}
        public VertexConsumer overlayCoords(int u,int v){throw new AssertionError("Unexpected overlay");}
        public VertexConsumer uv2(int u,int v){throw new AssertionError("Unexpected light");}
        public VertexConsumer normal(float x,float y,float z){throw new AssertionError("Unexpected normal");}
        public void defaultColor(int r,int g,int b,int a){throw new AssertionError("Unexpected default color");}
        public void unsetDefaultColor(){}
    }
}
