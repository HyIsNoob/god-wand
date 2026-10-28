package net.godwand.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** Extracts authored meshes, then uploads and submits them on the world drawing stage. */
public final class GodWandGeometry {
    private record Key(PrimitiveTopology topology, boolean texture, boolean blend,
                       boolean additive, boolean depth, boolean writeDepth, boolean cull) {}
    private record Draw(MeshData mesh, Key key, Identifier texture, Vector4f color) {}
    private static final ByteBufferBuilder ARENA = new ByteBufferBuilder(2 * 1024 * 1024);
    private static final List<Draw> PENDING = new ArrayList<>();
    private static final Map<Key, RenderPipeline> PIPELINES = new HashMap<>();
    private static boolean blend, additive, depth = true, writeDepth = true, cull = true, texture;
    private static Identifier textureId;
    private static Vector4f color = new Vector4f(1);

    public static BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        return new BufferBuilder(ARENA, topology, format);
    }
    public static void enableBlend() { blend = true; }
    public static void disableBlend() { blend = false; }
    public static void defaultBlendFunc() { additive = false; }
    public static void additiveBlend() { additive = true; }
    public static void enableDepthTest() { depth = true; }
    public static void disableDepthTest() { depth = false; }
    public static void depthMask(boolean value) { writeDepth = value; }
    public static void enableCull() { cull = true; }
    public static void disableCull() { cull = false; }
    public static void colored() { texture = false; }
    public static void textured() { texture = true; }
    public static void setShaderTexture(int unit, Identifier id) {
        if (unit != 0) throw new IllegalArgumentException("God Wand geometry has one texture sampler");
        textureId = id;
    }
    public static void setShaderColor(float r, float g, float b, float a) { color = new Vector4f(r,g,b,a); }

    private static RenderPipeline pipeline(Key key) {
        return PIPELINES.computeIfAbsent(key, k -> {
            var layout = BindGroupLayout.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER);
            if (k.texture) layout.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER);
            String shader = k.texture ? "core/position_tex_color" : "core/position_color";
            var builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("godwand", "geometry/" + PIPELINES.size()))
                    .withVertexShader(shader).withFragmentShader(shader)
                    .withBindGroupLayout(layout.build())
                    .withVertexBinding(0, k.texture ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(k.topology).withCull(k.cull)
                    .withColorTargetState(k.blend ? new ColorTargetState(k.additive
                            ? new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)
                            : BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
                    .withDepthStencilState(new DepthStencilState(k.depth ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, k.writeDepth));
            return RenderPipelines.register(builder.build());
        });
    }

    /** Register all authored state combinations before resource shader compilation. */
    public static void initialize() {
        for (var topology : new PrimitiveTopology[]{PrimitiveTopology.QUADS, PrimitiveTopology.TRIANGLES,
                PrimitiveTopology.TRIANGLE_STRIP, PrimitiveTopology.TRIANGLE_FAN})
            for (int style = 0; style < 3; style++)
                for (boolean writing : new boolean[]{false,true})
                    pipeline(new Key(topology,false,style != 0,style == 2,true,writing,false));
        pipeline(new Key(PrimitiveTopology.QUADS,true,true,true,true,false,false));
    }

    public static void draw(MeshData mesh) {
        if (mesh == null) return;
        var key = new Key(mesh.drawState().primitiveTopology(), texture, blend, additive, depth, writeDepth, cull);
        PENDING.add(new Draw(mesh,key,textureId,new Vector4f(color)));
    }

    public static void reset() {
        for (Draw draw : PENDING) draw.mesh.close();
        PENDING.clear();
        ARENA.clear();
    }

    public static void flush() {
        if (PENDING.isEmpty()) return;
        var client = Minecraft.getInstance();
        var target = client.gameRenderer.mainRenderTarget();
        var device = RenderSystem.getDevice();
        int byteCount = PENDING.stream().mapToInt(draw -> draw.mesh.vertexBuffer().remaining()).sum();
        var upload = org.lwjgl.system.MemoryUtil.memAlloc(byteCount);
        GpuBuffer vertices = null;
        try {
            for (Draw draw : PENDING) upload.put(draw.mesh.vertexBuffer());
            upload.flip();
            vertices = device.createBuffer(() -> "God Wand frame geometry", GpuBuffer.USAGE_VERTEX, upload);
            var encoder = device.createCommandEncoder();
            long offset = 0;
            for (Draw draw : PENDING) {
                var state = draw.mesh.drawState();
                int size = state.vertexCount() * state.format().getVertexSize();
                var sequential = RenderSystem.getSequentialBuffer(state.primitiveTopology());
                var indices = sequential.getBuffer(state.indexCount());
                var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), draw.color, new org.joml.Vector3f(), new Matrix4f());
                try (var pass = encoder.createRenderPass(() -> "God Wand procedural geometry", target.getColorTextureView(), Optional.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(draw.key)));
                    pass.setUniform("Projection",RenderSystem.getProjectionMatrixBuffer());
                    pass.setUniform("DynamicTransforms",transforms);
                    if (draw.key.texture) {
                        var sampled = client.getTextureManager().getTexture(Objects.requireNonNull(draw.texture));
                        pass.setUniform("Sampler0",sampled.getTextureView(),sampled.getSampler());
                    }
                    pass.setVertexBuffer(0,vertices.slice(offset,size));
                    pass.setIndexBuffer(indices,sequential.type());
                    pass.drawIndexed(0,0,state.indexCount(),1,0);
                }
                offset += size;
            }
            encoder.submit();
        } finally {
            if (vertices != null) {
                GpuBuffer submitted = vertices;
                RenderSystem.queueFencedTask(submitted::close);
            }
            org.lwjgl.system.MemoryUtil.memFree(upload);
            reset();
        }
    }
}
