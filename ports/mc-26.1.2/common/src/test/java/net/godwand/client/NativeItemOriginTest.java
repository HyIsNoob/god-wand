package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.client.model.GodWandModel;
import net.godwand.client.render.GodWandItemRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeItemOriginTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void nativeItemLayerKeepsTheCanonicalPositionAndSize() throws Exception {
        var client=mock(Minecraft.class,RETURNS_DEEP_STUBS);
        var stack=mock(ItemStack.class);
        var previous=GodWandConfig.CLIENT;
        GodWandConfig.CLIENT=new GodWandConfig.Client();
        GodWandConfig.CLIENT.wandSkin=WandSkin.DEFAULT;
        try(var clients=mockStatic(Minecraft.class)) {
            clients.when(Minecraft::getInstance).thenReturn(client);
            try(var types=mockStatic(RenderTypes.class)) {
                var renderer=new GodWandItemRenderer();
                var buffers=Class.forName("net.godwand.client.render.GodWandItemRenderer$Buffers");
                var render=GodWandItemRenderer.class.getDeclaredMethod("render",ItemStack.class,ItemDisplayContext.class,PoseStack.class,buffers,int.class,int.class);
                render.setAccessible(true);
                var display=GodWandItemRenderer.class.getDeclaredMethod("applyDisplayTransform",PoseStack.class,ItemDisplayContext.class);
                display.setAccessible(true);
                for(var context:new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.GUI}) {
                    var actualPose=new PoseStack();var expectedPose=new PoseStack();
                    if(context.firstPerson()) {
                        float hand=context.leftHand()?-1:1;
                        actualPose.translate(hand*.56f,-.52f,-.72f);
                        expectedPose.translate(hand*.56f,-.52f,-.72f);
                    }
                    ItemTransform.NO_TRANSFORM.apply(context.leftHand(),actualPose.last());
                    var actual=new AtomicVisualTest.Mesh();
                    Object sink=java.lang.reflect.Proxy.newProxyInstance(buffers.getClassLoader(),new Class<?>[]{buffers},(proxy,method,args) -> actual);
                    render.invoke(renderer,stack,context,actualPose,sink,0,0);
                    display.invoke(renderer,expectedPose,context);
                    var expected=new AtomicVisualTest.Mesh();
                    var model=new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
                    model.setupProceduralAnimations(0,0,0);
                    model.renderWand(expectedPose,() -> expected,() -> expected,0,0,GodWandItemRenderer.coreColor(0,0));
                    assertFalse(actual.vertices.isEmpty());
                    assertEquals(expected.vertices.size(),actual.vertices.size(),context.toString());
                    for(int i=0;i<actual.vertices.size();i++)for(int axis=0;axis<3;axis++)
                        assertEquals(expected.vertices.get(i)[axis],actual.vertices.get(i)[axis],.00001,context+" vertex "+i+" axis "+axis);
                }
            }
        } finally {GodWandConfig.CLIENT=previous;}
    }
}
