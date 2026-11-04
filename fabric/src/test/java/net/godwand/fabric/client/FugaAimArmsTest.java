package net.godwand.fabric.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FugaAimArmsTest {
 private static PlayerModel<?> model(boolean slim) {return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);}
 @Test void classicSlimAndSleevesKeepOriginalUvRegionsAndFiniteGeometry() {
  for(boolean slim:new boolean[]{false,true}) {
   var model=model(slim);
   for(var part:List.of(model.rightArm,model.rightSleeve)) {
    var source=new Mesh();part.visit(new PoseStack(),(pose,path,index,cube)->cube.compile(new PoseStack().last(),source,0,0,-1));
    for(double age:new double[]{0,48,72,110,144}) {
     var mesh=new Mesh();FugaAimArms.draw(part,part==model.rightArm||part==model.rightSleeve,age,0,new PoseStack(),mesh,0,0,-1);
     assertFalse(mesh.vertices.isEmpty());assertEquals(0,mesh.vertices.size()%4);
     for(var p:mesh.vertices) {
      for(float n:p)assertTrue(Float.isFinite(n));
      for(int axis=3;axis<=4;axis++) {
       final int a=axis;
       assertTrue(p[a]>=source.vertices.stream().mapToDouble(v->v[a]).min().orElseThrow()-.0001);
       assertTrue(p[a]<=source.vertices.stream().mapToDouble(v->v[a]).max().orElseThrow()+.0001);
      }
     }
    }
   }
  }
 }
 @Test void visibilityRestoresEvenWhenRenderingThrows() {
  var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot();
  var model=new PlayerModel<>(root,false) {
   @Override public void renderToBuffer(PoseStack pose,VertexConsumer v,int light,int overlay,int color){throw new IllegalStateException("probe failure");}
  };
  model.rightSleeve.visible=false;
  assertThrows(IllegalStateException.class,()->FugaAimArms.renderOwned(model,200,0,new PoseStack(),new Mesh(),0,0,-1));
  assertTrue(model.rightArm.visible);assertTrue(model.leftArm.visible);assertTrue(model.leftSleeve.visible);assertFalse(model.rightSleeve.visible);
 }
 static class Mesh implements VertexConsumer {
  final List<float[]> vertices=new ArrayList<>();float[] last;
  public VertexConsumer addVertex(float x,float y,float z){last=new float[]{x,y,z,0,0};vertices.add(last);return this;}
  public VertexConsumer setUv(float u,float v){last[3]=u;last[4]=v;return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv1(int a,int b){return this;}
  public VertexConsumer setUv2(int a,int b){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
 }
}
