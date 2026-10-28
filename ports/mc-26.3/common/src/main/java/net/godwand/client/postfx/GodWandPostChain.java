package net.godwand.client.postfx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.serialization.JsonOps;
import net.godwand.client.GodWandPostFxManager;
import net.godwand.mixin.PostEffectProcessorAccessor;
import net.godwand.mixin.PostPassUniformsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Owns each cinematic's native framegraph chain and its dynamic std140 uniforms. */
public final class GodWandPostChain implements AutoCloseable {
    private final Identifier id;
    private final CrossFrameResourcePool pool = new CrossFrameResourcePool(2);
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("God Wand post projection");
    private final Map<String, float[]> values = new HashMap<>();
    private PostChain chain;
    private JsonObject definition;
    private int width, height, downsample;

    public GodWandPostChain(String name) { id = Identifier.withDefaultNamespace(name); }

    public void setUniform(String name, float... value) { values.put(name, value); }
    public void setUniform(String name, Matrix4f value) { values.put(name, value.get(new float[16])); }

    public void resize(int w, int h) throws Exception {
        int divisor = net.godwand.config.GodWandConfig.CLIENT.bloomDownsample;
        if (chain != null && width == w && height == h && downsample == divisor) return;
        if (chain != null) { chain.close(); chain = null; }
        pool.clear();
        var client = Minecraft.getInstance();
        try (var reader = client.getResourceManager().openAsReader(id.withPath("post_effect/" + id.getPath() + ".json"))) {
            definition = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (var entry : definition.getAsJsonObject("targets").entrySet()) {
            if (entry.getKey().contains("emit") || entry.getKey().contains("blur")) {
                var target = entry.getValue().getAsJsonObject();
                target.addProperty("width", GodWandPostFxManager.bloomDimension(w));
                target.addProperty("height", GodWandPostFxManager.bloomDimension(h));
            }
        }
        var config = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, definition).getOrThrow();
        projection.setupOrtho(0.1f, 1000, w, h, false);
        chain = PostChain.load(config, client.getTextureManager(), Set.of(PostChain.MAIN_TARGET_ID), id, projection, projectionBuffer);
        // Vanilla's config buffers are immutable; dynamic cinematic data needs transfer writes.
        for (var pass : ((PostEffectProcessorAccessor) chain).getPasses()) {
            var uniforms = ((PostPassUniformsAccessor) pass).godwand$uniforms();
            var old = uniforms.get("GodWandConfig");
            if (old != null) {
                var dynamic = RenderSystem.getDevice().createBuffer(() -> "God Wand dynamic post uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, old.size());
                uniforms.put("GodWandConfig", dynamic);
                old.close();
            }
        }
        width = w; height = h; downsample = divisor;
    }

    public void process() {
        var passes = ((PostEffectProcessorAccessor) chain).getPasses();
        var definitions = definition.getAsJsonArray("passes");
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var stack = MemoryStack.stackPush()) {
            for (int i = 0; i < passes.size(); i++) {
                var buffer = ((PostPassUniformsAccessor) passes.get(i)).godwand$uniforms().get("GodWandConfig");
                if (buffer == null) continue;
                var builder = Std140Builder.onStack(stack, (int) buffer.size());
                for (var element : definitions.get(i).getAsJsonObject().getAsJsonObject("uniforms").getAsJsonArray("GodWandConfig")) {
                    var uniform = element.getAsJsonObject();
                    String name = uniform.get("name").getAsString();
                    float[] data = values.get(name);
                    if (data == null) {
                        var value = uniform.get("value");
                        data = new float[value.isJsonArray() ? value.getAsJsonArray().size() : 1];
                        for (int n = 0; n < data.length; n++) data[n] = value.isJsonArray() ? value.getAsJsonArray().get(n).getAsFloat() : value.getAsFloat();
                    }
                    switch (uniform.get("type").getAsString()) {
                        case "float" -> builder.putFloat(data[0]);
                        case "vec2" -> builder.putVec2(data[0], data[1]);
                        case "vec3" -> builder.putVec3(data[0], data[1], data[2]);
                        case "matrix4x4" -> builder.putMat4f(new Matrix4f().set(data));
                        default -> throw new IllegalArgumentException("Unsupported post uniform " + name);
                    }
                }
                encoder.writeToBuffer(buffer.slice(), builder.get());
            }
            encoder.submit();
        }
        chain.process(Minecraft.getInstance().gameRenderer.mainRenderTarget(), pool);
        pool.endFrame();
    }

    @Override public void close() {
        if (chain != null) chain.close();
        pool.close();
        projectionBuffer.close();
    }
}
