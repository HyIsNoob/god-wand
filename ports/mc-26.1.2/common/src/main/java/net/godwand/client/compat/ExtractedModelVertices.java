package net.godwand.client.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.*;

/** Captures model vertices during extraction; vanilla submits each material in its drawing stage. */
public final class ExtractedModelVertices implements VertexConsumer {
    private static final class Vertex {
        float x,y,z,u,v,nx,ny,nz,uv3x,uv3y,lineWidth;
        int color = -1, overlay, light;
    }
    private final List<Vertex> vertices = new ArrayList<>();
    private Vertex current;
    public VertexConsumer addVertex(float x,float y,float z) {
        current = new Vertex(); current.x=x;current.y=y;current.z=z; vertices.add(current); return this;
    }
    public VertexConsumer setColor(int r,int g,int b,int a) { return setColor(a<<24|r<<16|g<<8|b); }
    public VertexConsumer setColor(int color) { current.color=color;return this; }
    public VertexConsumer setUv(float u,float v) { current.u=u;current.v=v;return this; }
    public VertexConsumer setUv1(int u,int v) { current.overlay=(u&65535)|(v<<16);return this; }
    public VertexConsumer setUv2(int u,int v) { current.light=(u&65535)|(v<<16);return this; }
    public VertexConsumer setUv3(float x,float y) { current.uv3x=x;current.uv3y=y;return this; }
    public VertexConsumer setNormal(float x,float y,float z) { current.nx=x;current.ny=y;current.nz=z;return this; }
    public VertexConsumer setLineWidth(float width) { current.lineWidth=width;return this; }
    public void submit(SubmitNodeCollector collector, RenderType type) {
        if(vertices.isEmpty()) return;
        var snapshot = List.copyOf(vertices);
        collector.submitCustomGeometry(new PoseStack(),type,(pose,out) -> {
            for(Vertex vertex:snapshot)
                out.addVertex(vertex.x,vertex.y,vertex.z,vertex.color,vertex.u,vertex.v,
                        vertex.overlay,vertex.light,vertex.nx,vertex.ny,vertex.nz);
        });
    }
}
