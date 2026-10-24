package net.godwand.client.model;

import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import java.util.function.Supplier;

public class GodWandModel extends Model {
    private final ModelPart root, baseAndCage, silver, singularityCore, floatingSpire, spireTrim, chargeArcs;
    private int activeSkill;
    private float charge;

    public GodWandModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        baseAndCage = root.getChild("base_and_cage");
        silver = root.getChild("silver");
        singularityCore = root.getChild("singularity_core");
        floatingSpire = root.getChild("floating_spire");
        spireTrim = root.getChild("spire_trim");
        chargeArcs = root.getChild("charge_arcs");
        chargeArcs.visible = false;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData mesh = new ModelData();
        ModelPartData root = mesh.getRoot();
        var dark = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1, -26, -1, 2, 34, 2)
                .uv(0, 0).cuboid(-0.5f, -28, -0.5f, 1, 2, 1)
                .uv(0, 0).cuboid(-2, 7, -2, 4, 3, 4)
                .uv(0, 0).cuboid(-3, 9, -3, 6, 2, 6)
                .uv(0, 0).cuboid(-3.4f, 10.5f, -3.4f, 6.8f, 1.1f, 6.8f)
                .uv(0, 0).cuboid(-1.6f, 21, -1.6f, 3.2f, 2.4f, 3.2f);
        // Slightly raised trim prevents coplanar caps from flickering against the body.
        var trim = ModelPartBuilder.create();
        for (float y : new float[]{-24, -16, -6, 4}) {
            trim.uv(0,0).cuboid(-1.45f, y, -1.45f, 2.9f, 0.8f, 2.9f, new Dilation(0.015f));
        }
        // Silver rails and stepped collars give the shaft relief on all four sides.
        for (int x : new int[]{-1, 1}) {
            trim.uv(0,0).cuboid(x * 0.85f - 0.12f, -25, -1.12f, 0.24f, 32, 0.24f, new Dilation(0.015f));
            trim.uv(0,0).cuboid(-1.12f, -25, x * 0.85f - 0.12f, 0.24f, 32, 0.24f, new Dilation(0.015f));
        }
        for (float y : new float[]{-18, -8, 5}) {
            dark.uv(0,0).cuboid(-1.6f, y, -1.6f, 3.2f, 3, 3.2f);
            trim.uv(0,0).cuboid(-1.8f, y, -1.8f, 3.6f, 0.45f, 3.6f, new Dilation(0.015f))
                    .uv(0,0).cuboid(-1.8f, y + 2.6f, -1.8f, 3.6f, 0.45f, 3.6f, new Dilation(0.015f));
            for (int side : new int[]{-1, 1}) {
                trim.uv(0,0).cuboid(-0.7f, y + 0.7f, side * 1.65f - 0.1f, 1.4f, 0.3f, 0.2f, new Dilation(0.015f))
                        .uv(0,0).cuboid(-0.15f, y + 0.7f, side * 1.65f - 0.1f, 0.3f, 1.5f, 0.2f, new Dilation(0.015f))
                        .uv(0,0).cuboid(side * 1.65f - 0.1f, y + 0.7f, -0.7f, 0.2f, 0.3f, 1.4f, new Dilation(0.015f))
                        .uv(0,0).cuboid(side * 1.65f - 0.1f, y + 0.7f, -0.15f, 0.2f, 1.5f, 0.3f, new Dilation(0.015f));
            }
        }
        for (int step = 0; step < 3; step++) {
            float width = 4.2f + step * 0.9f;
            trim.uv(0,0).cuboid(-width / 2, 7.7f + step, -width / 2, width, 0.3f, width, new Dilation(0.015f));
        }
        trim.uv(0,0).cuboid(-1.8f, 21, -1.8f, 3.6f, 0.3f, 3.6f, new Dilation(0.015f))
                .uv(0,0).cuboid(-1.8f, 23, -1.8f, 3.6f, 0.3f, 3.6f, new Dilation(0.015f));
        for (float y : new float[]{10.5f, 11.35f}) {
            trim.uv(0,0).cuboid(-3.6f, y, -3.6f, 7.2f, 0.25f, 7.2f, new Dilation(0.015f));
        }
        // Shorter prongs leave the golden crystal exposed instead of enclosing a rectangular lantern.
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            dark.uv(0,0).cuboid(x * 2.8f - 0.35f, 11.6f, z * 2.8f - 0.35f, 0.7f, 7.5f, 0.7f);
            trim.uv(0,0).cuboid(x * 2.8f - 0.5f, 12, z * 2.8f - 0.5f, 1, 0.5f, 1, new Dilation(0.015f))
                    .uv(0,0).cuboid(x * 2.8f - 0.12f, 12, z * 3.15f - 0.1f, 0.24f, 6.5f, 0.2f, new Dilation(0.015f))
                    .uv(0,0).cuboid(x * 3.15f - 0.1f, 12, z * 2.8f - 0.12f, 0.2f, 6.5f, 0.24f, new Dilation(0.015f));
        }
        ModelPartData base = root.addChild("base_and_cage", dark, ModelTransform.NONE);
        ModelPartData silver = root.addChild("silver", trim, ModelTransform.NONE);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            var arch = ModelTransform.of(x * 2.8f, 17.5f, z * 2.8f, -z * 0.24f, 0, x * 0.24f);
            base.addChild("arch_" + x + "_" + z,
                    ModelPartBuilder.create().uv(0,0).cuboid(-0.3f, 0, -0.3f, 0.6f, 5, 0.6f), arch);
            silver.addChild("arch_trim_" + x + "_" + z,
                    ModelPartBuilder.create().uv(0,0).cuboid(-0.35f, 0, -0.4f, 0.7f, 5, 0.15f, new Dilation(0.015f)), arch);
        }
        root.addChild("singularity_core", ModelPartBuilder.create()
                .uv(0,0).cuboid(-0.6f, -2.8f, -0.6f, 1.2f, 0.8f, 1.2f)
                .uv(0,0).cuboid(-1.1f, -2, -1.1f, 2.2f, 1.1f, 2.2f)
                .uv(0,0).cuboid(-1.5f, -0.9f, -1.5f, 3, 1.8f, 3)
                .uv(0,0).cuboid(-1.1f, 0.9f, -1.1f, 2.2f, 1.1f, 2.2f)
                .uv(0,0).cuboid(-0.6f, 2, -0.6f, 1.2f, 0.8f, 1.2f), ModelTransform.pivot(0, 16.5f, 0));
        root.addChild("floating_spire", ModelPartBuilder.create()
                .uv(0,0).cuboid(-1.7f, 0, -1.7f, 3.4f, 1.2f, 3.4f)
                .uv(0,0).cuboid(-1.2f, 1.2f, -1.2f, 2.4f, 3.3f, 2.4f)
                .uv(0,0).cuboid(-0.75f, 4.5f, -0.75f, 1.5f, 2.5f, 1.5f)
                .uv(0,0).cuboid(-0.25f, 7, -0.25f, 0.5f, 3, 0.5f), ModelTransform.pivot(0, 25.5f, 0));
        root.addChild("spire_trim", ModelPartBuilder.create()
                .uv(0,0).cuboid(-1.8f, 0, -1.8f, 3.6f, 0.3f, 3.6f, new Dilation(0.015f))
                .uv(0,0).cuboid(-1.3f, 4.2f, -1.3f, 2.6f, 0.3f, 2.6f, new Dilation(0.015f))
                .uv(0,0).cuboid(-0.12f, 1.2f, -1.3f, 0.24f, 3, 0.2f, new Dilation(0.015f))
                .uv(0,0).cuboid(-0.12f, 7, -0.3f, 0.24f, 3, 0.15f, new Dilation(0.015f)), ModelTransform.pivot(0, 25.5f, 0));
        var arcs = ModelPartBuilder.create();
        for (int sign : new int[]{-1, 1}) {
            arcs.uv(0,0).cuboid(sign * 2.1f - 0.07f, -1.8f, -0.07f, 0.14f, 3.6f, 0.14f);
            arcs.uv(0,0).cuboid(-1.8f, sign * 2.1f - 0.07f, -0.07f, 3.6f, 0.14f, 0.14f);
        }
        root.addChild("charge_arcs", arcs, ModelTransform.pivot(0, 16.5f, 0));
        return TexturedModelData.of(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int activeSkillId) {
        activeSkill = activeSkillId;
        charge = Math.clamp(chargeRatio, 0, 1);
        float hover = (float) Math.sin(time * 0.08f) * 0.3f;
        floatingSpire.pivotY = 25.5f + hover + charge * charge * (activeSkillId == 3 ? 4.0f : 2.6f);
        floatingSpire.yaw = time * (0.025f + chargeRatio * 0.12f);
        spireTrim.pivotY = floatingSpire.pivotY;
        spireTrim.yaw = floatingSpire.yaw;
        singularityCore.pivotY = 16.5f - hover * 0.4f;
        singularityCore.yaw = -time * (0.04f + chargeRatio * 0.15f);
        singularityCore.pitch = (float) Math.sin(time * 0.06f) * 0.12f + charge * (activeSkillId == 2 ? 0.7f : 0.2f);
        float size = activeSkillId == 3 ? 1 - 0.45f * charge * charge : 1 + charge * 0.18f;
        singularityCore.xScale = singularityCore.yScale = singularityCore.zScale = size;
        chargeArcs.visible = charge > 0.05f;
        chargeArcs.pivotY = singularityCore.pivotY;
        chargeArcs.yaw = time * (0.03f + charge * 0.14f);
        chargeArcs.roll = time * 0.035f;
        chargeArcs.xScale = chargeArcs.yScale = chargeArcs.zScale = 0.8f + charge * 0.3f;
    }

    public void renderWand(MatrixStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();
        baseAndCage.render(matrices, solid, light, overlay, 0xFF283039);
        floatingSpire.render(matrices, solid, light, overlay, 0xFF283039);
        silver.render(matrices, solid, light, overlay, 0xFFCAD2D8);
        spireTrim.render(matrices, solid, light, overlay, 0xFFCAD2D8);
        // Immediate providers can flush the previous layer when the next buffer is requested.
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
