package net.godwand.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Black symmetric double-edge sword with a thin diamond-ended crossguard and three-pronged pommel. */
public class ShadowBladeModel {
    private final ModelPart root;
    private float animationTime;
    private float charge;

    private static final Vector3f[][] POMMEL_QUADS = createPommelQuads();
    private static final Vector3f[][] HILT_QUADS = createHiltQuads();
    private static final Vector3f[][] TSUBA_QUADS = createTsubaQuads();
    private static final Vector3f[][] BLADE_QUADS = createBladeQuads();

    public ShadowBladeModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("blade", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int skillMode) {
        this.animationTime = time;
        this.charge = skillMode==5?chargeRatio:0;
    }

    public void renderToBuffer(PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        renderQuads(matrices, vertexConsumer, BLADE_QUADS, light, overlay, 0xFF292A30);
    }

    public void renderBlade(PoseStack matrices, Supplier<VertexConsumer> solidBuffer,
                            Supplier<VertexConsumer> emissiveBuffer, int light, int overlay, int coreColor) {
        matrices.pushPose();
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));

        VertexConsumer solid = solidBuffer.get();

        // 1. Black tsuka hilt
        renderQuads(matrices, solid, HILT_QUADS, light, overlay, 0xFF17171B);

        renderQuads(matrices, solid, POMMEL_QUADS, light, overlay, 0xFF292A30);

        // 2. Oval tsuba (crossguard)
        renderQuads(matrices, solid, TSUBA_QUADS, light, overlay, 0xFF24252B);

        // 3. Sleek black obsidian blade body
        int bladeColor=0xFF000000|((41+Math.round(charge*79))<<16)|((42-Math.round(charge*14))<<8)|(48+Math.round(charge*207));
        renderQuads(matrices, solid, BLADE_QUADS, light, overlay, bladeColor);
        if(charge>0)renderQuads(matrices,emissiveBuffer.get(),BLADE_QUADS,0x00F000F0,overlay,
                (Math.round(charge*170)<<24)|0xC77DFF);

        matrices.popPose();
    }

    private static void renderQuads(PoseStack matrices, VertexConsumer vertices, Vector3f[][] quads,
                                    int light, int overlay, int color) {
        for (int i=0;i<quads.length;i++) {
            Vector3f[] q=quads[i];
            int faceColor=color;
            int facet=(i-4)%6;
            if(quads==BLADE_QUADS&&(color>>>24)==255&&i>=4&&facet!=1&&facet!=4) {
                int r=Math.min(255,((color>>16)&255)+22),g=Math.min(255,((color>>8)&255)+22),b=Math.min(255,(color&255)+25);
                faceColor=0xFF000000|(r<<16)|(g<<8)|b;
            }
            quad(matrices, vertices, q[0], q[1], q[2], q[3], light, overlay, faceColor);
        }
    }

    private static void quad(PoseStack matrices, VertexConsumer vertices,
                             Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                             int light, int overlay, int color) {
        PoseStack.Pose entry = matrices.last();
        var pose = entry.pose();
        var normal = entry.normal();

        float v1x = p1.x() - p0.x(), v1y = p1.y() - p0.y(), v1z = p1.z() - p0.z();
        float v2x = p2.x() - p0.x(), v2y = p2.y() - p0.y(), v2z = p2.z() - p0.z();
        float nx = v1y * v2z - v1z * v2y;
        float ny = v1z * v2x - v1x * v2z;
        float nz = v1x * v2y - v1y * v2x;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-5f) { nx /= len; ny /= len; nz /= len; } else { ny = 1.0f; }

        float u0 = .80f;
        float v0 = .80f;
        float u1 = u0 + .04f, v1 = v0 + .04f;

        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        vertices.addVertex(pose, p0.x(), p0.y() + .5f, p0.z()).setColor(r, g, b, a).setUv(u0, v0)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
        vertices.addVertex(pose, p1.x(), p1.y() + .5f, p1.z()).setColor(r, g, b, a).setUv(u0, v1)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
        vertices.addVertex(pose, p2.x(), p2.y() + .5f, p2.z()).setColor(r, g, b, a).setUv(u1, v1)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
        vertices.addVertex(pose, p3.x(), p3.y() + .5f, p3.z()).setColor(r, g, b, a).setUv(u1, v0)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
    }

    private static Vector3f[][] createHiltQuads() {
        List<Vector3f[]> faces = new ArrayList<>();
        for (int i=0;i<8;i++) {
            double a=i*Math.PI/4, b=(i+1)*Math.PI/4;
            faces.add(new Vector3f[]{point((float)Math.cos(a)*.7f,-14,(float)Math.sin(a)*.8f),
                    point((float)Math.cos(b)*.7f,-14,(float)Math.sin(b)*.8f),
                    point((float)Math.cos(b)*.7f,-2,(float)Math.sin(b)*.8f),
                    point((float)Math.cos(a)*.7f,-2,(float)Math.sin(a)*.8f)});
        }
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            faces.add(new Vector3f[]{point(0,-14,0),point((float)Math.cos(b)*.7f,-14,(float)Math.sin(b)*.8f),
                    point((float)Math.cos(a)*.7f,-14,(float)Math.sin(a)*.8f),point(0,-14,0)});
        }
        return faces.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createTsubaQuads() {
        List<Vector3f[]> faces=new ArrayList<>();
        // The shallow faceted collar overlaps both blade and grip without a box-shaped socket.
        profile(faces,new float[][]{{-3.2f,0},{-2.5f,-1.7f},{.1f,-1.5f},{.6f,0},{.1f,1.5f},{-2.5f,1.7f}},.42f);
        for(int side:new int[]{-1,1}) {
            profile(faces,new float[][]{{-2.1f,0},{-1.35f,0},{-1.2f,side*6.8f},{-1.9f,side*6.8f}},.25f);
            profile(faces,new float[][]{{-1.5f,side*6.1f},{-.45f,side*7.5f},{-1.1f,side*8f},{-2.3f,side*7.2f}},.32f);
        }
        return faces.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPommelQuads() {
        List<Vector3f[]> faces=new ArrayList<>();
        profile(faces,new float[][]{{-13.4f,-.8f},{-13.4f,.8f},{-15.2f,1},{-15.2f,-1}},.48f);
        profile(faces,new float[][]{{-13.6f,0},{-15.8f,-1.15f},{-21,0},{-15.8f,1.15f}},.4f);
        for(int side:new int[]{-1,1})
            profile(faces,new float[][]{{-15.3f,side*.6f},{-14.2f,side*2.8f},{-14.8f,side*3.2f},{-16.8f,side*1.1f}},.28f);
        return faces.toArray(new Vector3f[0][]);
    }

    private static void profile(List<Vector3f[]> faces,float[][] outline,float thickness) {
        float cy=0,cz=0,area=0;
        for(int i=0;i<outline.length;i++) {
            float[] p=outline[i],next=outline[(i+1)%outline.length];
            cy+=p[0]/outline.length;cz+=p[1]/outline.length;
            area+=p[0]*next[1]-next[0]*p[1];
        }
        for(int i=0;i<outline.length;i++) {
            float[] a=outline[area>=0?i:(i+1)%outline.length],b=outline[area>=0?(i+1)%outline.length:i];
            Vector3f edgeA=point(0,a[0],a[1]),edgeB=point(0,b[0],b[1]);
            for(int side:new int[]{-1,1}) {
                Vector3f insetA=point(side*thickness,cy+(a[0]-cy)*.76f,cz+(a[1]-cz)*.76f);
                Vector3f insetB=point(side*thickness,cy+(b[0]-cy)*.76f,cz+(b[1]-cz)*.76f);
                if(side>0) {
                    faces.add(new Vector3f[]{edgeA,edgeB,insetB,insetA});
                    faces.add(new Vector3f[]{point(thickness,cy,cz),insetA,insetB,point(thickness,cy,cz)});
                } else {
                    faces.add(new Vector3f[]{edgeB,edgeA,insetA,insetB});
                    faces.add(new Vector3f[]{point(-thickness,cy,cz),insetB,insetA,point(-thickness,cy,cz)});
                }
            }
        }
    }

    private static Vector3f[][] createBladeQuads() {
        List<Vector3f[]> faces=new ArrayList<>();
        float[][] sections={{-2.4f,1.615f},{30,1.46f},{40,1.05f},{47,0}};
        Vector3f[] previous=null;
        for(float[] section:sections) {
            float y=section[0],w=section[1],t=w==0?0:.26f;
            Vector3f[] ring={point(0,y,-w),point(t,y,-w*.58f),point(t,y,w*.58f),
                    point(0,y,w),point(-t,y,w*.58f),point(-t,y,-w*.58f)};
            if(previous==null)for(int j=1;j<ring.length-1;j++)
                faces.add(new Vector3f[]{ring[0],ring[j+1],ring[j],ring[j]});
            if(previous!=null)for(int j=0;j<ring.length;j++) {
                int next=(j+1)%ring.length;
                faces.add(new Vector3f[]{previous[j],previous[next],ring[next],ring[j]});
            }
            previous=ring;
        }
        return faces.toArray(new Vector3f[0][]);
    }

    private static Vector3f point(float x,float y,float z) { return new Vector3f(x/16,y/16,z/16); }
}

