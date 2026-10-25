package net.godwand.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import java.util.function.Supplier;

public class GodWandModel extends Model {
    private final ModelPart root, obsidian, gold, leather, wrapping, reactorCapsule, pommelGem;
    private final ModelPart singularityCore, orbit, satellite;
    private float charge, animationTime;

    private static final float CX = -0.55f;
    private static final float CY = 21.2f;
    private static final float C_RADIUS = 5.9f;
    private static final int NUM_SEGMENTS = 8;

    private static final Vector3f[][][] CRESCENT_SEGMENTS = createCrescentSegments();
    private static final Vector3f[][] GOLD_RAIL = createGoldRail();
    private static final Vector3f[][] RED_VENTS = createRedVents();
    private static final Vector3f[][] GOLD_BRACKETS = createGoldBrackets();
    private static final Vector3f[][] TOP_TALON = createTopTalon();
    private static final Vector3f[][] POMMEL_BLADE = createPommelBlade();

    public GodWandModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.obsidian = root.getChild("obsidian");
        this.gold = root.getChild("gold");
        this.leather = root.getChild("leather");
        this.wrapping = root.getChild("wrapping");
        this.reactorCapsule = root.getChild("reactor_capsule");
        this.pommelGem = root.getChild("pommel_gem");
        this.singularityCore = root.getChild("singularity_core");
        this.orbit = root.getChild("orbit");
        this.satellite = this.orbit.getChild("satellite");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 1. Dark obsidian / netherite main shaft
        var shaft = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.8f, -35.95f, -0.8f, 1.6f, 29.9f, 1.6f)
                .texOffs(0, 0).addBox(-0.75f, 6.05f, -0.75f, 1.5f, 8.9f, 1.5f);
        root.addOrReplaceChild("obsidian", shaft, PartPose.ZERO);

        // 2. Gold trims, collars, reactor cage, pommel quillons (carefully offset from obsidian planes to avoid z-fighting)
        var goldTrim = CubeListBuilder.create()
                // Staff trim rings
                .texOffs(64, 0).addBox(-1.0f, -35.7f, -1.0f, 2.0f, 0.5f, 2.0f)
                .texOffs(64, 0).addBox(-0.95f, -24.0f, -0.95f, 1.9f, 0.4f, 1.9f)
                .texOffs(64, 0).addBox(-0.95f, -15.0f, -0.95f, 1.9f, 0.4f, 1.9f)
                .texOffs(64, 0).addBox(-1.15f, -6.2f, -1.15f, 2.3f, 0.5f, 2.3f)
                .texOffs(64, 0).addBox(-1.15f, 5.5f, -1.15f, 2.3f, 0.5f, 2.3f)
                // Mid-staff reactor upper & lower cage rings
                .texOffs(64, 0).addBox(-1.25f, 6.6f, -1.25f, 2.5f, 0.55f, 2.5f)
                .texOffs(64, 0).addBox(-1.25f, 10.0f, -1.25f, 2.5f, 0.55f, 2.5f)
                // 4 Vertical cage struts
                .texOffs(64, 0).addBox(-0.95f, 7.15f, -0.95f, 0.28f, 2.85f, 0.28f)
                .texOffs(64, 0).addBox(0.67f, 7.15f, -0.95f, 0.28f, 2.85f, 0.28f)
                .texOffs(64, 0).addBox(-0.95f, 7.15f, 0.67f, 0.28f, 2.85f, 0.28f)
                .texOffs(64, 0).addBox(0.67f, 7.15f, 0.67f, 0.28f, 2.85f, 0.28f)
                // Neck stepped collars
                .texOffs(64, 0).addBox(-1.15f, 11.0f, -1.15f, 2.3f, 0.75f, 2.3f)
                .texOffs(64, 0).addBox(-1.35f, 12.6f, -1.35f, 2.7f, 1.35f, 2.7f)
                // Crescent base socket bracket
                .texOffs(64, 0).addBox(-1.6f, 14.0f, -0.9f, 2.2f, 1.6f, 1.8f)
                // Pommel transition collar & flared winged quillons
                .texOffs(64, 0).addBox(-1.15f, -36.85f, -1.15f, 2.3f, 0.8f, 2.3f)
                .texOffs(64, 0).addBox(-2.4f, -38.4f, -0.4f, 4.8f, 0.8f, 0.8f)
                .texOffs(64, 0).addBox(-2.8f, -38.2f, -0.25f, 0.5f, 1.2f, 0.5f)
                .texOffs(64, 0).addBox(2.3f, -38.2f, -0.25f, 0.5f, 1.2f, 0.5f);
        root.addOrReplaceChild("gold", goldTrim, PartPose.ZERO);

        // 3. Deep burgundy leather grip sleeve
        root.addOrReplaceChild("leather", CubeListBuilder.create().texOffs(0, 64)
                .addBox(-1.05f, -5.7f, -1.05f, 2.1f, 11.2f, 2.1f), PartPose.ZERO);

        // 4. Grip wrapping bands with gold rivet studs
        var wraps = CubeListBuilder.create();
        for (int band = 0; band < 11; band++) {
            float y = -5.2f + band * 1.02f;
            wraps.texOffs(0, 64).addBox(-1.08f, y, -1.08f, 2.16f, 0.15f, 2.16f);
            wraps.texOffs(64, 0).addBox(-0.15f, y, -1.12f, 0.3f, 0.15f, 0.04f);
            wraps.texOffs(64, 0).addBox(-0.15f, y, 1.08f, 0.3f, 0.15f, 0.04f);
        }
        root.addOrReplaceChild("wrapping", wraps, PartPose.ZERO);

        // 5. Glowing reactor ruby capsule
        root.addOrReplaceChild("reactor_capsule", CubeListBuilder.create().texOffs(64, 64)
                .addBox(-0.72f, 7.15f, -0.72f, 1.44f, 2.85f, 1.44f), PartPose.ZERO);

        // 6. Inset ruby gem inside pommel dagger hollow
        root.addOrReplaceChild("pommel_gem", CubeListBuilder.create().texOffs(64, 64)
                .addBox(-0.42f, -41.8f, -0.42f, 0.84f, 1.5f, 0.84f), PartPose.ZERO);

        // 7. Dynamic floating core & diamond gimbal bezel roots
        root.addOrReplaceChild("singularity_core", CubeListBuilder.create(), PartPose.offset(CX, CY, 0.0f));
        root.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offset(CX, CY, 0.0f))
                .addOrReplaceChild("satellite", CubeListBuilder.create(), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int activeSkillId) {
        animationTime = time;
        charge = Math.clamp(chargeRatio, 0.0f, 1.0f);

        float speed = switch (activeSkillId) {
            case 1 -> 0.055f + charge * 0.16f;
            case 2 -> 0.040f + charge * 0.12f;
            case 3 -> 0.030f + charge * 0.22f;
            case 4 -> 0.085f + charge * 0.30f;
            default -> 0.035f + charge * 0.08f;
        };

        // Gemstone and diamond bezel rotate and precess together as a unified core assembly
        singularityCore.yRot = time * speed;
        singularityCore.xRot = (float)Math.sin(time * 0.035f) * 0.06f;
        singularityCore.zRot = (float)Math.cos(time * 0.025f) * 0.05f;
        singularityCore.y = CY;

        float size = activeSkillId == 3 ? 1.0f - 0.45f * charge * charge : 1.0f + charge * 0.14f;
        singularityCore.xScale = singularityCore.yScale = singularityCore.zScale = size;

        // Orbiting crystalline ember flecks
        orbit.xRot = (float)Math.sin(time * 0.035f) * 0.15f;
        orbit.yRot = time * (speed * 0.9f);
        orbit.zRot = (float)Math.cos(time * 0.025f) * 0.15f;

        satellite.yRot = time * (0.05f + charge * 0.14f);
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();

        // 1. Solid baked shaft, gold trims, grip leather & wrapping
        obsidian.render(matrices, solid, light, overlay, 0xFF36313F);
        gold.render(matrices, solid, light, overlay, 0xFFB88A43);
        leather.render(matrices, solid, light, overlay, 0xFF632335);
        wrapping.render(matrices, solid, light, overlay, 0xFF832E43);

        // 2. Procedural crescent blade, inner gold rail, brackets, top talon beak (rotated 16 deg CCW to point upwards)
        matrices.pushPose();
        matrices.translate(CX / 16.0f, CY / 16.0f, 0.0f);
        matrices.mulPose(Axis.ZP.rotationDegrees(16.0f));
        matrices.translate(-CX / 16.0f, -CY / 16.0f, 0.0f);

        renderCrescent(matrices, solid, light, overlay);
        renderGoldRail(matrices, solid, light, overlay);
        renderGoldBrackets(matrices, solid, light, overlay);
        renderTopTalon(matrices, solid, light, overlay);
        matrices.popPose();

        renderPommelBlade(matrices, solid, light, overlay);

        // 3 & 4. Floating Core Assembly: Diamond Bezel Frame + Faceted Ruby Gemstone (synchronized)
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        float bob = (float)Math.sin(animationTime * 0.04f) * 0.015f;
        matrices.translate(0, bob, 0);

        renderDiamondBezel(matrices, solid, light, overlay);
        renderFacetedGem(matrices, solid, light, overlay, 1.25f, 1.90f, 1.25f, coreColor);
        matrices.popPose();

        // 5. Emissive luminous pass (reactor capsule, pommel gem, vents, inner crystal, aura)
        VertexConsumer emissive = coreBuffer.get();
        reactorCapsule.render(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 0xFFFF2548);
        pommelGem.render(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 0xFFFF2548);

        // Red vents (rotated with crescent head)
        matrices.pushPose();
        matrices.translate(CX / 16.0f, CY / 16.0f, 0.0f);
        matrices.mulPose(Axis.ZP.rotationDegrees(16.0f));
        matrices.translate(-CX / 16.0f, -CY / 16.0f, 0.0f);
        renderRedVents(matrices, emissive, overlay);
        matrices.popPose();

        // Glowing faceted ruby heart & translucent bloom auras
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        matrices.translate(0, bob, 0);
        renderFacetedGem(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 0.75f, 1.15f, 0.75f, 0xFFFFFFFF);

        int auraColor = 0xFF000000 | (((((coreColor >> 16) & 255) + 255) / 2) << 16)
                | (((((coreColor >> 8) & 255) + 255) / 2) << 8) | (((coreColor & 255) + 255) / 2);
        renderFacetedGem(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 1.40f, 2.10f, 1.40f,
                (auraColor & 0x00FFFFFF) | ((40 + (int)(charge * 36)) << 24));
        renderFacetedGem(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 1.65f, 2.45f, 1.65f,
                (auraColor & 0x00FFFFFF) | ((12 + (int)(charge * 14)) << 24));
        matrices.popPose();

        // Orbiting crystalline ember flecks
        matrices.pushPose();
        orbit.translateAndRotate(matrices);
        satellite.translateAndRotate(matrices);
        renderEmberMotes(matrices, emissive, overlay, 0xFFFF6E8A);
        matrices.popPose();
    }

    private static Vector3f[][][] createCrescentSegments() {
        var segs = new Vector3f[NUM_SEGMENTS][3][6];
        for (int s = 0; s < NUM_SEGMENTS; s++) {
            double t0 = s / (double)NUM_SEGMENTS, t1 = (s + 1) / (double)NUM_SEGMENTS;
            double aStart = Math.toRadians(45 + 260 * t0) + 0.025;
            double aEnd   = Math.toRadians(45 + 260 * t1) - 0.025;
            double tMid   = (t0 + t1) / 2.0;
            float envelope = (float)Math.pow(Math.sin(tMid * Math.PI), 0.5);
            float wRad = 1.15f + 1.25f * envelope;
            float tZ   = 0.65f + 0.50f * envelope;

            for (int sub = 0; sub < 3; sub++) {
                double a = aStart + (aEnd - aStart) * sub / 2.0;
                float cosA = (float)Math.cos(a), sinA = (float)Math.sin(a);
                float rIn = C_RADIUS - 0.1f, rOut = C_RADIUS + wRad;

                segs[s][sub][0] = new Vector3f(CX + cosA * rIn, CY + sinA * rIn, -tZ * 0.9f);
                segs[s][sub][1] = new Vector3f(CX + cosA * rOut, CY + sinA * rOut, -tZ * 0.65f);
                segs[s][sub][2] = new Vector3f(CX + cosA * (rOut + 0.15f), CY + sinA * (rOut + 0.15f), -tZ * 0.2f);
                segs[s][sub][3] = new Vector3f(CX + cosA * (rOut + 0.15f), CY + sinA * (rOut + 0.15f),  tZ * 0.2f);
                segs[s][sub][4] = new Vector3f(CX + cosA * rOut, CY + sinA * rOut,  tZ * 0.65f);
                segs[s][sub][5] = new Vector3f(CX + cosA * rIn, CY + sinA * rIn,  tZ * 0.9f);
            }
        }
        return segs;
    }

    private static void renderCrescent(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        for (int s = 0; s < NUM_SEGMENTS; s++) {
            for (int sub = 0; sub < 2; sub++) {
                Vector3f[] r0 = CRESCENT_SEGMENTS[s][sub];
                Vector3f[] r1 = CRESCENT_SEGMENTS[s][sub + 1];

                quad(matrices, vertices, r0[0], r1[0], r1[1], r0[1], light, overlay, 0xFF302A38);
                quad(matrices, vertices, r0[1], r1[1], r1[2], r0[2], light, overlay, 0xFF24212E);
                quad(matrices, vertices, r0[2], r1[2], r1[3], r0[3], light, overlay, 0xFF24212E);
                quad(matrices, vertices, r0[3], r1[3], r1[4], r0[4], light, overlay, 0xFF24212E);
                quad(matrices, vertices, r0[4], r1[4], r1[5], r0[5], light, overlay, 0xFF302A38);
            }
            Vector3f[] start = CRESCENT_SEGMENTS[s][0];
            Vector3f[] end = CRESCENT_SEGMENTS[s][2];
            quad(matrices, vertices, start[0], start[1], start[4], start[5], light, overlay, 0xFF24212E);
            quad(matrices, vertices, start[1], start[2], start[3], start[4], light, overlay, 0xFF24212E);
            quad(matrices, vertices, end[5], end[4], end[1], end[0], light, overlay, 0xFF24212E);
            quad(matrices, vertices, end[4], end[3], end[2], end[1], light, overlay, 0xFF24212E);
        }
    }

    private static Vector3f[][] createGoldRail() {
        int steps = 32;
        var rail = new Vector3f[steps][4];
        for (int i = 0; i < steps; i++) {
            double a = Math.toRadians(42 + 266 * (i / (double)(steps - 1)));
            float cosA = (float)Math.cos(a), sinA = (float)Math.sin(a);
            float rIn = C_RADIUS - 0.35f, rOut = C_RADIUS + 0.15f;
            rail[i][0] = new Vector3f(CX + cosA * rIn, CY + sinA * rIn, -0.45f);
            rail[i][1] = new Vector3f(CX + cosA * rOut, CY + sinA * rOut, -0.45f);
            rail[i][2] = new Vector3f(CX + cosA * rOut, CY + sinA * rOut,  0.45f);
            rail[i][3] = new Vector3f(CX + cosA * rIn, CY + sinA * rIn,  0.45f);
        }
        return rail;
    }

    private static void renderGoldRail(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        for (int i = 0; i < GOLD_RAIL.length - 1; i++) {
            Vector3f[] a = GOLD_RAIL[i], b = GOLD_RAIL[i + 1];
            quad(matrices, vertices, a[0], b[0], b[1], a[1], light, overlay, 0xFFB88A43);
            quad(matrices, vertices, b[3], a[3], a[2], b[2], light, overlay, 0xFFB88A43);
            quad(matrices, vertices, a[3], b[3], b[0], a[0], light, overlay, 0xFFD8AA53);
        }
    }

    private static Vector3f[][] createRedVents() {
        var vents = new Vector3f[NUM_SEGMENTS * 2][4];
        for (int s = 0; s < NUM_SEGMENTS; s++) {
            double tMid = (s + 0.5) / NUM_SEGMENTS;
            double a = Math.toRadians(45 + 260 * tMid);
            float envelope = (float)Math.pow(Math.sin(tMid * Math.PI), 0.5);
            float wRad = 1.15f + 1.25f * envelope;
            float rVent = C_RADIUS + wRad * 0.68f;
            float tZ = 0.65f + 0.50f * envelope;

            double vAng = 0.045;
            float vRad = 0.38f;

            float cos0 = (float)Math.cos(a - vAng), sin0 = (float)Math.sin(a - vAng);
            float cos1 = (float)Math.cos(a), sin1 = (float)Math.sin(a);
            float cos2 = (float)Math.cos(a + vAng), sin2 = (float)Math.sin(a + vAng);

            vents[s * 2][0] = new Vector3f(CX + cos0 * rVent, CY + sin0 * rVent, -tZ * 0.72f);
            vents[s * 2][1] = new Vector3f(CX + cos1 * (rVent + vRad), CY + sin1 * (rVent + vRad), -tZ * 0.72f);
            vents[s * 2][2] = new Vector3f(CX + cos2 * rVent, CY + sin2 * rVent, -tZ * 0.72f);
            vents[s * 2][3] = new Vector3f(CX + cos1 * (rVent - vRad), CY + sin1 * (rVent - vRad), -tZ * 0.72f);

            vents[s * 2 + 1][0] = new Vector3f(CX + cos2 * rVent, CY + sin2 * rVent, tZ * 0.72f);
            vents[s * 2 + 1][1] = new Vector3f(CX + cos1 * (rVent + vRad), CY + sin1 * (rVent + vRad), tZ * 0.72f);
            vents[s * 2 + 1][2] = new Vector3f(CX + cos0 * rVent, CY + sin0 * rVent, tZ * 0.72f);
            vents[s * 2 + 1][3] = new Vector3f(CX + cos1 * (rVent - vRad), CY + sin1 * (rVent - vRad), tZ * 0.72f);
        }
        return vents;
    }

    private static void renderRedVents(PoseStack matrices, VertexConsumer vertices, int overlay) {
        for (Vector3f[] v : RED_VENTS) {
            quad(matrices, vertices, v[0], v[1], v[2], v[3], LightTexture.FULL_BRIGHT, overlay, 0xFFFF2548);
        }
    }

    private static Vector3f[][] createGoldBrackets() {
        var brackets = new Vector3f[(NUM_SEGMENTS - 1) * 3][4];
        for (int s = 1; s < NUM_SEGMENTS; s++) {
            double t = s / (double)NUM_SEGMENTS;
            double a = Math.toRadians(45 + 260 * t);
            float envelope = (float)Math.pow(Math.sin(t * Math.PI), 0.5);
            float wRad = 1.15f + 1.25f * envelope;
            float tZ = 0.65f + 0.50f * envelope;

            double dAng = 0.024;
            float cos0 = (float)Math.cos(a - dAng), sin0 = (float)Math.sin(a - dAng);
            float cos1 = (float)Math.cos(a + dAng), sin1 = (float)Math.sin(a + dAng);

            float rIn = C_RADIUS - 0.28f, rOut = C_RADIUS + wRad * 0.70f;

            // Front clamp face
            brackets[(s - 1) * 3][0] = new Vector3f(CX + cos0 * rIn, CY + sin0 * rIn, -tZ * 0.72f);
            brackets[(s - 1) * 3][1] = new Vector3f(CX + cos1 * rIn, CY + sin1 * rIn, -tZ * 0.72f);
            brackets[(s - 1) * 3][2] = new Vector3f(CX + cos1 * rOut, CY + sin1 * rOut, -tZ * 0.35f);
            brackets[(s - 1) * 3][3] = new Vector3f(CX + cos0 * rOut, CY + sin0 * rOut, -tZ * 0.35f);

            // Back clamp face
            brackets[(s - 1) * 3 + 1][0] = new Vector3f(CX + cos1 * rIn, CY + sin1 * rIn, tZ * 0.72f);
            brackets[(s - 1) * 3 + 1][1] = new Vector3f(CX + cos0 * rIn, CY + sin0 * rIn, tZ * 0.72f);
            brackets[(s - 1) * 3 + 1][2] = new Vector3f(CX + cos0 * rOut, CY + sin0 * rOut, tZ * 0.35f);
            brackets[(s - 1) * 3 + 1][3] = new Vector3f(CX + cos1 * rOut, CY + sin1 * rOut, tZ * 0.35f);

            // Inner bridge face along rail
            brackets[(s - 1) * 3 + 2][0] = new Vector3f(CX + cos0 * rIn, CY + sin0 * rIn, -tZ * 0.72f);
            brackets[(s - 1) * 3 + 2][1] = new Vector3f(CX + cos0 * rIn, CY + sin0 * rIn,  tZ * 0.72f);
            brackets[(s - 1) * 3 + 2][2] = new Vector3f(CX + cos1 * rIn, CY + sin1 * rIn,  tZ * 0.72f);
            brackets[(s - 1) * 3 + 2][3] = new Vector3f(CX + cos1 * rIn, CY + sin1 * rIn, -tZ * 0.72f);
        }
        return brackets;
    }

    private static void renderGoldBrackets(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        for (Vector3f[] b : GOLD_BRACKETS) {
            quad(matrices, vertices, b[0], b[1], b[2], b[3], light, overlay, 0xFFB88A43);
        }
    }

    private static Vector3f[][] createTopTalon() {
        double a0 = Math.toRadians(45);
        float b0x = CX + (float)Math.cos(a0) * C_RADIUS;
        float b0y = CY + (float)Math.sin(a0) * C_RADIUS;
        float t0x = b0x - 1.0f, t0y = b0y + 1.1f;

        double a1 = Math.toRadians(305);
        float b1x = CX + (float)Math.cos(a1) * C_RADIUS;
        float b1y = CY + (float)Math.sin(a1) * C_RADIUS;
        float t1x = b1x + 1.1f, t1y = b1y - 0.9f;

        return new Vector3f[][]{
            {new Vector3f(b0x - 0.45f, b0y + 0.45f, -0.2f), new Vector3f(b0x + 0.45f, b0y + 0.22f, -0.2f),
             new Vector3f(t0x, t0y, 0.0f), new Vector3f(t0x, t0y, 0.0f)},
            {new Vector3f(b0x + 0.45f, b0y + 0.22f, 0.2f), new Vector3f(b0x - 0.45f, b0y + 0.45f, 0.2f),
             new Vector3f(t0x, t0y, 0.0f), new Vector3f(t0x, t0y, 0.0f)},
            {new Vector3f(b0x - 0.45f, b0y + 0.45f, -0.2f), new Vector3f(t0x, t0y, 0.0f),
             new Vector3f(t0x, t0y, 0.0f), new Vector3f(b0x - 0.45f, b0y + 0.45f, 0.2f)},
            {new Vector3f(b0x + 0.45f, b0y + 0.22f, 0.2f), new Vector3f(t0x, t0y, 0.0f),
             new Vector3f(t0x, t0y, 0.0f), new Vector3f(b0x + 0.45f, b0y + 0.22f, -0.2f)},

            {new Vector3f(b1x - 0.45f, b1y + 0.45f, -0.2f), new Vector3f(b1x + 0.45f, b1y + 0.22f, -0.2f),
             new Vector3f(t1x, t1y, 0.0f), new Vector3f(t1x, t1y, 0.0f)},
            {new Vector3f(b1x + 0.45f, b1y + 0.22f, 0.2f), new Vector3f(b1x - 0.45f, b1y + 0.45f, 0.2f),
             new Vector3f(t1x, t1y, 0.0f), new Vector3f(t1x, t1y, 0.0f)},
            {new Vector3f(b1x - 0.45f, b1y + 0.45f, -0.2f), new Vector3f(t1x, t1y, 0.0f),
             new Vector3f(t1x, t1y, 0.0f), new Vector3f(b1x - 0.45f, b1y + 0.45f, 0.2f)},
            {new Vector3f(b1x + 0.45f, b1y + 0.22f, 0.2f), new Vector3f(t1x, t1y, 0.0f),
             new Vector3f(t1x, t1y, 0.0f), new Vector3f(b1x + 0.45f, b1y + 0.22f, -0.2f)}
        };
    }

    private static void renderTopTalon(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        for (Vector3f[] q : TOP_TALON) {
            quad(matrices, vertices, q[0], q[1], q[2], q[3], light, overlay, 0xFFB88A43);
        }
    }

    private static Vector3f[][] createPommelBlade() {
        float bTop = -38.6f, bMid = -40.5f, bTip = -42.8f;
        float wOut = 1.4f, wIn = 0.55f;

        return new Vector3f[][]{
            {new Vector3f(-wOut * 0.7f, bTop, -0.22f), new Vector3f(-wOut, bMid, -0.22f),
             new Vector3f(-wIn, bMid, -0.05f), new Vector3f(0.0f, bTop - 0.35f, -0.05f)},
            {new Vector3f(-wOut * 0.7f, bTop, 0.22f), new Vector3f(0.0f, bTop - 0.35f, 0.05f),
             new Vector3f(-wIn, bMid, 0.05f), new Vector3f(-wOut, bMid, 0.22f)},
            {new Vector3f(wOut * 0.7f, bTop, -0.22f), new Vector3f(0.0f, bTop - 0.35f, -0.05f),
             new Vector3f(wIn, bMid, -0.05f), new Vector3f(wOut, bMid, -0.22f)},
            {new Vector3f(wOut * 0.7f, bTop, 0.22f), new Vector3f(wOut, bMid, 0.22f),
             new Vector3f(wIn, bMid, 0.05f), new Vector3f(0.0f, bTop - 0.35f, 0.05f)},

            {new Vector3f(-wOut, bMid, -0.22f), new Vector3f(0.0f, bTip, 0.0f),
             new Vector3f(0.0f, bMid - 1.8f, -0.05f), new Vector3f(-wIn, bMid, -0.05f)},
            {new Vector3f(-wOut, bMid, 0.22f), new Vector3f(-wIn, bMid, 0.05f),
             new Vector3f(0.0f, bMid - 1.8f, 0.05f), new Vector3f(0.0f, bTip, 0.0f)},
            {new Vector3f(wOut, bMid, -0.22f), new Vector3f(wIn, bMid, -0.05f),
             new Vector3f(0.0f, bMid - 1.8f, -0.05f), new Vector3f(0.0f, bTip, 0.0f)},
            {new Vector3f(wOut, bMid, 0.22f), new Vector3f(0.0f, bTip, 0.0f),
             new Vector3f(0.0f, bMid - 1.8f, 0.05f), new Vector3f(wIn, bMid, 0.05f)},

            {new Vector3f(-wOut, bMid, -0.22f), new Vector3f(-wOut * 0.7f, bTop, -0.22f),
             new Vector3f(-wOut * 0.7f, bTop, 0.22f), new Vector3f(-wOut, bMid, 0.22f)},
            {new Vector3f(wOut * 0.7f, bTop, -0.22f), new Vector3f(wOut, bMid, -0.22f),
             new Vector3f(wOut, bMid, 0.22f), new Vector3f(wOut * 0.7f, bTop, 0.22f)},
            {new Vector3f(-wOut, bMid, -0.22f), new Vector3f(0.0f, bTip, 0.0f),
             new Vector3f(0.0f, bTip, 0.0f), new Vector3f(-wOut, bMid, 0.22f)},
            {new Vector3f(0.0f, bTip, 0.0f), new Vector3f(wOut, bMid, -0.22f),
             new Vector3f(wOut, bMid, 0.22f), new Vector3f(0.0f, bTip, 0.0f)}
        };
    }

    private static void renderPommelBlade(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        for (Vector3f[] q : POMMEL_BLADE) {
            quad(matrices, vertices, q[0], q[1], q[2], q[3], light, overlay, 0xFFB88A43);
        }
    }

    private void renderFacetedGem(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                                  float rx, float ry, float rz, int color) {
        int facets = 6;
        Vector3f top = new Vector3f(0.0f, ry, 0.0f);
        Vector3f bot = new Vector3f(0.0f, -ry, 0.0f);
        Vector3f[] equator = new Vector3f[facets];

        for (int i = 0; i < facets; i++) {
            double ang = 2 * Math.PI * i / facets;
            equator[i] = new Vector3f(rx * (float)Math.cos(ang), 0.0f, rz * (float)Math.sin(ang));
        }

        for (int i = 0; i < facets; i++) {
            int nxt = (i + 1) % facets;
            quad(matrices, vertices, top, equator[i], equator[nxt], equator[nxt], light, overlay, color);
            quad(matrices, vertices, bot, equator[nxt], equator[i], equator[i], light, overlay, color);
        }
    }

    private void renderDiamondBezel(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        float sizeX = 2.4f, sizeY = 3.3f, width = 0.42f, halfT = 0.20f;
        Vector3f[] outPts = {
            new Vector3f(0.0f, sizeY, 0.0f),
            new Vector3f(sizeX, 0.0f, 0.0f),
            new Vector3f(0.0f, -sizeY, 0.0f),
            new Vector3f(-sizeX, 0.0f, 0.0f)
        };
        Vector3f[] inPts = {
            new Vector3f(0.0f, sizeY - width, 0.0f),
            new Vector3f(sizeX - width, 0.0f, 0.0f),
            new Vector3f(0.0f, -sizeY + width, 0.0f),
            new Vector3f(-sizeX + width, 0.0f, 0.0f)
        };

        for (int i = 0; i < 4; i++) {
            int nxt = (i + 1) % 4;
            Vector3f p0F = new Vector3f(outPts[i].x, outPts[i].y, halfT);
            Vector3f p1F = new Vector3f(outPts[nxt].x, outPts[nxt].y, halfT);
            Vector3f p2F = new Vector3f(inPts[nxt].x, inPts[nxt].y, halfT);
            Vector3f p3F = new Vector3f(inPts[i].x, inPts[i].y, halfT);

            Vector3f p0B = new Vector3f(outPts[i].x, outPts[i].y, -halfT);
            Vector3f p1B = new Vector3f(outPts[nxt].x, outPts[nxt].y, -halfT);
            Vector3f p2B = new Vector3f(inPts[nxt].x, inPts[nxt].y, -halfT);
            Vector3f p3B = new Vector3f(inPts[i].x, inPts[i].y, -halfT);

            quad(matrices, vertices, p0F, p1F, p2F, p3F, light, overlay, 0xFFB88A43);
            quad(matrices, vertices, p1B, p0B, p3B, p2B, light, overlay, 0xFFB88A43);
            quad(matrices, vertices, p0B, p1B, p1F, p0F, light, overlay, 0xFF9E7432);
            quad(matrices, vertices, p3F, p2F, p2B, p3B, light, overlay, 0xFFD8AA53);
        }
    }

    private void renderEmberMotes(PoseStack matrices, VertexConsumer vertices, int overlay, int color) {
        float[][] motes = {
            {2.3f, 1.1f, 0.8f, 0.22f},
            {-1.9f, 2.0f, -0.6f, 0.18f},
            {1.6f, -2.1f, -0.7f, 0.20f},
            {-2.1f, -1.4f, 0.9f, 0.16f}
        };
        for (float[] m : motes) {
            matrices.pushPose();
            matrices.translate(m[0] / 16.0f, m[1] / 16.0f, m[2] / 16.0f);
            renderFacetedGem(matrices, vertices, LightTexture.FULL_BRIGHT, overlay, m[3], m[3] * 1.5f, m[3], color);
            matrices.popPose();
        }
    }

    private static void quad(PoseStack matrices, VertexConsumer vertices, Vector3f a,
                             Vector3f b, Vector3f c, Vector3f d,
                             int light, int overlay, int color) {
        var normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        for (var point : new Vector3f[]{a, b, c, d}) {
            boolean gold = ((color >> 16) & 255) == 184;
            float baseU = Math.clamp(0.02f + (point.x + 7.5f) / 15.0f * 0.44f, 0.02f, 0.48f);
            float u = gold ? baseU + 0.50f : baseU;
            float v = Math.clamp(0.02f + (point.y - 12.0f) / 17.0f * 0.44f, 0.02f, 0.48f);
            vertex(matrices, vertices, point, normal, light, overlay, color, u, v);
        }
    }

    private static void vertex(PoseStack matrices, VertexConsumer vertices, Vector3f point,
                               Vector3f normal, int light, int overlay, int color, float u, float v) {
        vertices.addVertex(matrices.last().pose(), point.x / 16.0f, point.y / 16.0f, point.z / 16.0f)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, color >>> 24).setUv(u, v)
                .setUv1(overlay & 65535, overlay >> 16).setUv2(light & 65535, light >> 16)
                .setNormal(matrices.last(), normal.x, normal.y, normal.z);
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
