package net.godwand.fabric.client;

import com.mojang.blaze3d.vertex.*;
import net.godwand.client.FugaAimRig;
import net.godwand.client.FugaRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Fabric 1.21.1 prototype bridge. Original skin quads are clipped; UVs are interpolated, never replaced. */
public final class FugaAimArms {
    private FugaAimArms() {}
    public static boolean eligible(EntityModel<?> model,LivingEntity entity,float partial) {
        var f=FugaRenderer.frameFor(entity.getId(),partial);
        // Replacement player renderers/armor retain their own arm geometry.
        return model.getClass()==PlayerModel.class && entity instanceof AbstractClientPlayer
                && entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && !entity.isBaby()
                && f!=null && f.phase()==0 && FugaAimRig.weight(f.age())>0;
    }
    public static void render(EntityModel<?> model,LivingEntity entity,float partial,PoseStack pose,
                              VertexConsumer output,int light,int overlay,int color) {
        if(!eligible(model,entity,partial)) { model.renderToBuffer(pose,output,light,overlay,color); return; }
        var player=(PlayerModel<?>)model;
        var f=FugaRenderer.frameFor(entity.getId(),partial);
        renderOwned(player,f.age(),player.head.xRot,pose,output,light,overlay,color);
    }
    static void renderOwned(PlayerModel<?> player,double age,float pitch,PoseStack pose,VertexConsumer output,int light,int overlay,int color) {
        ModelPart[] parts={player.rightArm,player.rightSleeve};
        boolean[] visible=new boolean[2];
        for(int i=0;i<2;i++){visible[i]=parts[i].visible;parts[i].visible=false;}
        try {
            player.renderToBuffer(pose,output,light,overlay,color);
            for(int i=0;i<2;i++)if(visible[i]) draw(parts[i],true,age,pitch,pose,output,light,overlay,color);
        } finally {for(int i=0;i<2;i++)parts[i].visible=visible[i];}
    }
    private record Vertex(Vec3 p,float u,float v) {
        Vertex lerp(Vertex b,double t) {return new Vertex(p.lerp(b.p,t),(float)(u+(b.u-u)*t),(float)(v+(b.v-v)*t));}
    }
    static void draw(ModelPart part,boolean right,double age,float pitch,PoseStack pose,VertexConsumer out,int light,int overlay,int color) {
        if(part.skipDraw)return;
        pose.pushPose();
        try {
            // Rig coordinates already contain shoulder placement, so use the player's model transform only.
            var original=new PoseStack();part.translateAndRotate(original);
            part.visit(new PoseStack(),(unused,path,index,cube)->{
                var faces=new ArrayList<List<Vertex>>();
                cube.compile(new PoseStack().last(),new Capture(faces),light,overlay,color);
                double cx=(cube.minX+cube.maxX)*.5,cz=(cube.minZ+cube.maxZ)*.5;
                List<Vertex> top=faces.stream().filter(face->face.stream().allMatch(v->Math.abs(v.p.y-face.get(0).p.y)<.001)).min(java.util.Comparator.comparingDouble(face->face.get(0).p.y)).orElseThrow();
                List<Vertex> bottom=faces.stream().filter(face->face.stream().allMatch(v->Math.abs(v.p.y-face.get(0).p.y)<.001)).max(java.util.Comparator.comparingDouble(face->face.get(0).p.y)).orElseThrow();
                var topCap=top.stream().map(v->new Vertex(new Vec3(v.p.x,2,v.p.z),v.u,v.v)).toList();
                var bottomCap=bottom.stream().map(v->new Vertex(new Vec3(v.p.x,2,v.p.z),v.u,v.v)).toList();
                faces.add(topCap);faces.add(bottomCap);
                double[] cuts={-100,2,100};
                for(var face:faces)for(int segment=0;segment<2;segment++) {
                    if(face==topCap&&segment!=1 || face==bottomCap&&segment!=0)continue;
                    List<Vertex> polygon=clip(clip(face,cuts[segment],true),cuts[segment+1],false);
                    if(polygon.size()<3)continue;
                    // Triangle fans encoded as degenerate quads preserve the current QUADS buffer contract.
                    for(int t=1;t+1<polygon.size();t++) {
                        Vertex[] quad={polygon.get(0),polygon.get(t),polygon.get(t+1),polygon.get(t+1)};
                        Vec3[] points=new Vec3[4];
                        for(int k=0;k<4;k++) {
                            var vertex=quad[k];
                            Vec3 rig=deform(vertex.p,segment,right,age,pitch,cx,cz,segment==0?cube.minY:cuts[segment],segment==1?cube.maxY:cuts[segment+1]);
                            Vector3f vanilla=original.last().pose().transformPosition((float)vertex.p.x/16,(float)vertex.p.y/16,(float)vertex.p.z/16,new Vector3f());
                            points[k]=new Vec3(vanilla.x,vanilla.y,vanilla.z).lerp(rig.scale(1.0/16),FugaAimRig.weight(age));
                        }
                        Vec3 n=points[1].subtract(points[0]).cross(points[2].subtract(points[0])).normalize();
                        for(int k=0;k<4;k++)out.addVertex(pose.last(),(float)points[k].x,(float)points[k].y,(float)points[k].z)
                                .setColor(color).setUv(quad[k].u,quad[k].v).setOverlay(overlay).setLight(light)
                                .setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);
                    }
                }
            });
        } finally {pose.popPose();}
    }
    private static Vec3 deform(Vec3 p,int section,boolean right,double age,float pitch,double cx,double cz,double low,double high) {
        var j=FugaAimRig.joints(age,pitch);
        Vec3 a=section==0?j.shoulder():j.elbow();
        Vec3 b=section==0?j.elbow():j.tip();
        // Keep a stable top/bottom face instead of rolling the square section around the elbow.
        Vec3 axis=b.subtract(a).normalize(),across=axis.cross(new Vec3(0,1,0)).normalize();
        if(across.lengthSqr()<.0001)across=new Vec3(1,0,0);
        Vec3 up=across.cross(axis).normalize();
        return a.lerp(b,(p.y-low)/(high-low)).add(across.scale(p.x-cx)).add(up.scale(p.z-cz));
    }
    private static List<Vertex> clip(List<Vertex> input,double y,boolean above) {
        var result=new ArrayList<Vertex>();if(input.isEmpty())return result;
        Vertex a=input.get(input.size()-1);boolean ai=above?a.p.y>=y:a.p.y<=y;
        for(var b:input) {
            boolean bi=above?b.p.y>=y:b.p.y<=y;
            if(ai!=bi)result.add(a.lerp(b,(y-a.p.y)/(b.p.y-a.p.y)));
            if(bi)result.add(b);a=b;ai=bi;
        }
        return result;
    }
    private static final class Capture implements VertexConsumer {
        final List<List<Vertex>> faces;final List<Vertex> pending=new ArrayList<>();Vec3 position;
        Capture(List<List<Vertex>> faces){this.faces=faces;}
        public VertexConsumer addVertex(float x,float y,float z){position=new Vec3(x*16,y*16,z*16);return this;}
        public VertexConsumer setUv(float u,float v){pending.add(new Vertex(position,u,v));if(pending.size()==4){faces.add(List.copyOf(pending));pending.clear();}return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv1(int a,int b){return this;}
        public VertexConsumer setUv2(int a,int b){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
