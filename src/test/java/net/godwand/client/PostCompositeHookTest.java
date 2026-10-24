package net.godwand.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class PostCompositeHookTest {
    @Test
    void frameIsSnapshottedAndConsumedOnlyAfterTheWorldReturns() throws Exception {
        var position = new org.joml.Matrix4f().rotateY(.3f);
        var projection = new org.joml.Matrix4f();
        var ticks = (net.minecraft.client.render.RenderTickCounter) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.minecraft.client.render.RenderTickCounter.class},
                (proxy, method, args) -> .5f);
        var context = (net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "positionMatrix" -> position;
                    case "projectionMatrix" -> projection;
                    case "tickCounter" -> ticks;
                    default -> null;
                });
        var pending = GodWandPostFxManager.class.getDeclaredField("pendingFrame");
        pending.setAccessible(true);
        GodWandPostFxManager.reset();
        try {
            GodWandPostFxManager.triggerImpact(0, 20, 0, net.godwand.network.ImpactFxPayload.TYPE_BLACK_HOLE, 340, 1);
            GodWandPostFxManager.onWorldRender(context);
            Object snapshot = pending.get(null);
            assertNotNull(snapshot);
            var accessor = snapshot.getClass().getDeclaredMethod("positionMatrix");
            accessor.setAccessible(true);
            var captured = new org.joml.Matrix4f(position);
            position.identity();
            assertEquals(captured, accessor.invoke(snapshot));
            GodWandPostFxManager.renderAfterWorld();
            assertNull(pending.get(null));
            assertDoesNotThrow(GodWandPostFxManager::renderAfterWorld);
        } finally {
            GodWandPostFxManager.reset();
        }
    }

    @Test
    void worldCompositeHookExistsBeforeVanillaClearsDepthForTheHand() throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = getClass().getResourceAsStream("/net/minecraft/client/render/GameRenderer.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var world = node.methods.stream().filter(m -> m.name.equals("renderWorld")).findFirst().orElseThrow();
        int calls = 0;
        boolean seenWorld = false, seenHand = false;
        for (var instruction : world.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.owner.equals("net/minecraft/client/render/WorldRenderer") && call.name.equals("render")) {
                calls++;
                seenWorld = true;
            }
            if (call.name.equals("renderHand")) {
                assertTrue(seenWorld);
                seenHand = true;
            }
        }
        assertEquals(1, calls);
        assertTrue(seenHand);
    }
}
