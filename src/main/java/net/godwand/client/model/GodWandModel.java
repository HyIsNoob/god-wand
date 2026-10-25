package net.godwand.client.model;

import net.minecraft.client.model.*;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import java.util.function.Supplier;

public class GodWandModel extends Model {
    private final ModelPart root, obsidian, gold, leather, wrapping, singularityCore, chargeArcs;
    private float charge;
    private int activeSkill;

    public GodWandModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        obsidian = root.getChild("obsidian");
        gold = root.getChild("gold");
        leather = root.getChild("leather");
        wrapping = root.getChild("wrapping");
        singularityCore = root.getChild("singularity_core");
        chargeArcs = root.getChild("charge_arcs");
        chargeArcs.visible = false;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData mesh = new ModelData();
        ModelPartData root = mesh.getRoot();
        var body = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-.48f, -46, -.48f, .96f, 62.7f, .96f);
        // The crescent lies in the vertical XY plane; its open side faces +X.
        float[][] steps = {
                {-1.7f, 16.5f, 3.3f, 1.2f}, {-3.2f, 17.7f, 1.7f, 1.2f},
                {-4.1f, 18.9f, 1.3f, 4.2f}, {-3.2f, 23.1f, 1.7f, 1.2f},
                {-1.7f, 24.3f, 2.1f, 1}, {.4f, 25.3f, 1.4f, .7f},
                {.4f, 17.7f, 1.2f, .7f}
        };
        for (float[] step : steps) {
            body.uv(0, 0).cuboid(step[0], step[1], -.55f, step[2], step[3], 1.1f);
        }
        root.addChild("obsidian", body, ModelTransform.NONE);
        var trim = ModelPartBuilder.create();
        for (float y : new float[]{-45.8f, -6, 5.5f, 15.4f}) {
            trim.uv(0, 0).cuboid(-.67f, y, -.67f, 1.34f, .5f, 1.34f);
        }
        // Narrow inner-edge inlays overlap the obsidian slightly to prevent coplanar flicker.
        trim.uv(0, 0).cuboid(-2.82f, 19.05f, -.57f, .2f, 3.9f, 1.14f)
                .uv(0, 0).cuboid(-1.72f, 23.15f, -.57f, .2f, 1, 1.14f)
                .uv(0, 0).cuboid(-1.48f, 24.12f, -.57f, 1.6f, .2f, 1.14f)
                .uv(0, 0).cuboid(.38f, 25.12f, -.57f, 1.1f, .2f, 1.14f)
                .uv(0, 0).cuboid(-1.48f, 17.52f, -.57f, 1.7f, .2f, 1.14f)
                .uv(0, 0).cuboid(-1.72f, 17.9f, -.57f, .2f, .8f, 1.14f);
        root.addChild("gold", trim, ModelTransform.NONE);
        // Both hand transforms hold the origin, in the middle of this leather sleeve.
        root.addChild("leather", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-.57f, -5.5f, -.57f, 1.14f, 11, 1.14f), ModelTransform.NONE);
        var wraps = ModelPartBuilder.create();
        for (int band = 0; band < 10; band++) {
            wraps.uv(0, 0).cuboid(-.6f, -5.2f + band * 1.1f, -.6f, 1.2f, .15f, 1.2f);
        }
        root.addChild("wrapping", wraps, ModelTransform.NONE);
        ModelPartData core = root.addChild("singularity_core", ModelPartBuilder.create(), ModelTransform.pivot(-.55f, 21.2f, 0));
        core.addChild("crystal", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-.82f, -.82f, -.82f, 1.64f, 1.64f, 1.64f),
                ModelTransform.of(0, 0, 0, 0, (float)Math.PI / 4, (float)Math.PI / 4));
        var arcs = ModelPartBuilder.create();
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3;
            arcs.uv(0, 0).cuboid((float)Math.cos(angle) * 1.65f - .09f, -.09f,
                    (float)Math.sin(angle) * 1.65f - .09f, .18f, .18f, .18f);
        }
        root.addChild("charge_arcs", arcs, ModelTransform.pivot(-.55f, 21.2f, 0));
        return TexturedModelData.of(mesh, 64, 64);
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
        singularityCore.yaw = time * speed;
        singularityCore.pitch = (float)Math.sin(time * .04f) * .12f + charge * (activeSkillId == 2 ? .65f : .2f);
        singularityCore.roll = (float)Math.sin(time * .03f) * .08f;
        float size = activeSkillId == 3 ? 1 - .45f * charge * charge : 1 + charge * .12f;
        singularityCore.xScale = singularityCore.yScale = singularityCore.zScale = size;
        chargeArcs.visible = charge > .05f;
        chargeArcs.yaw = -time * speed;
        chargeArcs.pitch = .4f + charge * .4f;
        chargeArcs.xScale = chargeArcs.yScale = chargeArcs.zScale = .8f + charge * .2f;
    }

    public void renderWand(MatrixStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();
        obsidian.render(matrices, solid, light, overlay, 0xFF24212E);
        gold.render(matrices, solid, light, overlay, 0xFFB88A43);
        leather.render(matrices, solid, light, overlay, 0xFF632335);
        wrapping.render(matrices, solid, light, overlay, 0xFF832E43);
        // Request the emissive layer only after finishing every solid part.
        VertexConsumer emissive = coreBuffer.get();
        singularityCore.render(matrices, emissive, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, coreColor);
        chargeArcs.render(matrices, emissive, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay,
                ((int)(charge * 220) << 24) | (activeSkill == 3 ? 0xD3BEFF : 0xE5F5FF));
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
