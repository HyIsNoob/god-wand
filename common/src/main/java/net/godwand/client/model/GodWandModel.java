package net.godwand.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Supplier;

public class GodWandModel extends Model {
    private final ModelPart root, obsidian, gold, leather, wrapping, singularityCore, orbit;
    private float charge, animationTime;
    private static final org.joml.Vector3f[][] CRESCENT = createCrescent();

    public GodWandModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        obsidian = root.getChild("obsidian");
        gold = root.getChild("gold");
        leather = root.getChild("leather");
        wrapping = root.getChild("wrapping");
        singularityCore = root.getChild("singularity_core");
        orbit = root.getChild("orbit");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        var body = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-.82f, -36, -.82f, 1.64f, 42, 1.64f)
                .texOffs(0, 0).addBox(-.72f, 6, -.72f, 1.44f, 4.5f, 1.44f);
        root.addOrReplaceChild("obsidian", body, PartPose.ZERO);
        var trim = CubeListBuilder.create();
        for (float y : new float[]{-35.8f, -6, 5.5f}) {
            float width = y == -35.8f ? 1.9f : 2.3f;
            trim.texOffs(0, 0).addBox(-width / 2, y, -width / 2, width, .4f, width);
        }
        root.addOrReplaceChild("gold", trim, PartPose.ZERO);
        // Both hands hold the origin, in the middle of the thicker red leather sleeve.
        root.addOrReplaceChild("leather", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.025f, -5.5f, -1.025f, 2.05f, 11, 2.05f), PartPose.ZERO);
        var wraps = CubeListBuilder.create();
        for (int band = 0; band < 10; band++) {
            wraps.texOffs(0, 0).addBox(-1.055f, -5.2f + band * 1.1f, -1.055f, 2.11f, .12f, 2.11f);
        }
        root.addOrReplaceChild("wrapping", wraps, PartPose.ZERO);
        root.addOrReplaceChild("singularity_core", CubeListBuilder.create(), PartPose.offset(-.55f, 21.2f, 0));
        root.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offset(-.55f, 21.2f, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int activeSkillId) {
        animationTime = time;
        charge = Math.clamp(chargeRatio, 0, 1);
        float speed = switch (activeSkillId) {
            case 1 -> .055f + charge * .15f;
            case 2 -> .035f + charge * .1f;
            case 3 -> .025f + charge * .2f;
            case 4 -> .08f + charge * .28f;
            default -> .03f;
        };
        singularityCore.yRot = time * speed;
        singularityCore.xRot = (float)Math.sin(time * .04f) * .12f + charge * (activeSkillId == 2 ? .65f : .2f);
        singularityCore.zRot = (float)Math.sin(time * .03f) * .08f;
        float size = activeSkillId == 3 ? 1 - .45f * charge * charge : 1 + charge * .12f;
        singularityCore.xScale = singularityCore.yScale = singularityCore.zScale = size;
        orbit.yRot = time * (.045f + charge * .12f);
        orbit.xRot = .65f;
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();
        obsidian.render(matrices, solid, light, overlay, 0xFF24212E);
        renderCrescent(matrices, solid, light, overlay);
        renderNeck(matrices, solid, light, overlay);
        gold.render(matrices, solid, light, overlay, 0xFFB88A43);
        leather.render(matrices, solid, light, overlay, 0xFF632335);
        wrapping.render(matrices, solid, light, overlay, 0xFF832E43);
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        renderSphere(matrices, solid, 1.3f, 255, coreColor, overlay);
        matrices.popPose();
        // Keep an opaque luminous body under the translucent aura for shaderpack depth stability.
        VertexConsumer emissive = coreBuffer.get();
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        renderSphere(matrices, emissive, 1.42f, 20 + (int)(charge * 30), coreColor, overlay);
        matrices.popPose();
        matrices.pushPose();
        orbit.translateAndRotate(matrices);
        matrices.translate(2.05f / 16, 0, 0);
        renderSphere(matrices, emissive, .16f, 255, 0xFFFFDFFF, overlay);
        matrices.popPose();
    }

    private static org.joml.Vector3f[][] createCrescent() {
        var sections = new org.joml.Vector3f[49][8];
        for (int i = 0; i < sections.length; i++) {
            double t = i / 48.0, angle = Math.toRadians(50 + 260 * t);
            float halfWidth = .07f + .95f * (float)Math.pow(Math.sin(t * Math.PI), .8);
            float depth = .09f + .71f * (float)Math.pow(Math.sin(t * Math.PI), .45);
            float bevel = Math.min(.18f, halfWidth * .4f);
            float[][] section = {{-halfWidth+bevel,-depth},{halfWidth-bevel,-depth},
                    {halfWidth,-depth+bevel},{halfWidth,depth-bevel},{halfWidth-bevel,depth},
                    {-halfWidth+bevel,depth},{-halfWidth,depth-bevel},{-halfWidth,-depth+bevel}};
            double normal = Math.atan2(5.2 * Math.sin(angle), 6.3 * Math.cos(angle));
            for (int j = 0; j < 8; j++) sections[i][j] = new org.joml.Vector3f(
                    -.55f + (float)Math.cos(angle) * 5.2f + section[j][0] * (float)Math.cos(normal),
                    21.2f + (float)Math.sin(angle) * 6.3f + section[j][0] * (float)Math.sin(normal), section[j][1]);
        }
        return sections;
    }

    private static void renderCrescent(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        // Adjacent surfaces use the same cross-section vertices; there are no internal caps or overlaps.
        for (int i = 0; i < CRESCENT.length - 1; i++) for (int side = 0; side < 8; side++) {
            int next = (side + 1) % 8;
            int color = side >= 5 ? 0xFFB88A43 : side == 0 || side == 4 ? 0xFF302C3A : 0xFF24212E;
            quad(matrices, vertices, CRESCENT[i][side], CRESCENT[i+1][side],
                    CRESCENT[i+1][next], CRESCENT[i][next], light, overlay, color);
        }
        for (int j = 1; j < 7; j++) {
            var start = CRESCENT[0];
            var end = CRESCENT[CRESCENT.length-1];
            quad(matrices, vertices, start[0], start[j], start[j+1], start[j+1], light, overlay, 0xFF24212E);
            quad(matrices, vertices, end[0], end[j+1], end[j], end[j], light, overlay, 0xFF24212E);
        }
    }

    private static org.joml.Vector3f neckPoint(int ring, int side) {
        float t = ring / 8f;
        double angle = side * Math.PI / 8;
        float x = (float)Math.cos(angle), z = (float)Math.sin(angle);
        float square = .72f / Math.max(Math.abs(x), Math.abs(z));
        return new org.joml.Vector3f(-.4f*t*t + x*(square*(1-t)+1.15f*t),
                10.5f + 4.8f*t, z*(square*(1-t)+.74f*t));
    }

    private static void renderNeck(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        for (int ring = 0; ring < 8; ring++) for (int side = 0; side < 16; side++) {
            quad(matrices, vertices, neckPoint(ring,side), neckPoint(ring+1,side),
                    neckPoint(ring+1,side+1), neckPoint(ring,side+1), light, overlay,
                    ring == 5 ? 0xFFB88A43 : 0xFF24212E);
        }
    }

    private void renderSphere(PoseStack matrices, VertexConsumer vertices, float radius, int alpha, int color, int overlay) {
        for (int latitude = 0; latitude < 8; latitude++) for (int longitude = 0; longitude < 16; longitude++) {
            int[][] corners = {{latitude,longitude},{latitude+1,longitude},
                    {latitude+1,longitude+1},{latitude,longitude+1}};
            for (int[] corner : corners) {
                double theta = -Math.PI/2 + corner[0]*Math.PI/8, phi = corner[1]*Math.PI/8;
                var normal = new org.joml.Vector3f((float)(Math.cos(theta)*Math.cos(phi)),
                        (float)Math.sin(theta), (float)(Math.cos(theta)*Math.sin(phi)));
                float ribbon = (float)Math.pow(Math.max(0, Math.sin(theta*6+phi*2-animationTime*.09)), 10);
                float brightness = .72f + normal.y*.12f + ribbon*(.16f+charge*.1f);
                int red = Math.min(255, Math.round(((color >> 16) & 255)*brightness + ribbon*35));
                int green = Math.min(255, Math.round(((color >> 8) & 255)*brightness + ribbon*35));
                int blue = Math.min(255, Math.round((color & 255)*brightness + ribbon*35));
                vertex(matrices, vertices, new org.joml.Vector3f(normal).mul(radius), normal,
                        LightTexture.FULL_BRIGHT, overlay, (alpha<<24)|(red<<16)|(green<<8)|blue);
            }
        }
    }

    private static void quad(PoseStack matrices, VertexConsumer vertices, org.joml.Vector3f a,
                             org.joml.Vector3f b, org.joml.Vector3f c, org.joml.Vector3f d,
                             int light, int overlay, int color) {
        var normal = new org.joml.Vector3f(b).sub(a).cross(new org.joml.Vector3f(c).sub(a)).normalize();
        for (var point : new org.joml.Vector3f[]{a,b,c,d}) vertex(matrices, vertices, point, normal, light, overlay, color);
    }

    private static void vertex(PoseStack matrices, VertexConsumer vertices, org.joml.Vector3f point,
                               org.joml.Vector3f normal, int light, int overlay, int color) {
        vertices.addVertex(matrices.last().pose(), point.x/16, point.y/16, point.z/16)
                .setColor((color>>16)&255, (color>>8)&255, color&255, color>>>24).setUv(.5f, .5f)
                .setUv1(overlay&65535, overlay>>16).setUv2(light&65535, light>>16)
                .setNormal(matrices.last(), normal.x, normal.y, normal.z);
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
