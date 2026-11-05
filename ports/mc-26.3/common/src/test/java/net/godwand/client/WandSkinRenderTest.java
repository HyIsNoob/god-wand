package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.client.render.GodWandItemRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Switching the wand's appearance must render every skin in every display context without throwing. */
class WandSkinRenderTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void everySkinRendersInEveryContext() throws Exception {
        var client=mock(Minecraft.class,RETURNS_DEEP_STUBS);
        var stack=mock(ItemStack.class);
        var previous=GodWandConfig.CLIENT;
        GodWandConfig.CLIENT=new GodWandConfig.Client();
        try(var clients=mockStatic(Minecraft.class);var types=mockStatic(RenderTypes.class)) {
            clients.when(Minecraft::getInstance).thenReturn(client);
            var renderer=new GodWandItemRenderer();
            var buffers=Class.forName("net.godwand.client.render.GodWandItemRenderer$Buffers");
            var render=GodWandItemRenderer.class.getDeclaredMethod("render",ItemStack.class,ItemDisplayContext.class,PoseStack.class,buffers,int.class,int.class);
            render.setAccessible(true);
            for(var skin:WandSkin.values())for(var context:ItemDisplayContext.values()) {
                GodWandConfig.CLIENT.wandSkin=skin;
                var mesh=new AtomicVisualTest.Mesh();
                Object sink=java.lang.reflect.Proxy.newProxyInstance(buffers.getClassLoader(),new Class<?>[]{buffers},(proxy,method,args) -> mesh);
                try { render.invoke(renderer,stack,context,new PoseStack(),sink,0,0); }
                catch(java.lang.reflect.InvocationTargetException e) { fail(skin+" in "+context+": "+e.getCause(),e.getCause()); }
            }
        } finally {GodWandConfig.CLIENT=previous;}
    }
}
