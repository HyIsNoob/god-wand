package net.godwand.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class PostCompositeHookTest {
    @Test
    void frameIsSnapshottedAndConsumedOnlyAfterTheLevelReturns() throws Exception {
        var position = new org.joml.Matrix4f().rotateY(.3f);
        var projection = new org.joml.Matrix4f();
        var pending = GodWandPostFxManager.class.getDeclaredField("pendingFrame");
        pending.setAccessible(true);
        GodWandPostFxManager.reset();
        try {
            GodWandPostFxManager.triggerImpact(0, 20, 0, net.godwand.network.ImpactFxPayload.TYPE_BLACK_HOLE, 340, 1);
            GodWandPostFxManager.onWorldRender(position, projection, 0.5f);
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

}
