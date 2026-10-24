package net.godwand.client.model;

import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Supplier;

public class GodWandModel extends Model {
    private final ModelPart root, baseAndCage, silver, singularityCore, floatingSpire, spireTrim, chargeArcs;
    private int activeSkill;
    private float charge;

    public GodWandModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        baseAndCage = root.getChild("base_and_cage");
        silver = root.getChild("silver");
        singularityCore = root.getChild("singularity_core");
        floatingSpire = root.getChild("floating_spire");
        spireTrim = root.getChild("spire_trim");
        chargeArcs = root.getChild("charge_arcs");
        chargeArcs.visible = false;
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        var dark = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1, -26, -1, 2, 34, 2)
                .texOffs(0, 0).addBox(-0.5f, -28, -0.5f, 1, 2, 1)
                .texOffs(0, 0).addBox(-2, 7, -2, 4, 3, 4)
                .texOffs(0, 0).addBox(-3, 9, -3, 6, 2, 6)
                .texOffs(0, 0).addBox(-3.4f, 10.5f, -3.4f, 6.8f, 1.1f, 6.8f)
                .texOffs(0, 0).addBox(-1.6f, 21, -1.6f, 3.2f, 2.4f, 3.2f);
        // Slightly raised trim prevents coplanar caps from flickering against the body.
        var trim = CubeListBuilder.create();
        for (float y : new float[]{-24, -16, -6, 4}) {
            trim.texOffs(0,0).addBox(-1.45f, y, -1.45f, 2.9f, 0.8f, 2.9f, new CubeDeformation(0.015f));
        }
        // Silver rails and stepped collars give the shaft relief on all four sides.
        for (int x : new int[]{-1, 1}) {
            trim.texOffs(0,0).addBox(x * 0.85f - 0.12f, -25, -1.12f, 0.24f, 32, 0.24f, new CubeDeformation(0.015f));
            trim.texOffs(0,0).addBox(-1.12f, -25, x * 0.85f - 0.12f, 0.24f, 32, 0.24f, new CubeDeformation(0.015f));
        }
        for (float y : new float[]{-18, -8, 5}) {
            dark.texOffs(0,0).addBox(-1.6f, y, -1.6f, 3.2f, 3, 3.2f);
            trim.texOffs(0,0).addBox(-1.8f, y, -1.8f, 3.6f, 0.45f, 3.6f, new CubeDeformation(0.015f))
                    .texOffs(0,0).addBox(-1.8f, y + 2.6f, -1.8f, 3.6f, 0.45f, 3.6f, new CubeDeformation(0.015f));
            for (int side : new int[]{-1, 1}) {
                trim.texOffs(0,0).addBox(-0.7f, y + 0.7f, side * 1.65f - 0.1f, 1.4f, 0.3f, 0.2f, new CubeDeformation(0.015f))
                        .texOffs(0,0).addBox(-0.15f, y + 0.7f, side * 1.65f - 0.1f, 0.3f, 1.5f, 0.2f, new CubeDeformation(0.015f))
                        .texOffs(0,0).addBox(side * 1.65f - 0.1f, y + 0.7f, -0.7f, 0.2f, 0.3f, 1.4f, new CubeDeformation(0.015f))
                        .texOffs(0,0).addBox(side * 1.65f - 0.1f, y + 0.7f, -0.15f, 0.2f, 1.5f, 0.3f, new CubeDeformation(0.015f));
            }
        }
        for (int step = 0; step < 3; step++) {
            float width = 4.2f + step * 0.9f;
            trim.texOffs(0,0).addBox(-width / 2, 7.7f + step, -width / 2, width, 0.3f, width, new CubeDeformation(0.015f));
        }
        trim.texOffs(0,0).addBox(-1.8f, 21, -1.8f, 3.6f, 0.3f, 3.6f, new CubeDeformation(0.015f))
                .texOffs(0,0).addBox(-1.8f, 23, -1.8f, 3.6f, 0.3f, 3.6f, new CubeDeformation(0.015f));
        for (float y : new float[]{10.5f, 11.35f}) {
            trim.texOffs(0,0).addBox(-3.6f, y, -3.6f, 7.2f, 0.25f, 7.2f, new CubeDeformation(0.015f));
        }
        // Shorter prongs leave the golden crystal exposed instead of enclosing a rectangular lantern.
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            dark.texOffs(0,0).addBox(x * 2.8f - 0.35f, 11.6f, z * 2.8f - 0.35f, 0.7f, 7.5f, 0.7f);
            trim.texOffs(0,0).addBox(x * 2.8f - 0.5f, 12, z * 2.8f - 0.5f, 1, 0.5f, 1, new CubeDeformation(0.015f))
                    .texOffs(0,0).addBox(x * 2.8f - 0.12f, 12, z * 3.15f - 0.1f, 0.24f, 6.5f, 0.2f, new CubeDeformation(0.015f))
                    .texOffs(0,0).addBox(x * 3.15f - 0.1f, 12, z * 2.8f - 0.12f, 0.2f, 6.5f, 0.24f, new CubeDeformation(0.015f));
        }
        PartDefinition base = root.addOrReplaceChild("base_and_cage", dark, PartPose.ZERO);
        PartDefinition silver = root.addOrReplaceChild("silver", trim, PartPose.ZERO);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            var arch = PartPose.offsetAndRotation(x * 2.8f, 17.5f, z * 2.8f, -z * 0.24f, 0, x * 0.24f);
            base.addOrReplaceChild("arch_" + x + "_" + z,
                    CubeListBuilder.create().texOffs(0,0).addBox(-0.3f, 0, -0.3f, 0.6f, 5, 0.6f), arch);
            silver.addOrReplaceChild("arch_trim_" + x + "_" + z,
                    CubeListBuilder.create().texOffs(0,0).addBox(-0.35f, 0, -0.4f, 0.7f, 5, 0.15f, new CubeDeformation(0.015f)), arch);
        }
        root.addOrReplaceChild("singularity_core", CubeListBuilder.create()
                .texOffs(0,0).addBox(-0.6f, -2.8f, -0.6f, 1.2f, 0.8f, 1.2f)
                .texOffs(0,0).addBox(-1.1f, -2, -1.1f, 2.2f, 1.1f, 2.2f)
                .texOffs(0,0).addBox(-1.5f, -0.9f, -1.5f, 3, 1.8f, 3)
                .texOffs(0,0).addBox(-1.1f, 0.9f, -1.1f, 2.2f, 1.1f, 2.2f)
                .texOffs(0,0).addBox(-0.6f, 2, -0.6f, 1.2f, 0.8f, 1.2f), PartPose.offset(0, 16.5f, 0));
        root.addOrReplaceChild("floating_spire", CubeListBuilder.create()
                .texOffs(0,0).addBox(-1.7f, 0, -1.7f, 3.4f, 1.2f, 3.4f)
                .texOffs(0,0).addBox(-1.2f, 1.2f, -1.2f, 2.4f, 3.3f, 2.4f)
                .texOffs(0,0).addBox(-0.75f, 4.5f, -0.75f, 1.5f, 2.5f, 1.5f)
                .texOffs(0,0).addBox(-0.25f, 7, -0.25f, 0.5f, 3, 0.5f), PartPose.offset(0, 25.5f, 0));
        root.addOrReplaceChild("spire_trim", CubeListBuilder.create()
                .texOffs(0,0).addBox(-1.8f, 0, -1.8f, 3.6f, 0.3f, 3.6f, new CubeDeformation(0.015f))
                .texOffs(0,0).addBox(-1.3f, 4.2f, -1.3f, 2.6f, 0.3f, 2.6f, new CubeDeformation(0.015f))
                .texOffs(0,0).addBox(-0.12f, 1.2f, -1.3f, 0.24f, 3, 0.2f, new CubeDeformation(0.015f))
                .texOffs(0,0).addBox(-0.12f, 7, -0.3f, 0.24f, 3, 0.15f, new CubeDeformation(0.015f)), PartPose.offset(0, 25.5f, 0));
        var arcs = CubeListBuilder.create();
        for (int sign : new int[]{-1, 1}) {
            arcs.texOffs(0,0).addBox(sign * 2.1f - 0.07f, -1.8f, -0.07f, 0.14f, 3.6f, 0.14f);
            arcs.texOffs(0,0).addBox(-1.8f, sign * 2.1f - 0.07f, -0.07f, 3.6f, 0.14f, 0.14f);
        }
        root.addOrReplaceChild("charge_arcs", arcs, PartPose.offset(0, 16.5f, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int activeSkillId) {
        activeSkill = activeSkillId;
        charge = Math.clamp(chargeRatio, 0, 1);
        float hover = (float) Math.sin(time * 0.08f) * 0.3f;
        floatingSpire.y = 25.5f + hover + charge * charge * (activeSkillId == 3 ? 4.0f : 2.6f);
        floatingSpire.yRot = time * (0.025f + chargeRatio * 0.12f);
        spireTrim.y = floatingSpire.y;
        spireTrim.yRot = floatingSpire.yRot;
        singularityCore.y = 16.5f - hover * 0.4f;
        singularityCore.yRot = -time * (0.04f + chargeRatio * 0.15f);
        singularityCore.xRot = (float) Math.sin(time * 0.06f) * 0.12f + charge * (activeSkillId == 2 ? 0.7f : 0.2f);
        float size = activeSkillId == 3 ? 1 - 0.45f * charge * charge : 1 + charge * 0.18f;
        singularityCore.xScale = singularityCore.yScale = singularityCore.zScale = size;
        chargeArcs.visible = charge > 0.05f;
        chargeArcs.y = singularityCore.y;
        chargeArcs.yRot = time * (0.03f + charge * 0.14f);
        chargeArcs.zRot = time * 0.035f;
        chargeArcs.xScale = chargeArcs.yScale = chargeArcs.zScale = 0.8f + charge * 0.3f;
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();
        baseAndCage.render(matrices, solid, light, overlay, 0xFF283039);
        floatingSpire.render(matrices, solid, light, overlay, 0xFF283039);
        silver.render(matrices, solid, light, overlay, 0xFFCAD2D8);
        spireTrim.render(matrices, solid, light, overlay, 0xFFCAD2D8);
        // Immediate providers can flush the previous layer when the next buffer is requested.
        VertexConsumer emissive = coreBuffer.get();
        singularityCore.render(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, coreColor);
        chargeArcs.render(matrices, emissive, LightTexture.FULL_BRIGHT, overlay,
                ((int)(charge * 220) << 24) | (activeSkill == 3 ? 0xD3BEFF : 0xE5F5FF));
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
