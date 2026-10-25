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
    private final ModelPart root, obsidian, facets, gold, leather, wrapping, singularityCore, chargeArcs;
    private float charge;
    private int activeSkill;

    public GodWandModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        obsidian = root.getChild("obsidian");
        facets = root.getChild("facets");
        gold = root.getChild("gold");
        leather = root.getChild("leather");
        wrapping = root.getChild("wrapping");
        singularityCore = root.getChild("singularity_core");
        chargeArcs = root.getChild("charge_arcs");
        chargeArcs.visible = false;
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        var body = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-.82f, -36, -.82f, 1.64f, 42, 1.64f)
                .texOffs(0, 0).addBox(-.72f, 6, -.72f, 1.44f, 9, 1.44f);
        PartDefinition obsidian = root.addOrReplaceChild("obsidian", body, PartPose.ZERO);
        PartDefinition facets = root.addOrReplaceChild("facets", CubeListBuilder.create(), PartPose.ZERO);
        var trim = CubeListBuilder.create();
        for (float y : new float[]{-35.8f, -25, -15, -6, 5.5f, 9, 14.2f}) {
            float width = y == -6 || y == 5.5f ? 2.3f : y == 14.2f ? 2.5f : 1.9f;
            trim.texOffs(0, 0).addBox(-width / 2, y, -width / 2, width, .55f, width);
        }
        // Angled, tapered sections give the crescent a curved blade silhouette instead of a square C.
        PartDefinition inlays = root.addOrReplaceChild("gold", trim, PartPose.ZERO);
        for (int segment = 0; segment < 12; segment++) {
            double t = (segment + .5) / 12.0;
            double angle = Math.toRadians(50 + 260 * t);
            float x = -.55f + (float)Math.cos(angle) * 5.2f;
            float y = 21.2f + (float)Math.sin(angle) * 6.3f;
            float thickness = .35f + 1.75f * (float)Math.pow(Math.sin(t * Math.PI), .85);
            float length = (float)(Math.hypot(5.2 * Math.sin(angle), 6.3 * Math.cos(angle)) * Math.toRadians(260.0 / 12)) + .18f;
            float rotation = (float)Math.atan2(5.2 * Math.sin(angle), 6.3 * Math.cos(angle));
            var pose = PartPose.offsetAndRotation(x, y, 0, 0, 0, rotation);
            obsidian.addOrReplaceChild("blade_" + segment, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-thickness / 2, -length / 2, -.72f, thickness, length, 1.44f), pose);
            facets.addOrReplaceChild("bevel_" + segment, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-thickness * .34f, -length * .38f, -.85f, thickness * .68f, length * .76f, 1.7f), pose);
            inlays.addOrReplaceChild("edge_" + segment, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-thickness / 2 - .06f, -length * .43f, -.76f, .16f, length * .86f, 1.52f), pose);
        }
        // A solid socket ties the lower blade into the shaft.
        obsidian.addOrReplaceChild("socket", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.1f, 14.4f, -.9f, 3.4f, 1.4f, 1.8f), PartPose.ZERO);
        // Both hands hold the origin, in the middle of the thicker red leather sleeve.
        root.addOrReplaceChild("leather", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.025f, -5.5f, -1.025f, 2.05f, 11, 2.05f), PartPose.ZERO);
        var wraps = CubeListBuilder.create();
        for (int band = 0; band < 10; band++) {
            wraps.texOffs(0, 0).addBox(-1.075f, -5.2f + band * 1.1f, -1.075f, 2.15f, .22f, 2.15f);
        }
        root.addOrReplaceChild("wrapping", wraps, PartPose.ZERO);
        root.addOrReplaceChild("singularity_core", CubeListBuilder.create(), PartPose.offset(-.55f, 21.2f, 0));
        var arcs = CubeListBuilder.create();
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3;
            arcs.texOffs(0, 0).addBox((float)Math.cos(angle) * 1.65f - .09f, -.09f,
                    (float)Math.sin(angle) * 1.65f - .09f, .18f, .18f, .18f);
        }
        root.addOrReplaceChild("charge_arcs", arcs, PartPose.offset(-.55f, 21.2f, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int activeSkillId) {
        activeSkill = activeSkillId;
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
        chargeArcs.visible = charge > .05f;
        chargeArcs.yRot = -time * speed;
        chargeArcs.xRot = .4f + charge * .4f;
        chargeArcs.xScale = chargeArcs.yScale = chargeArcs.zScale = .8f + charge * .2f;
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();
        obsidian.render(matrices, solid, light, overlay, 0xFF24212E);
        facets.render(matrices, solid, light, overlay, 0xFF383444);
        gold.render(matrices, solid, light, overlay, 0xFFB88A43);
        leather.render(matrices, solid, light, overlay, 0xFF632335);
        wrapping.render(matrices, solid, light, overlay, 0xFF832E43);
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        renderCrystal(matrices, solid, LightTexture.FULL_BRIGHT, overlay, coreColor);
        matrices.popPose();
        // The crystal stays opaque and shaded; only orbiting energy uses the emissive pass.
        VertexConsumer emissive = coreBuffer.get();
        chargeArcs.render(matrices, emissive, LightTexture.FULL_BRIGHT, overlay,
                ((int)(charge * 220) << 24) | (activeSkill == 3 ? 0xD3BEFF : 0xE5F5FF));
    }

    private static void renderCrystal(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        for (int half : new int[]{-1, 1}) for (int side = 0; side < 8; side++) {
            double a = side * Math.PI / 4, b = (side + 1) * Math.PI / 4;
            var tip = new org.joml.Vector3f(0, half * 1.85f, 0);
            var first = new org.joml.Vector3f((float)Math.cos(a) * 1.25f, 0, (float)Math.sin(a) * 1.25f);
            var second = new org.joml.Vector3f((float)Math.cos(b) * 1.25f, 0, (float)Math.sin(b) * 1.25f);
            if (half > 0) { var swap = first; first = second; second = swap; }
            var normal = new org.joml.Vector3f(first).sub(tip).cross(new org.joml.Vector3f(second).sub(tip)).normalize();
            float shade = .68f + .24f * (float)(.5 + .5 * Math.cos(a - .6));
            int red = Math.round(((color >> 16) & 255) * shade);
            int green = Math.round(((color >> 8) & 255) * shade);
            int blue = Math.round((color & 255) * shade);
            // A repeated final vertex lets the existing quad buffer draw each triangular facet.
            for (var point : new org.joml.Vector3f[]{tip, first, second, second}) {
                vertices.addVertex(matrices.last().pose(), point.x / 16, point.y / 16, point.z / 16)
                        .setColor(red, green, blue, 255).setUv(.5f, .5f)
                        .setUv1(overlay & 65535, overlay >> 16).setUv2(light & 65535, light >> 16)
                        .setNormal(matrices.last(), normal.x, normal.y, normal.z);
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
