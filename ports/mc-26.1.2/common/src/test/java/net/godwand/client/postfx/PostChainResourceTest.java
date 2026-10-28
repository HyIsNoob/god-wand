package net.godwand.client.postfx;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.lwjgl.util.shaderc.Shaderc.*;

public class PostChainResourceTest {
    private static final Path RESOURCES = Path.of("src/main/resources/assets/minecraft");

    @Test
    void nativePostChainResources() throws Exception { verify(RESOURCES); }

    @Test
    void renderHooksMatchVanilla() throws Exception { verifyHooks(); }

    @Test
    void shadercCompilesBothDepthConventions() throws Exception {
        compileShaders(RESOURCES.resolve("shaders/program"));
    }

    // Also runnable while unrelated port sources prevent Gradle's test compilation.
    public static void main(String[] args) throws Exception {
        Path resources = Path.of(args[0]);
        verify(resources);
        verifyHooks();
        compileShaders(resources.resolve("shaders/program"));
    }

    private static void verifyHooks() throws Exception {
        try (var input = PostChainResourceTest.class.getResourceAsStream("/net/minecraft/client/renderer/GameRenderer.class")) {
            require(input != null, "vanilla GameRenderer bytecode");
            var model = java.lang.classfile.ClassFile.of().parse(input.readAllBytes());
            var render = model.methods().stream().filter(m -> m.methodName().equalsString("renderLevel")).findFirst().orElseThrow();
            int projection = -1, world = -1, hud = -1, index = 0;
            for (var element : render.code().orElseThrow()) {
                if (element instanceof java.lang.classfile.instruction.InvokeInstruction call) {
                    String target = call.owner().asInternalName() + ";" + call.name().stringValue() + call.type().stringValue();
                    if (target.equals("net/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;")) projection = index;
                    if (target.equals("net/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V")) world = index;
                    if (call.name().equalsString("render3dHud")) hud = index;
                }
                index++;
            }
            require(projection >= 0 && world > projection && hud > world, "projection/world/hand render order");
            System.out.println("Actual 26.3 render hook descriptors and ordering verified");
        }
    }

    private static void verify(Path base) throws Exception {
        int blocks = 0;
        for (String name : List.of("godwand_atomic", "godwand_nova", "godwand_impact")) {
            var json = JsonParser.parseString(Files.readString(base.resolve("post_effect/" + name + ".json"))).getAsJsonObject();
            var config = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            require(config.passes().size() == (name.equals("godwand_impact") ? 2 : 5), "pass count");
            require(PostChain.getReferencedExternalTargets(config).equals(Set.of(PostChain.MAIN_TARGET_ID)), "external targets");
            for (int i = 0; i < config.passes().size(); i++) {
                var pass = config.passes().get(i);
                String shader = Files.readString(base.resolve("shaders/" + pass.fragmentShaderId().getPath() + ".fsh"));
                for (var input : pass.inputs()) {
                    require(shader.contains("sampler2D " + input.samplerName() + "Sampler;"), "sampler " + input.samplerName());
                }
                var definitions = json.getAsJsonArray("passes").get(i).getAsJsonObject()
                        .getAsJsonObject("uniforms").getAsJsonArray("GodWandConfig");
                if (definitions == null) continue;
                int position = -1;
                for (var element : definitions) {
                    var uniform = element.getAsJsonObject();
                    String uniformName = uniform.get("name").getAsString();
                    String type = uniform.get("type").getAsString().replace("matrix4x4", "mat4");
                    int next = shader.indexOf(type + " " + uniformName + ";");
                    require(next > position, "uniform order " + uniformName);
                    position = next;
                    if (uniformName.equals("EmissionOnly")) {
                        require(uniform.get("value").getAsFloat() == (i == 1 ? 1 : 0), "emission pass");
                        require(shader.contains("godwandNdcDepth"), "backend depth conversion");
                    }
                }
                var size = new Std140SizeCalculator();
                for (var uniform : pass.uniforms().get("GodWandConfig")) uniform.addSize(size);
                var data = Std140Builder.intoBuffer(ByteBuffer.allocate(size.get()));
                for (var uniform : pass.uniforms().get("GodWandConfig")) uniform.writeTo(data);
                require(data.get().remaining() == size.get(), "std140 size");
                blocks++;
            }
            System.out.println(name + ": CODEC, topology, sampler and std140 layouts passed");
        }
        require(blocks == 11, "uniform block count");
        System.out.println(blocks + " uniform blocks verified");
    }

    private static void compileShaders(Path directory) throws Exception {
        long compiler = shaderc_compiler_initialize();
        try {
            for (boolean zeroToOne : new boolean[]{false, true}) {
                long options = shaderc_compile_options_initialize();
                try {
                    // Same Vulkan target and auto-binding options as 26.3 GlslCompiler.
                    shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
                    shaderc_compile_options_set_auto_bind_uniforms(options, true);
                    shaderc_compile_options_set_generate_debug_info(options);
                    if (zeroToOne) shaderc_compile_options_add_macro_definition(options, "RENDERPEARL_DEPTH_IS_ZERO_TO_ONE", "");
                    try (var files = Files.list(directory)) {
                        for (var file : files.filter(f -> f.toString().endsWith(".fsh")).toList()) {
                            long result = shaderc_compile_into_spv(compiler, Files.readString(file), shaderc_fragment_shader,
                                    file.getFileName().toString(), "main", options);
                            try {
                                require(shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success,
                                        shaderc_result_get_error_message(result));
                            } finally { shaderc_result_release(result); }
                        }
                    }
                } finally { shaderc_compile_options_release(options); }
            }
        } finally { shaderc_compiler_release(compiler); }
        System.out.println("All five fragment shaders compile for both depth conventions");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
