package net.godwand.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ExtractedModelVerticesTest {
    @Test void preservesEveryModelAttributeUntilDrawing() {
        var callback = new AtomicReference<SubmitNodeCollector.CustomGeometryRenderer>();
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy,method,args) -> {
                    if(method.getName().equals("submitCustomGeometry")) callback.set((SubmitNodeCollector.CustomGeometryRenderer)args[2]);
                    return null;
                });
        var extracted = new ExtractedModelVertices();
        extracted.addVertex(1,2,3).setColor(10,20,30,40).setUv(.25f,.75f)
                .setOverlay(0x00200010).setLight(0x00400030).setNormal(0,1,0);
        extracted.submit(collector,null);
        var drawn = new AtomicReference<Object[]>();
        var sink = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy,method,args) -> {
                    if(method.getName().equals("addVertex") && args.length==11) drawn.set(args);
                    return proxy;
                });
        assertNotNull(callback.get());
        callback.get().render(new PoseStack.Pose(),sink);
        assertArrayEquals(new Object[]{1f,2f,3f,0x280A141E,.25f,.75f,0x00200010,0x00400030,0f,1f,0f},drawn.get());
    }
}
