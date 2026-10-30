package net.godwand.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.shaders.UniformType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** Extracts authored meshes, then uploads and submits them on the world drawing stage. */
public final class GodWandGeometry {
    private record Key(VertexFormat.Mode topology, boolean texture, boolean blend,
                       boolean additive, boolean depth, boolean writeDepth, boolean cull) {}
    private record Draw(MeshData mesh, Key key, Identifier texture, Vector4f color) {}
    private static final ByteBufferBuilder ARENA = new ByteBufferBuilder(2 * 1024 * 1024);
    private static final List<Draw> PENDING = new ArrayList<>();
    private static final Map<Key, RenderPipeline> PIPELINES = new HashMap<>();
    private static boolean blend, additive, depth = true, writeDepth = true, cull = true, texture;
    private static Identifier textureId;
    private static Vector4f color = new Vector4f(1);

    public static BufferBuilder begin(VertexFormat.Mode topology, VertexFormat format) {
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
            String shader = k.texture ? "core/position_tex_color" : "core/position_color";
            var builder = RenderPipeline.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withLocation(Identifier.fromNamespaceAndPath("godwand", "geometry/" + PIPELINES.size()))
                    .withVertexShader(shader).withFragmentShader(shader)
                    .withVertexFormat(k.texture ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR, k.topology)
                    .withCull(k.cull).withDepthWrite(k.writeDepth)
                    .withDepthTestFunction(k.depth ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST);
            if (k.texture) builder.withSampler("Sampler0");
            if (k.blend) builder.withBlend(k.additive
                    ? new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE)
                    : BlendFunction.TRANSLUCENT);
            else builder.withoutBlend();
            return builder.build();
        });
    }

    /** Register all authored state combinations before resource shader compilation. */
    public static void initialize() { initialize(RenderPipelines::register); }
    public static void initialize(java.util.function.Consumer<RenderPipeline> register) {
        for (var topology : new VertexFormat.Mode[]{VertexFormat.Mode.QUADS, VertexFormat.Mode.TRIANGLES,
                VertexFormat.Mode.TRIANGLE_STRIP, VertexFormat.Mode.TRIANGLE_FAN})
            for (int style = 0; style < 3; style++)
                for (boolean writing : new boolean[]{false,true})
                    pipeline(new Key(topology,false,style != 0,style == 2,true,writing,false));
        pipeline(new Key(VertexFormat.Mode.QUADS,true,true,true,true,false,false));
        PIPELINES.values().forEach(register);
    }

    public static void draw(MeshData mesh) {
        if (mesh == null) return;
        var key = new Key(mesh.drawState().mode(), texture, blend, additive, depth, writeDepth, cull);
        PENDING.add(new Draw(mesh,key,textureId,new Vector4f(color)));
    }

    public static void reset() {
        for (Draw draw : PENDING) draw.mesh.close();
        PENDING.clear();
        ARENA.clear();
    }

    public static void flush() { flush(RenderSystem.getModelViewMatrix()); }
    public static void flush(org.joml.Matrix4fc view) {
        if (PENDING.isEmpty()) return;
        var client = Minecraft.getInstance();
        var target = client.getMainRenderTarget();
        var device = RenderSystem.getDevice();
        try {
            for (Draw draw : PENDING) {
                var state = draw.mesh.drawState();
                var vertices = device.createBuffer(() -> "God Wand frame geometry", GpuBuffer.USAGE_VERTEX, draw.mesh.vertexBuffer());
                try {
                    var sequential = RenderSystem.getSequentialBuffer(state.mode());
                    var indices = sequential.getBuffer(state.indexCount());
                    var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(view), draw.color, new org.joml.Vector3f(), new Matrix4f());
                    try (var pass = device.createCommandEncoder().createRenderPass(() -> "God Wand procedural geometry",
                            target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                        pass.setPipeline(pipeline(draw.key));
                        pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                        pass.setUniform("DynamicTransforms", transforms);
                        if (draw.key.texture) {
                            var sampled = client.getTextureManager().getTexture(Objects.requireNonNull(draw.texture));
                            pass.bindTexture("Sampler0", sampled.getTextureView(), sampled.getSampler());
                        }
                        pass.setVertexBuffer(0, vertices);
                        pass.setIndexBuffer(indices, sequential.type());
                        pass.drawIndexed(0, 0, state.indexCount(), 1);
                    }
                } finally { RenderSystem.queueFencedTask(vertices::close); }
            }
        } finally { reset(); }

    }
}
