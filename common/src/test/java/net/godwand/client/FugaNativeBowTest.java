package net.godwand.client;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class FugaNativeBowTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void targetTransformsMatchActualMinecraftLeftBowBranch() throws Exception {
  var branch=HumanoidModel.class.getDeclaredMethod("poseLeftArm",LivingEntity.class);branch.setAccessible(true);
  for(boolean slim:new boolean[]{false,true})for(float pitch:new float[]{0,.35f,-.35f})for(float yaw:new float[]{0,.4f,-.4f}){
   var model=new PlayerModel<LivingEntity>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
   model.head.xRot=pitch;model.head.yRot=yaw;model.leftArmPose=HumanoidModel.ArmPose.BOW_AND_ARROW;
   branch.invoke(model,new Object[]{null});
   var target=FugaPose.arms(0,240,pitch,yaw);
   assertEquals(model.rightArm.xRot,target.rightPitch(),.00001);assertEquals(model.leftArm.xRot,target.leftPitch(),.00001);
   assertEquals(model.rightArm.yRot,target.rightYaw(),.00001);assertEquals(model.leftArm.yRot,target.leftYaw(),.00001);
   // Only the torso turns; both arm angles remain exactly the vanilla branch.
   assertEquals(FugaPose.TWIST,target.bodyYaw(),.00001);assertTrue(target.bodyYaw()>0&&target.bodyYaw()<1.0,"side-on but not turned away from the target");
  }
 }
}
