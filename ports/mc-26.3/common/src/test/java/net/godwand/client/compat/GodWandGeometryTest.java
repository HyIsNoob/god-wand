package net.godwand.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class GodWandGeometryTest {
    @Test void submitsVisibleGeometryWithTheNativeDrawArgumentOrder() throws Exception {
        var client=mock(Minecraft.class,RETURNS_DEEP_STUBS);
        var renderer=Minecraft.class.getDeclaredField("gameRenderer");
        renderer.setAccessible(true);
        renderer.set(client,mock(net.minecraft.client.renderer.GameRenderer.class,RETURNS_DEEP_STUBS));
        var device=mock(GpuDevice.class,RETURNS_DEEP_STUBS);
        var encoder=mock(CommandEncoder.class);
        var pass=mock(RenderPass.class);
        var sequential=mock(RenderSystem.AutoStorageIndexBuffer.class,RETURNS_DEEP_STUBS);
        var uniforms=mock(DynamicGpuData.class,RETURNS_DEEP_STUBS);
        when(device.createCommandEncoder()).thenReturn(encoder);
        when(encoder.createRenderPass(any(),any(),any(),any(),any())).thenReturn(pass);
        try(var clients=mockStatic(Minecraft.class);var render=mockStatic(RenderSystem.class)) {
            clients.when(Minecraft::getInstance).thenReturn(client);
            render.when(RenderSystem::getDevice).thenReturn(device);
            render.when(RenderSystem::getDynamicUniforms).thenReturn(uniforms);
            render.when(() -> RenderSystem.getSequentialBuffer(any())).thenReturn(sequential);
            GodWandGeometry.enableBlend();GodWandGeometry.additiveBlend();
            GodWandGeometry.disableCull();GodWandGeometry.depthMask(false);GodWandGeometry.colored();
            var vertices=GodWandGeometry.begin(PrimitiveTopology.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int i=0;i<4;i++) vertices.addVertex(i%2,i/2,-2).setColor(255,255,255,255);
            GodWandGeometry.draw(vertices.buildOrThrow());
            GodWandGeometry.flush(new Matrix4f());
            // RenderPearl: index count, instance count, first index, vertex offset, first instance.
            verify(pass).drawIndexed(6,1,0,0,0);
            verify(pass).close();verify(encoder).submit();
            render.verify(() -> RenderSystem.queueFencedTask(any()));
        } finally {
            GodWandGeometry.reset();GodWandGeometry.depthMask(true);
            GodWandGeometry.enableCull();GodWandGeometry.defaultBlendFunc();GodWandGeometry.disableBlend();
        }
    }
}
