package net.godwand.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Silent Witch Staff - The ornate golden magic staff of Monica Everett (Secrets of the Silent Witch).
 * Features 3 top beveled ring clamps holding spherical amethyst beads and 4-faceted chevron-collared obelisk spikes,
 * a beveled circular halo ring with sculpted outer side cuffs, an inner Y-shaped crescent cradle with
 * a central ridge and U-cup holding a floating lavender-amethyst orb, a central column with recessed-panel
 * stepped wing gussets, a curved scroll crossbar with outer barbs and seamless spiral volutes, a biconical neck bulb
 * with a horizontal equator crease and purple teardrop gem, two 4-pointed scalloped conical bells each inlaid with
 * 4 oval purple gems, and levitating golden drop needles inlaid with vertical oval purple gems.
 */
public class SilentWitchModel extends Model {
    private final ModelPart root, manaCore, leftPendant, rightPendant, leftNeedle, rightNeedle;
    private float animationTime;
    private float charge;

    private static final float CY = 23.2f;
    private static final float ORB_Y = 24.15f;
    private static final float PENDANT_X = 4.85f;
    private static final float PENDANT_Y = 17.05f;

    private static final int GOLD_BRIGHT = 0xFFEAD07A;
    private static final int GOLD_MAIN = 0xFFD4A84F;
    private static final int GOLD_SHADOW = 0xFF9E742B;
    private static final int GRIP_COLOR = 0xFFC89B3C;
    private static final int AMETHYST_SOLID = 0xFFAC66F5;
    private static final int AMETHYST_DEEP = 0xFF8A42D6;
    private static final int AMETHYST_GLOW = 0xFFD8A8FF;
    private static final int AMETHYST_AURA_RGB = 0xD08BFF;

    private static final Vector3f[][] GOLD_MAIN_QUADS = createGoldMainQuads();
    private static final Vector3f[][] GOLD_BRIGHT_QUADS = createGoldBrightQuads();
    private static final Vector3f[][] GOLD_SHADOW_QUADS = createGoldShadowQuads();
    private static final Vector3f[][] GRIP_QUADS = createGripQuads();
    private static final Vector3f[][] CRYSTAL_INLAY_QUADS = createCrystalInlayQuads();
    private static final Vector3f[][] CRYSTAL_HIGHLIGHT_QUADS = createCrystalHighlightQuads();

    private static final Vector3f[][] PENDANT_BELL_GOLD_QUADS = createPendantBellGoldQuads();
    private static final Vector3f[][] PENDANT_BELL_SHADOW_QUADS = createPendantBellShadowQuads();
    private static final Vector3f[][] PENDANT_GEMS_QUADS = createPendantGemsQuads();
    private static final Vector3f[][] PENDANT_NEEDLE_GOLD_QUADS = createPendantNeedleGoldQuads();
    private static final Vector3f[][] PENDANT_NEEDLE_GEM_QUADS = createPendantNeedleGemQuads();

    private static final Vector3f[][] ORB_SOLID_QUADS = createSphereQuads(1.32f, 16, 24);
    private static final Vector3f[][] ORB_SHEEN_QUADS = createSphereQuads(1.35f, 12, 18);
    private static final Vector3f[][] ORB_AURA_OUTER_QUADS = createSphereQuads(1.46f, 12, 18);

    public SilentWitchModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.manaCore = root.getChild("mana_core");
        this.leftPendant = root.getChild("left_pendant");
        this.rightPendant = root.getChild("right_pendant");
        this.leftNeedle = this.leftPendant.getChild("left_needle");
        this.rightNeedle = this.rightPendant.getChild("right_needle");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("mana_core", CubeListBuilder.create(), PartPose.offset(0.0f, ORB_Y, 0.0f));
        root.addOrReplaceChild("left_pendant", CubeListBuilder.create(), PartPose.offset(-PENDANT_X, PENDANT_Y, 0.0f))
                .addOrReplaceChild("left_needle", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_pendant", CubeListBuilder.create(), PartPose.offset(PENDANT_X, PENDANT_Y, 0.0f))
                .addOrReplaceChild("right_needle", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int skillMode) {
        this.animationTime = time;
        this.charge = net.minecraft.util.Mth.clamp(chargeRatio, 0.0f, 1.0f);

        float spinSpeed = 0.032f + this.charge * 0.14f;
        this.manaCore.x = 0.0f;
        this.manaCore.y = ORB_Y;
        this.manaCore.z = 0.0f;
        this.manaCore.yRot = time * spinSpeed;
        this.manaCore.xRot = (float) Math.sin(time * 0.04f) * 0.08f;
        this.manaCore.zRot = (float) Math.cos(time * 0.03f) * 0.08f;

        float swayAmp = 0.028f + this.charge * 0.045f;
        float swayZ = (float) Math.sin(time * 0.045f) * swayAmp;
        float swayX = (float) Math.cos(time * 0.036f) * (swayAmp * 0.6f);

        this.leftPendant.x = -PENDANT_X;
        this.leftPendant.y = PENDANT_Y;
        this.leftPendant.z = 0.0f;
        this.leftPendant.zRot = -swayZ;
        this.leftPendant.xRot = swayX;

        this.rightPendant.x = PENDANT_X;
        this.rightPendant.y = PENDANT_Y;
        this.rightPendant.z = 0.0f;
        this.rightPendant.zRot = swayZ;
        this.rightPendant.xRot = -swayX;

        float needleBob = (float) Math.sin(time * 0.07f) * (0.18f + this.charge * 0.16f);
        this.leftNeedle.y = needleBob;
        this.leftNeedle.yRot = time * (0.025f + this.charge * 0.08f);
        this.rightNeedle.y = -needleBob;
        this.rightNeedle.yRot = -time * (0.025f + this.charge * 0.08f);
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        renderQuads(matrices, vertexConsumer, GOLD_MAIN_QUADS, light, overlay, GOLD_MAIN);
        renderQuads(matrices, vertexConsumer, GOLD_BRIGHT_QUADS, light, overlay, GOLD_BRIGHT);
        renderQuads(matrices, vertexConsumer, GOLD_SHADOW_QUADS, light, overlay, GOLD_SHADOW);
        renderQuads(matrices, vertexConsumer, GRIP_QUADS, light, overlay, GRIP_COLOR);
        renderQuads(matrices, vertexConsumer, CRYSTAL_INLAY_QUADS, LightTexture.FULL_BRIGHT, overlay, AMETHYST_SOLID);
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer,
                           Supplier<VertexConsumer> emissiveBuffer, int light, int overlay, int coreColor) {
        // PASS 1: All solid geometry (never interleave with emissiveBuffer to prevent buffer flush invalidation)
        VertexConsumer solid = solidBuffer.get();

        renderQuads(matrices, solid, GOLD_MAIN_QUADS, light, overlay, GOLD_MAIN);
        renderQuads(matrices, solid, GOLD_BRIGHT_QUADS, light, overlay, GOLD_BRIGHT);
        renderQuads(matrices, solid, GOLD_SHADOW_QUADS, light, overlay, GOLD_SHADOW);
        renderQuads(matrices, solid, GRIP_QUADS, light, overlay, GRIP_COLOR);
        renderQuads(matrices, solid, CRYSTAL_INLAY_QUADS, light, overlay, AMETHYST_SOLID);
        renderQuads(matrices, solid, CRYSTAL_HIGHLIGHT_QUADS, light, overlay, AMETHYST_GLOW);

        // Left & right hanging 4-pointed bells and floating drop needles (solid pass)
        renderPendantSolid(matrices, solid, this.leftPendant, this.leftNeedle, light, overlay);
        renderPendantSolid(matrices, solid, this.rightPendant, this.rightNeedle, light, overlay);

        // Floating Lavender-Amethyst Orb in upper cradle (solid body)
        float orbBob = (float) Math.sin(animationTime * 0.045f) * 0.006f;
        float orbScale = 1.0f + charge * 0.10f;
        matrices.pushPose();
        manaCore.translateAndRotate(matrices);
        matrices.translate(0.0f, orbBob, 0.0f);
        matrices.scale(orbScale, orbScale, orbScale);
        renderQuads(matrices, solid, ORB_SOLID_QUADS, light, overlay, AMETHYST_SOLID);
        matrices.popPose();

        // PASS 2: All emissive / luminous geometry
        VertexConsumer emissive = emissiveBuffer.get();

        // Soft luminous sheen on the 3 top spike spheres and neck teardrop gem
        int gemAlpha = 95 + (int) (charge * 70);
        int gemGlowColor = (gemAlpha << 24) | AMETHYST_AURA_RGB;
        renderQuads(matrices, emissive, CRYSTAL_INLAY_QUADS, LightTexture.FULL_BRIGHT, overlay, gemGlowColor);
        renderQuads(matrices, emissive, CRYSTAL_HIGHLIGHT_QUADS, LightTexture.FULL_BRIGHT, overlay,
                (140 << 24) | (AMETHYST_GLOW & 0xFFFFFF));

        // Luminous amethyst gems on both hanging bells and floating needles
        renderPendantEmissive(matrices, emissive, this.leftPendant, this.leftNeedle, overlay, gemGlowColor);
        renderPendantEmissive(matrices, emissive, this.rightPendant, this.rightNeedle, overlay, gemGlowColor);

        // Radiant orb sheen & outer magical halo
        matrices.pushPose();
        manaCore.translateAndRotate(matrices);
        matrices.translate(0.0f, orbBob, 0.0f);
        matrices.scale(orbScale, orbScale, orbScale);

        int alphaInner = Math.min(255, 72 + (int) (charge * 75));
        int alphaOuter = Math.min(255, 26 + (int) (charge * 46));
        renderQuads(matrices, emissive, ORB_SHEEN_QUADS, LightTexture.FULL_BRIGHT, overlay,
                (alphaInner << 24) | (AMETHYST_GLOW & 0xFFFFFF));
        renderQuads(matrices, emissive, ORB_AURA_OUTER_QUADS, LightTexture.FULL_BRIGHT, overlay,
                (alphaOuter << 24) | AMETHYST_AURA_RGB);
        matrices.popPose();
    }

    private static void renderPendantSolid(PoseStack matrices, VertexConsumer solid,
                                           ModelPart pendant, ModelPart needle, int light, int overlay) {
        matrices.pushPose();
        pendant.translateAndRotate(matrices);
        renderQuads(matrices, solid, PENDANT_BELL_GOLD_QUADS, light, overlay, GOLD_MAIN);
        renderQuads(matrices, solid, PENDANT_BELL_SHADOW_QUADS, light, overlay, GOLD_SHADOW);
        renderQuads(matrices, solid, PENDANT_GEMS_QUADS, light, overlay, AMETHYST_SOLID);

        matrices.pushPose();
        needle.translateAndRotate(matrices);
        renderQuads(matrices, solid, PENDANT_NEEDLE_GOLD_QUADS, light, overlay, GOLD_MAIN);
        renderQuads(matrices, solid, PENDANT_NEEDLE_GEM_QUADS, light, overlay, AMETHYST_SOLID);
        matrices.popPose();

        matrices.popPose();
    }

    private static void renderPendantEmissive(PoseStack matrices, VertexConsumer emissive,
                                              ModelPart pendant, ModelPart needle, int overlay, int gemGlowColor) {
        matrices.pushPose();
        pendant.translateAndRotate(matrices);
        renderQuads(matrices, emissive, PENDANT_GEMS_QUADS, LightTexture.FULL_BRIGHT, overlay, gemGlowColor);

        matrices.pushPose();
        needle.translateAndRotate(matrices);
        renderQuads(matrices, emissive, PENDANT_NEEDLE_GEM_QUADS, LightTexture.FULL_BRIGHT, overlay, gemGlowColor);
        matrices.popPose();

        matrices.popPose();
    }

    private static void renderQuads(PoseStack matrices, VertexConsumer vertices, Vector3f[][] quads,
                                    int light, int overlay, int color) {
        for (Vector3f[] q : quads) {
            quad(matrices, vertices, q[0], q[1], q[2], q[3], light, overlay, color);
        }
    }

    private static void quad(PoseStack matrices, VertexConsumer vertices,
                             Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                             int light, int overlay, int color) {
        PoseStack.Pose entry = matrices.last();
        var pose = entry.pose();

        float v1x = p1.x() - p0.x(), v1y = p1.y() - p0.y(), v1z = p1.z() - p0.z();
        float v2x = p2.x() - p0.x(), v2y = p2.y() - p0.y(), v2z = p2.z() - p0.z();
        float nx = v1y * v2z - v1z * v2y;
        float ny = v1z * v2x - v1x * v2z;
        float nz = v1x * v2y - v1y * v2x;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-5f) {
            nx /= len; ny /= len; nz /= len;
        } else {
            ny = 1.0f;
        }

        int rgb = color & 0xFFFFFF;
        boolean energy = rgb == (AMETHYST_SOLID & 0xFFFFFF)
                || rgb == (AMETHYST_DEEP & 0xFFFFFF)
                || rgb == (AMETHYST_GLOW & 0xFFFFFF)
                || rgb == AMETHYST_AURA_RGB;
        boolean grip = rgb == (GRIP_COLOR & 0xFFFFFF);

        // Map each material strictly within its own quadrant on eclipse_materials.png:
        // Upper-right (0.52..0.98, 0.02..0.48): hammered metal (gold/bronze)
        // Lower-left  (0.02..0.48, 0.52..0.98): fine grain (grip)
        // Lower-right (0.52..0.98, 0.52..0.98): luminous energy/crystal
        // Polished metal (owner, 2026-11-14: "no shine at all, it looks awful"): no grain. Gold and grip take one clean, near white spot of the sheet, so the vertex
        // colour is the metal's own; the gloss is painted per face from where it looks, in view space: a broad sheen and a tight highlight that slide over the
        // facets as the wand turns. Only the energy keeps its texture.
        float u0 = energy ? 0.52f : 0.745f, v0 = u0, u1 = energy ? 0.98f : 0.755f, v1 = u1;
        if (!energy) {
            Vector3f nv = entry.normal().transform(new Vector3f(nx, ny, nz));
            float nl = nv.length();
            if (nl > 1e-5f) nv.div(nl);
            // Either side of a face may be the one looking at us (nothing is culled).
            if (nv.z < 0) nv.negate();
            float diff = Math.max(0, nv.x * -.33f + nv.y * .72f + nv.z * .61f);
            float spec = (float) Math.pow(Math.max(0, nv.x * -.19f + nv.y * .41f + nv.z * .89f), grip ? 12 : 20);
            // And the edge light of polished metal: faces turning away from the eye catch the sky.
            float rim = (1 - nv.z) * (1 - nv.z) * (1 - nv.z);
            float shade = .76f + .36f * diff, hot = Math.min(1, (grip ? .35f : .9f) * spec + (grip ? .1f : .3f) * rim);
            float cr = ((color >> 16) & 0xFF) * shade, cg = ((color >> 8) & 0xFF) * shade, cb = (color & 0xFF) * shade;
            color = (color & 0xFF000000) | Math.min(255, Math.round(cr + (255 - cr) * hot)) << 16 | Math.min(255, Math.round(cg + (246 - cg) * hot)) << 8
                    | Math.min(255, Math.round(cb + (214 - cb) * hot));
        }

        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        vertices.addVertex(pose, p0.x(), p0.y(), p0.z()).setColor(r, g, b, a).setUv(u0, v0)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
        vertices.addVertex(pose, p1.x(), p1.y(), p1.z()).setColor(r, g, b, a).setUv(u0, v1)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
        vertices.addVertex(pose, p2.x(), p2.y(), p2.z()).setColor(r, g, b, a).setUv(u1, v1)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
        vertices.addVertex(pose, p3.x(), p3.y(), p3.z()).setColor(r, g, b, a).setUv(u1, v0)
                .setOverlay(overlay).setLight(light).setNormal(entry, nx, ny, nz);
    }

    private static Vector3f pt(float x, float y, float z) {
        return new Vector3f(x / 16.0f, y / 16.0f, z / 16.0f);
    }

    private static Vector3f[][] createGoldMainQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Main Halo Ring (r = 3.55 .. 5.15, 40 segments around 360 degrees)
        int ringSteps = 40;
        for (int i = 0; i < ringSteps; i++) {
            double a0 = 2.0 * Math.PI * i / ringSteps;
            double a1 = 2.0 * Math.PI * (i + 1) / ringSteps;
            addRingBandSection(list, a0, a1, 3.55f, 4.35f, 5.15f, 0.28f, 0.62f, 0.30f);
        }

        // 2. Left & Right Outer Ring Cuffs (main body wrapping the outer half of the ring r = 3.92 .. 5.42)
        addSideRingCuff(list, Math.toRadians(156.0), Math.toRadians(204.0), Math.toRadians(163.0), Math.toRadians(197.0));
        addSideRingCuff(list, Math.toRadians(-24.0), Math.toRadians(24.0), Math.toRadians(-17.0), Math.toRadians(17.0));

        // 3. Inner Y-Shaped Crescent Cradle (left lower facet)
        addInnerYCradleGold(list, false);

        // 4. Top 3 Crown 4-Faceted Diamond Spikes with V-Chevron Socket Collars (right-side facets + top clamp sides)
        double[] baseAnglesDeg = {90.0, 105.5, 74.5};
        double[] tiltAnglesDeg = {90.0, 106.5, 73.5};
        float[] spikeLengths = {8.55f, 7.85f, 7.85f};
        for (int s = 0; s < 3; s++) {
            double bAng = Math.toRadians(baseAnglesDeg[s]);
            double tAng = Math.toRadians(tiltAnglesDeg[s]);
            addTopRingClamp(list, bAng, false);
            addCrownSpikeFaceted(list, bAng, tAng, spikeLengths[s], false);
        }

        // 5. Upper Neck Cone (above the sharp equator at y = 13.35) & Central Column (y = 15.6 .. 18.25)
        float[][] upperNeckAndColumn = {
            {13.35f, 1.48f, 1.18f},
            {14.55f, 1.04f, 0.88f},
            {15.65f, 0.64f, 0.64f},
            {18.20f, 0.62f, 0.62f}
        };
        addLoftOctagon(list, upperNeckAndColumn, 0.0f, 0.0f);

        // 6. Stepped Wing Gusset Plates flanking the central column below the ring
        for (int side : new int[]{-1, 1}) {
            addSteppedWingGusset(list, side);
        }

        // 7. Curved Scroll Crossbar with sharp outer top barbs & seamless inward-curling 3D spiral volutes
        addCrossbarAndScrolls(list);

        // 8. Upper and Lower Golden Shaft Sections
        float[][] upperShaft = {
            {5.8f, 0.72f, 0.72f},
            {10.0f, 0.70f, 0.70f},
            {11.6f, 0.68f, 0.68f}
        };
        addLoftOctagon(list, upperShaft, 0.0f, 0.0f);

        float[][] lowerShaft = {
            {-36.9f, 0.65f, 0.65f},
            {-26.0f, 0.68f, 0.68f},
            {-15.5f, 0.70f, 0.70f},
            {-5.8f, 0.72f, 0.72f}
        };
        addLoftOctagon(list, lowerShaft, 0.0f, 0.0f);

        // 9. Arched 3-Fin Tail Pommel (y = -37.8 .. -41.5)
        addFinPommel(list);

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createGoldBrightQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Top 3 Beveled Gold Ring Clamps & Left Lit Facets of the 3 Top Spikes
        double[] baseAnglesDeg = {90.0, 105.5, 74.5};
        double[] tiltAnglesDeg = {90.0, 106.5, 73.5};
        float[] spikeLengths = {8.55f, 7.85f, 7.85f};
        for (int s = 0; s < 3; s++) {
            double bAng = Math.toRadians(baseAnglesDeg[s]);
            double tAng = Math.toRadians(tiltAnglesDeg[s]);
            addTopRingClamp(list, bAng, true);
            addCrownSpikeFaceted(list, bAng, tAng, spikeLengths[s], true);
        }

        // 2. Diagonal Accent Straps across the Left & Right Side Ring Cuffs
        addSideRingCuff(list, Math.toRadians(175.0), Math.toRadians(184.0), Math.toRadians(178.0), Math.toRadians(186.0));
        addSideRingCuff(list, Math.toRadians(-4.0), Math.toRadians(5.0), Math.toRadians(-6.0), Math.toRadians(2.0));

        // 3. Raised U-Shaped Socket Rim cradling the bottom half of the floating orb
        int cupSteps = 18;
        for (int i = 0; i < cupSteps; i++) {
            double a0 = Math.toRadians(-162.0 + 144.0 * i / cupSteps);
            double a1 = Math.toRadians(-162.0 + 144.0 * (i + 1) / cupSteps);
            float rIn = 1.38f, rOut = 1.62f;
            float x0In = rIn * (float) Math.cos(a0), y0In = ORB_Y + rIn * (float) Math.sin(a0);
            float x1In = rIn * (float) Math.cos(a1), y1In = ORB_Y + rIn * (float) Math.sin(a1);
            float x0Out = rOut * (float) Math.cos(a0), y0Out = ORB_Y + rOut * (float) Math.sin(a0);
            float x1Out = rOut * (float) Math.cos(a1), y1Out = ORB_Y + rOut * (float) Math.sin(a1);
            addQuad(list, pt(x0Out, y0Out, 0.46f), pt(x1Out, y1Out, 0.46f), pt(x1In, y1In, 0.48f), pt(x0In, y0In, 0.48f));
            addQuad(list, pt(x0In, y0In, -0.48f), pt(x1In, y1In, -0.48f), pt(x1Out, y1Out, -0.46f), pt(x0Out, y0Out, -0.46f));
            addQuad(list, pt(x0In, y0In, 0.48f), pt(x1In, y1In, 0.48f), pt(x1In, y1In, -0.48f), pt(x0In, y0In, -0.48f));
        }

        // 4. Inner Y-Cradle lit upper facets
        addInnerYCradleGold(list, true);

        // 5. Column Capital Ring (at base of halo ring y = 18.25) & Lower Neck Torus Collar Ring (y = 11.55)
        addHorizontalCollar(list, 18.22f, 0.44f, 0.88f);
        addHorizontalCollar(list, 11.55f, 0.50f, 0.96f);

        // 6. Subtle Shaft Collars & Double Pommel Ferrule Rings
        float[] ringHeights = {5.8f, -5.8f, -16.5f, -27.0f};
        float[] ringRadii = {0.82f, 0.82f, 0.78f, 0.76f};
        for (int i = 0; i < ringHeights.length; i++) {
            addHorizontalCollar(list, ringHeights[i], 0.34f, ringRadii[i]);
        }
        addHorizontalCollar(list, -36.85f, 0.48f, 1.02f);
        addHorizontalCollar(list, -37.65f, 0.52f, 1.08f);

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createGoldShadowQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Inner Y-Cradle right lower shaded facet (creates the crisp central vertical crease)
        addInnerYCradleShadow(list);

        // 2. Lower half of Biconical Neck Bulb below the sharp equator crease (y = 11.60 .. 13.35)
        float[][] lowerNeck = {
            {11.60f, 0.68f, 0.68f},
            {12.40f, 1.12f, 0.94f},
            {13.35f, 1.48f, 1.18f}
        };
        addLoftOctagon(list, lowerNeck, 0.0f, 0.0f);

        // 3. Recessed Inner Panels on the Stepped Wing Gussets
        for (int side : new int[]{-1, 1}) {
            for (int zSide : new int[]{-1, 1}) {
                float z = zSide * 0.35f;
                Vector3f p0 = pt(side * 0.65f, 16.05f, z);
                Vector3f p1 = pt(side * 0.65f, 17.95f, z);
                Vector3f p2 = pt(side * 2.15f, 17.95f, z);
                Vector3f p3 = pt(side * 1.42f, 16.82f, z);
                if (side * zSide > 0) {
                    list.add(new Vector3f[]{p0, p3, p2, p1});
                } else {
                    list.add(new Vector3f[]{p0, p1, p2, p3});
                }
            }
        }

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createGripQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        // Central holding section centered around Y = 0 (-5.8 .. +5.8) in warm burnished gold
        float[][] gripLevels = {
            {-5.8f, 0.74f, 0.74f},
            {0.0f, 0.74f, 0.74f},
            {5.8f, 0.74f, 0.74f}
        };
        addLoftOctagon(list, gripLevels, 0.0f, 0.0f);
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createCrystalInlayQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Three Plump Spherical Amethyst Beads resting on the 3 top ring clamps
        double[] baseAnglesDeg = {90.0, 105.5, 74.5};
        double[] tiltAnglesDeg = {90.0, 106.5, 73.5};
        for (int s = 0; s < 3; s++) {
            double bAng = Math.toRadians(baseAnglesDeg[s]);
            double tAng = Math.toRadians(tiltAnglesDeg[s]);
            float bx = 5.15f * (float) Math.cos(bAng);
            float by = CY + 5.15f * (float) Math.sin(bAng);
            float dx = (float) Math.cos(tAng), dy = (float) Math.sin(tAng);
            addSphereAt(list, bx + dx * 0.94f, by + dy * 0.94f, 0.0f, 0.65f, 10, 14);
        }

        // 2. Front & Back Vertical Teardrop Amethyst Gem above the Neck Equator Crease (y = 13.38 .. 15.22)
        for (int zSide : new int[]{-1, 1}) {
            addTeardropGem(list, 0.0f, 13.38f, 15.22f, 0.27f, zSide * 1.06f, zSide * 1.24f, zSide > 0);
        }

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createCrystalHighlightQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        // Soft lilac inner highlight on the 3 top amethyst spheres
        double[] baseAnglesDeg = {90.0, 105.5, 74.5};
        double[] tiltAnglesDeg = {90.0, 106.5, 73.5};
        for (int s = 0; s < 3; s++) {
            double bAng = Math.toRadians(baseAnglesDeg[s]);
            double tAng = Math.toRadians(tiltAnglesDeg[s]);
            float bx = 5.15f * (float) Math.cos(bAng);
            float by = CY + 5.15f * (float) Math.sin(bAng);
            float dx = (float) Math.cos(tAng), dy = (float) Math.sin(tAng);
            addSphereAt(list, bx + dx * 1.02f - 0.10f, by + dy * 1.02f + 0.10f, 0.24f, 0.42f, 6, 8);
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPendantBellGoldQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Tiny top suspension pin below the scroll volute (y = 0.22 .. -0.15)
        float[][] pin = {
            {0.22f, 0.07f, 0.07f},
            {-0.15f, 0.09f, 0.09f}
        };
        addLoftOctagon(list, pin, 0.0f, 0.0f);

        // 2. Conical 4-Pointed Scalloped Bell Skirt (y = -0.10 down to -6.35 at the 4 tips, -5.65 in the 4 valleys)
        int hSteps = 10;
        int cSteps = 24;
        Vector3f[][] grid = new Vector3f[hSteps + 1][cSteps];
        for (int i = 0; i <= hSteps; i++) {
            float t = i / (float) hSteps;
            float t2 = t * t;
            float t3 = t2 * t;
            for (int j = 0; j < cSteps; j++) {
                double theta = 2.0 * Math.PI * j / cSteps;
                float lobe = 0.5f + 0.5f * (float) Math.cos(4.0 * theta);
                float r = 0.06f + 1.12f * t + 0.52f * t3 * (0.35f + 0.65f * lobe);
                float y = -0.10f - t * (5.45f + 0.78f * t2 * lobe);
                grid[i][j] = pt(r * (float) Math.cos(theta), y, r * (float) Math.sin(theta));
            }
        }
        for (int i = 0; i < hSteps; i++) {
            for (int j = 0; j < cSteps; j++) {
                int nxt = (j + 1) % cSteps;
                list.add(new Vector3f[]{grid[i][j], grid[i + 1][j], grid[i + 1][nxt], grid[i][nxt]});
            }
        }

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPendantBellShadowQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        // Inner dark-gold bell cavity & bottom closure rim
        int cSteps = 24;
        Vector3f center = pt(0.0f, -5.25f, 0.0f);
        for (int j = 0; j < cSteps; j++) {
            int nxt = (j + 1) % cSteps;
            double th0 = 2.0 * Math.PI * j / cSteps;
            double th1 = 2.0 * Math.PI * nxt / cSteps;
            float lobe0 = 0.5f + 0.5f * (float) Math.cos(4.0 * th0);
            float lobe1 = 0.5f + 0.5f * (float) Math.cos(4.0 * th1);
            float r0 = 1.18f + 0.52f * (0.35f + 0.65f * lobe0);
            float r1 = 1.18f + 0.52f * (0.35f + 0.65f * lobe1);
            float y0 = -5.55f - 0.78f * lobe0;
            float y1 = -5.55f - 0.78f * lobe1;
            Vector3f p0 = pt(r0 * (float) Math.cos(th0), y0, r0 * (float) Math.sin(th0));
            Vector3f p1 = pt(r1 * (float) Math.cos(th1), y1, r1 * (float) Math.sin(th1));
            list.add(new Vector3f[]{center, p1, p0, p0});
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPendantGemsQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        // 4 Vertical Oval Amethyst Cabochons around the lower-mid bell body (at 0, 90, 180, 270 degrees)
        for (int k = 0; k < 4; k++) {
            double ang = k * Math.PI * 0.5;
            float dirX = (float) Math.cos(ang), dirZ = (float) Math.sin(ang);
            float tanX = -dirZ, tanZ = dirX;
            float yMid = -4.52f;
            float rMid = 1.12f;
            float slope = 0.24f;
            int segs = 12;
            Vector3f apex = pt(dirX * (rMid + 0.16f), yMid, dirZ * (rMid + 0.16f));
            for (int i = 0; i < segs; i++) {
                double a0 = 2.0 * Math.PI * i / segs;
                double a1 = 2.0 * Math.PI * (i + 1) / segs;
                float u0 = 0.22f * (float) Math.cos(a0), v0 = 0.45f * (float) Math.sin(a0);
                float u1 = 0.22f * (float) Math.cos(a1), v1 = 0.45f * (float) Math.sin(a1);
                float r0 = rMid - v0 * slope + 0.03f;
                float r1 = rMid - v1 * slope + 0.03f;
                Vector3f p0 = pt(dirX * r0 + tanX * u0, yMid + v0, dirZ * r0 + tanZ * u0);
                Vector3f p1 = pt(dirX * r1 + tanX * u1, yMid + v1, dirZ * r1 + tanZ * u1);
                list.add(new Vector3f[]{apex, p0, p1, p1});
            }
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPendantNeedleGoldQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        // Continuous golden teardrop-spindle needle (frames the front/back vertical oval purple gem on the sides!)
        float[][] spindle = {
            {-6.65f, 0.0f, 0.0f},
            {-7.05f, 0.26f, 0.22f},
            {-7.55f, 0.38f, 0.30f},
            {-8.10f, 0.36f, 0.29f},
            {-8.65f, 0.31f, 0.28f},
            {-9.80f, 0.24f, 0.24f},
            {-11.30f, 0.14f, 0.14f},
            {-13.15f, 0.0f, 0.0f}
        };
        addLoftOctagon(list, spindle, 0.0f, 0.0f);
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPendantNeedleGemQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        // Front & Back Vertical Oval Purple Cabochon inlaid into the upper bulge of the golden drop needle
        // Narrower in X (rx = 0.23 < gold rx = 0.38) so a clean golden rim is visible on both left and right sides!
        for (int zSide : new int[]{-1, 1}) {
            float yMid = -7.75f;
            float rx = 0.23f, ry = 0.56f;
            float zBase = zSide * 0.27f;
            float zPeak = zSide * 0.39f;
            Vector3f apex = pt(0.0f, yMid, zPeak);
            int segs = 12;
            for (int i = 0; i < segs; i++) {
                double a0 = 2.0 * Math.PI * i / segs;
                double a1 = 2.0 * Math.PI * (i + 1) / segs;
                Vector3f p0 = pt(rx * (float) Math.cos(a0), yMid + ry * (float) Math.sin(a0), zBase);
                Vector3f p1 = pt(rx * (float) Math.cos(a1), yMid + ry * (float) Math.sin(a1), zBase);
                if (zSide > 0) {
                    list.add(new Vector3f[]{apex, p0, p1, p1});
                } else {
                    list.add(new Vector3f[]{apex, p1, p0, p0});
                }
            }
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static void addQuad(List<Vector3f[]> list, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        list.add(new Vector3f[]{p0, p1, p2, p3});
    }

    private static void addRingBandSection(List<Vector3f[]> list, double a0, double a1,
                                           float rIn, float rMid, float rOut,
                                           float zIn, float zMid, float zOut) {
        float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
        float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

        Vector3f[] ring0 = {
            pt(c0 * rIn, CY + s0 * rIn, 0.0f),
            pt(c0 * rIn, CY + s0 * rIn, zIn),
            pt(c0 * rMid, CY + s0 * rMid, zMid),
            pt(c0 * rOut, CY + s0 * rOut, zOut),
            pt(c0 * rOut, CY + s0 * rOut, 0.0f),
            pt(c0 * rOut, CY + s0 * rOut, -zOut),
            pt(c0 * rMid, CY + s0 * rMid, -zMid),
            pt(c0 * rIn, CY + s0 * rIn, -zIn)
        };
        Vector3f[] ring1 = {
            pt(c1 * rIn, CY + s1 * rIn, 0.0f),
            pt(c1 * rIn, CY + s1 * rIn, zIn),
            pt(c1 * rMid, CY + s1 * rMid, zMid),
            pt(c1 * rOut, CY + s1 * rOut, zOut),
            pt(c1 * rOut, CY + s1 * rOut, 0.0f),
            pt(c1 * rOut, CY + s1 * rOut, -zOut),
            pt(c1 * rMid, CY + s1 * rMid, -zMid),
            pt(c1 * rIn, CY + s1 * rIn, -zIn)
        };

        for (int k = 0; k < 8; k++) {
            int nxt = (k + 1) % 8;
            list.add(new Vector3f[]{ring0[k], ring1[k], ring1[nxt], ring0[nxt]});
        }
    }

    private static void addSideRingCuff(List<Vector3f[]> list,
                                        double outA0, double outA1, double inA0, double inA1) {
        int steps = 8;
        float rIn = 3.92f, rMid = 4.68f, rOut = 5.42f;
        float zIn = 0.56f, zMid = 0.76f, zOut = 0.44f;
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps;
            double t1 = (i + 1) / (double) steps;
            double ia0 = inA0 + (inA1 - inA0) * t0;
            double ia1 = inA0 + (inA1 - inA0) * t1;
            double oa0 = outA0 + (outA1 - outA0) * t0;
            double oa1 = outA0 + (outA1 - outA0) * t1;
            double ma0 = (ia0 + oa0) * 0.5;
            double ma1 = (ia1 + oa1) * 0.5;

            Vector3f[] r0 = {
                pt(rIn * (float) Math.cos(ia0), CY + rIn * (float) Math.sin(ia0), 0.0f),
                pt(rIn * (float) Math.cos(ia0), CY + rIn * (float) Math.sin(ia0), zIn),
                pt(rMid * (float) Math.cos(ma0), CY + rMid * (float) Math.sin(ma0), zMid),
                pt(rOut * (float) Math.cos(oa0), CY + rOut * (float) Math.sin(oa0), zOut),
                pt(rOut * (float) Math.cos(oa0), CY + rOut * (float) Math.sin(oa0), 0.0f),
                pt(rOut * (float) Math.cos(oa0), CY + rOut * (float) Math.sin(oa0), -zOut),
                pt(rMid * (float) Math.cos(ma0), CY + rMid * (float) Math.sin(ma0), -zMid),
                pt(rIn * (float) Math.cos(ia0), CY + rIn * (float) Math.sin(ia0), -zIn)
            };
            Vector3f[] r1 = {
                pt(rIn * (float) Math.cos(ia1), CY + rIn * (float) Math.sin(ia1), 0.0f),
                pt(rIn * (float) Math.cos(ia1), CY + rIn * (float) Math.sin(ia1), zIn),
                pt(rMid * (float) Math.cos(ma1), CY + rMid * (float) Math.sin(ma1), zMid),
                pt(rOut * (float) Math.cos(oa1), CY + rOut * (float) Math.sin(oa1), zOut),
                pt(rOut * (float) Math.cos(oa1), CY + rOut * (float) Math.sin(oa1), 0.0f),
                pt(rOut * (float) Math.cos(oa1), CY + rOut * (float) Math.sin(oa1), -zOut),
                pt(rMid * (float) Math.cos(ma1), CY + rMid * (float) Math.sin(ma1), -zMid),
                pt(rIn * (float) Math.cos(ia1), CY + rIn * (float) Math.sin(ia1), -zIn)
            };
            for (int k = 0; k < 8; k++) {
                int nxt = (k + 1) % 8;
                list.add(new Vector3f[]{r0[k], r1[k], r1[nxt], r0[nxt]});
            }
        }
    }

    private static void addTopRingClamp(List<Vector3f[]> list, double ang, boolean leftLitHalf) {
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        float nx = -dy, ny = dx;
        float r0 = 3.42f, r1 = 5.42f;
        float w = 0.36f, zMid = 0.76f, zSide = 0.58f;

        Vector3f m0F = pt(dx * r0, CY + dy * r0, zMid);
        Vector3f m1F = pt(dx * r1, CY + dy * r1, zMid);
        Vector3f l0F = pt(dx * r0 + nx * w, CY + dy * r0 + ny * w, zSide);
        Vector3f l1F = pt(dx * r1 + nx * w, CY + dy * r1 + ny * w, zSide);
        Vector3f r0F = pt(dx * r0 - nx * w, CY + dy * r0 - ny * w, zSide);
        Vector3f r1F = pt(dx * r1 - nx * w, CY + dy * r1 - ny * w, zSide);

        Vector3f m0B = pt(dx * r0, CY + dy * r0, -zMid);
        Vector3f m1B = pt(dx * r1, CY + dy * r1, -zMid);
        Vector3f l0B = pt(dx * r0 + nx * w, CY + dy * r0 + ny * w, -zSide);
        Vector3f l1B = pt(dx * r1 + nx * w, CY + dy * r1 + ny * w, -zSide);
        Vector3f r0B = pt(dx * r0 - nx * w, CY + dy * r0 - ny * w, -zSide);
        Vector3f r1B = pt(dx * r1 - nx * w, CY + dy * r1 - ny * w, -zSide);

        if (leftLitHalf) {
            list.add(new Vector3f[]{m0F, l0F, l1F, m1F});
            list.add(new Vector3f[]{l0B, m0B, m1B, l1B});
            list.add(new Vector3f[]{l0F, l0B, l1B, l1F});
        } else {
            list.add(new Vector3f[]{r0F, m0F, m1F, r1F});
            list.add(new Vector3f[]{m0B, r0B, r1B, m1B});
            list.add(new Vector3f[]{r0B, r0F, r1F, r1B});
        }
    }

    private static void addCrownSpikeFaceted(List<Vector3f[]> list, double baseAng, double tiltAng,
                                             float totalLen, boolean leftLitHalf) {
        float bx = 5.15f * (float) Math.cos(baseAng);
        float by = CY + 5.15f * (float) Math.sin(baseAng);
        float dx = (float) Math.cos(tiltAng), dy = (float) Math.sin(tiltAng);
        float nx = -dy, ny = dx;

        // Distinct V-chevron socket collar (s = 1.40 .. 2.02) + stepped 4-faceted spike blade (s = 2.02 .. totalLen)
        float sBaseSide = 1.40f;
        float sBaseMid = 1.66f;
        float sCollarTopSide = 1.92f;
        float sCollarTopMid = 2.18f;

        Vector3f baseL = pt(bx + dx * sBaseSide + nx * 0.60f, by + dy * sBaseSide + ny * 0.60f, 0.0f);
        Vector3f baseR = pt(bx + dx * sBaseSide - nx * 0.60f, by + dy * sBaseSide - ny * 0.60f, 0.0f);
        Vector3f baseF = pt(bx + dx * sBaseMid, by + dy * sBaseMid, 0.58f);
        Vector3f baseB = pt(bx + dx * sBaseMid, by + dy * sBaseMid, -0.58f);

        // Outer rim of the chevron socket collar
        Vector3f colOutL = pt(bx + dx * sCollarTopSide + nx * 0.68f, by + dy * sCollarTopSide + ny * 0.68f, 0.0f);
        Vector3f colOutR = pt(bx + dx * sCollarTopSide - nx * 0.68f, by + dy * sCollarTopSide - ny * 0.68f, 0.0f);
        Vector3f colOutF = pt(bx + dx * sCollarTopMid, by + dy * sCollarTopMid, 0.64f);
        Vector3f colOutB = pt(bx + dx * sCollarTopMid, by + dy * sCollarTopMid, -0.64f);

        // Recessed base of the upper 4-faceted obelisk spike blade
        Vector3f bladeL = pt(bx + dx * sCollarTopSide + nx * 0.54f, by + dy * sCollarTopSide + ny * 0.54f, 0.0f);
        Vector3f bladeR = pt(bx + dx * sCollarTopSide - nx * 0.54f, by + dy * sCollarTopSide - ny * 0.54f, 0.0f);
        Vector3f bladeF = pt(bx + dx * sCollarTopMid, by + dy * sCollarTopMid, 0.50f);
        Vector3f bladeB = pt(bx + dx * sCollarTopMid, by + dy * sCollarTopMid, -0.50f);

        Vector3f tip = pt(bx + dx * totalLen, by + dy * totalLen, 0.0f);

        if (leftLitHalf) {
            list.add(new Vector3f[]{baseL, baseF, colOutF, colOutL});
            list.add(new Vector3f[]{colOutL, colOutF, bladeF, bladeL});
            list.add(new Vector3f[]{bladeL, bladeF, tip, tip});

            list.add(new Vector3f[]{baseB, baseL, colOutL, colOutB});
            list.add(new Vector3f[]{colOutB, colOutL, bladeL, bladeB});
            list.add(new Vector3f[]{bladeB, bladeL, tip, tip});
        } else {
            list.add(new Vector3f[]{baseF, baseR, colOutR, colOutF});
            list.add(new Vector3f[]{colOutF, colOutR, bladeR, bladeF});
            list.add(new Vector3f[]{bladeF, bladeR, tip, tip});

            list.add(new Vector3f[]{baseR, baseB, colOutB, colOutR});
            list.add(new Vector3f[]{colOutR, colOutB, bladeB, bladeR});
            list.add(new Vector3f[]{bladeR, bladeB, tip, tip});
        }
    }

    private static float cradleTopY(float absX) {
        if (absX <= 1.48f) {
            return ORB_Y - (float) Math.sqrt(Math.max(0.0f, 1.54f * 1.54f - absX * absX));
        }
        float u = (absX - 1.48f) / (3.48f - 1.48f);
        float yCupEdge = ORB_Y - (float) Math.sqrt(1.54f * 1.54f - 1.48f * 1.48f);
        return yCupEdge + u * (24.48f - yCupEdge);
    }

    private static float cradleMidY(float absX) {
        float u = absX / 3.55f;
        return 21.85f + 2.05f * (float) Math.pow(u, 0.92);
    }

    private static float cradleBotY(float absX) {
        if (absX <= 0.68f) {
            float t = absX / 0.68f;
            return 19.65f + 2.20f * (float) Math.pow(t, 0.82);
        }
        float u = (absX - 0.68f) / (3.58f - 0.68f);
        return 21.85f + 1.45f * (float) Math.pow(u, 0.85);
    }

    private static void addInnerYCradleGold(List<Vector3f[]> list, boolean brightPass) {
        int steps = 16;
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < steps; i++) {
                float x0 = 3.48f * i / steps;
                float x1 = 3.48f * (i + 1) / steps;
                float sx0 = side * x0, sx1 = side * x1;

                float yTop0 = cradleTopY(x0), yTop1 = cradleTopY(x1);
                float yMid0 = cradleMidY(x0), yMid1 = cradleMidY(x1);
                float yBot0 = cradleBotY(x0), yBot1 = cradleBotY(x1);

                float zEdge = 0.22f;
                float zRidge0 = 0.58f * (1.0f - 0.22f * (x0 / 3.48f));
                float zRidge1 = 0.58f * (1.0f - 0.22f * (x1 / 3.48f));

                Vector3f tF0 = pt(sx0, yTop0, zEdge), tF1 = pt(sx1, yTop1, zEdge);
                Vector3f tB0 = pt(sx0, yTop0, -zEdge), tB1 = pt(sx1, yTop1, -zEdge);
                Vector3f mF0 = pt(sx0, yMid0, zRidge0), mF1 = pt(sx1, yMid1, zRidge1);
                Vector3f mB0 = pt(sx0, yMid0, -zRidge0), mB1 = pt(sx1, yMid1, -zRidge1);
                Vector3f bF0 = pt(sx0, yBot0, 0.16f), bF1 = pt(sx1, yBot1, 0.16f);
                Vector3f bB0 = pt(sx0, yBot0, -0.16f), bB1 = pt(sx1, yBot1, -0.16f);

                if (brightPass) {
                    if (side > 0) {
                        list.add(new Vector3f[]{mF0, mF1, tF1, tF0});
                        list.add(new Vector3f[]{tB0, tB1, mB1, mB0});
                        list.add(new Vector3f[]{tF0, tF1, tB1, tB0});
                    } else {
                        list.add(new Vector3f[]{tF0, tF1, mF1, mF0});
                        list.add(new Vector3f[]{mB0, mB1, tB1, tB0});
                        list.add(new Vector3f[]{tB0, tB1, tF1, tF0});
                    }
                } else if (side < 0) {
                    list.add(new Vector3f[]{mF0, mF1, bF1, bF0});
                    list.add(new Vector3f[]{bB0, bB1, mB1, mB0});
                    list.add(new Vector3f[]{bF0, bF1, bB1, bB0});
                }
            }
        }
    }

    private static void addInnerYCradleShadow(List<Vector3f[]> list) {
        int steps = 16;
        int side = 1;
        for (int i = 0; i < steps; i++) {
            float x0 = 3.48f * i / steps;
            float x1 = 3.48f * (i + 1) / steps;
            float sx0 = side * x0, sx1 = side * x1;

            float yMid0 = cradleMidY(x0), yMid1 = cradleMidY(x1);
            float yBot0 = cradleBotY(x0), yBot1 = cradleBotY(x1);

            float zRidge0 = 0.58f * (1.0f - 0.22f * (x0 / 3.48f));
            float zRidge1 = 0.58f * (1.0f - 0.22f * (x1 / 3.48f));

            Vector3f mF0 = pt(sx0, yMid0, zRidge0), mF1 = pt(sx1, yMid1, zRidge1);
            Vector3f mB0 = pt(sx0, yMid0, -zRidge0), mB1 = pt(sx1, yMid1, -zRidge1);
            Vector3f bF0 = pt(sx0, yBot0, 0.16f), bF1 = pt(sx1, yBot1, 0.16f);
            Vector3f bB0 = pt(sx0, yBot0, -0.16f), bB1 = pt(sx1, yBot1, -0.16f);

            list.add(new Vector3f[]{bF0, bF1, mF1, mF0});
            list.add(new Vector3f[]{mB0, mB1, bB1, bB0});
            list.add(new Vector3f[]{bB0, bB1, bF1, bF0});
        }
    }

    private static void addSteppedWingGusset(List<Vector3f[]> list, int side) {
        float[][] wingOutline = {
            {side * 0.58f, 15.65f},
            {side * 1.65f, 16.65f},
            {side * 1.48f, 16.92f},
            {side * 2.52f, 17.78f},
            {side * 2.52f, 18.25f},
            {side * 0.58f, 18.25f}
        };
        float z = 0.32f;
        Vector3f cF = pt(side * 1.15f, 17.25f, z);
        Vector3f cB = pt(side * 1.15f, 17.25f, -z);
        for (int i = 0; i < wingOutline.length; i++) {
            int nxt = (i + 1) % wingOutline.length;
            Vector3f p0F = pt(wingOutline[i][0], wingOutline[i][1], z);
            Vector3f p1F = pt(wingOutline[nxt][0], wingOutline[nxt][1], z);
            Vector3f p0B = pt(wingOutline[i][0], wingOutline[i][1], -z);
            Vector3f p1B = pt(wingOutline[nxt][0], wingOutline[nxt][1], -z);
            if (side > 0) {
                list.add(new Vector3f[]{cF, p0F, p1F, p1F});
                list.add(new Vector3f[]{cB, p1B, p0B, p0B});
                list.add(new Vector3f[]{p0F, p0B, p1B, p1F});
            } else {
                list.add(new Vector3f[]{cF, p1F, p0F, p0F});
                list.add(new Vector3f[]{cB, p0B, p1B, p1B});
                list.add(new Vector3f[]{p0B, p0F, p1F, p1B});
            }
        }
    }

    private static void addCrossbarAndScrolls(List<Vector3f[]> list) {
        for (int side : new int[]{-1, 1}) {
            float[][] arm = {
                // x, yCenter, halfHeight, halfZ
                {side * 0.55f, 18.22f, 0.30f, 0.44f},
                {side * 1.85f, 18.28f, 0.30f, 0.42f},
                {side * 3.35f, 18.38f, 0.28f, 0.40f},
                {side * 4.65f, 18.42f, 0.24f, 0.36f},
                {side * 5.25f, 18.45f, 0.18f, 0.28f},
                {side * 5.62f, 18.54f, 0.04f, 0.08f}
            };
            for (int i = 0; i < arm.length - 1; i++) {
                float[] a = arm[i], b = arm[i + 1];
                if (side > 0) {
                    addHorizontalBarSegment(list, a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3]);
                } else {
                    addHorizontalBarSegment(list, b[0], b[1], b[2], b[3], a[0], a[1], a[2], a[3]);
                }
            }

            // Seamless, continuous mitered 3D spiral volute (@-scroll) curling inward under the outer barb
            float cx = side * 4.85f;
            float cy = 17.75f;
            int spiralSteps = 22;
            Vector3f[][] rings = new Vector3f[spiralSteps + 1][4];
            for (int i = 0; i <= spiralSteps; i++) {
                double t = i / (double) spiralSteps;
                double ang = Math.toRadians(90.0 - side * t * 360.0);
                float rad = 0.66f * (1.0f - 0.72f * (float) t);
                float x = cx + rad * (float) Math.cos(ang);
                float y = cy + rad * (float) Math.sin(ang);
                float nx = (float) Math.cos(ang);
                float ny = (float) Math.sin(ang);
                float hw = 0.24f * (1.0f - 0.35f * (float) t);
                float hz = 0.40f * (1.0f - 0.20f * (float) t);
                rings[i][0] = pt(x + nx * hw, y + ny * hw, 0.0f);
                rings[i][1] = pt(x, y, hz);
                rings[i][2] = pt(x - nx * hw, y - ny * hw, 0.0f);
                rings[i][3] = pt(x, y, -hz);
            }
            for (int i = 0; i < spiralSteps; i++) {
                for (int k = 0; k < 4; k++) {
                    int nxt = (k + 1) % 4;
                    if (side > 0) {
                        list.add(new Vector3f[]{rings[i][k], rings[i + 1][k], rings[i + 1][nxt], rings[i][nxt]});
                    } else {
                        list.add(new Vector3f[]{rings[i][nxt], rings[i + 1][nxt], rings[i + 1][k], rings[i][k]});
                    }
                }
            }
            addSphereAt(list, cx, cy, 0.0f, 0.24f, 6, 8);
        }
    }

    private static void addTeardropGem(List<Vector3f[]> list, float cx, float yBot, float yTop,
                                       float maxRx, float zBase, float zPeak, boolean positiveZ) {
        int segs = 14;
        float yCenter = yBot + (yTop - yBot) * 0.36f;
        Vector3f peak = pt(cx, yCenter, zPeak);
        for (int i = 0; i < segs; i++) {
            double a0 = 2.0 * Math.PI * i / segs;
            double a1 = 2.0 * Math.PI * (i + 1) / segs;
            Vector3f p0 = teardropRimPoint(cx, yBot, yTop, maxRx, zBase, a0);
            Vector3f p1 = teardropRimPoint(cx, yBot, yTop, maxRx, zBase, a1);
            if (positiveZ) {
                list.add(new Vector3f[]{peak, p0, p1, p1});
            } else {
                list.add(new Vector3f[]{peak, p1, p0, p0});
            }
        }
    }

    private static Vector3f teardropRimPoint(float cx, float yBot, float yTop, float maxRx, float zBase, double a) {
        float s = (float) Math.sin(a);
        float c = (float) Math.cos(a);
        float u = 0.5f * (s + 1.0f);
        float y = yBot + u * (yTop - yBot);
        float widthFactor = (float) (Math.pow(1.0f - u, 0.72) * Math.sqrt(Math.max(0.0f, 1.0f - s * s)) * 1.35);
        float x = cx + maxRx * (c >= 0 ? widthFactor : -widthFactor);
        return pt(x, y, zBase);
    }

    private static void addSphereAt(List<Vector3f[]> list, float cx, float cy, float cz,
                                    float r, int latDiv, int lonDiv) {
        for (int lat = 0; lat < latDiv; lat++) {
            double theta1 = lat * Math.PI / latDiv;
            double theta2 = (lat + 1) * Math.PI / latDiv;
            float y1 = cy + r * (float) Math.cos(theta1);
            float y2 = cy + r * (float) Math.cos(theta2);
            float r1 = r * (float) Math.sin(theta1);
            float r2 = r * (float) Math.sin(theta2);
            for (int lon = 0; lon < lonDiv; lon++) {
                double phi1 = lon * 2.0 * Math.PI / lonDiv;
                double phi2 = (lon + 1) * 2.0 * Math.PI / lonDiv;
                Vector3f p0 = pt(cx + r1 * (float) Math.cos(phi1), y1, cz + r1 * (float) Math.sin(phi1));
                Vector3f p1 = pt(cx + r2 * (float) Math.cos(phi1), y2, cz + r2 * (float) Math.sin(phi1));
                Vector3f p2 = pt(cx + r2 * (float) Math.cos(phi2), y2, cz + r2 * (float) Math.sin(phi2));
                Vector3f p3 = pt(cx + r1 * (float) Math.cos(phi2), y1, cz + r1 * (float) Math.sin(phi2));
                list.add(new Vector3f[]{p0, p1, p2, p3});
            }
        }
    }

    private static void addHorizontalBarSegment(List<Vector3f[]> list,
                                                float x0, float y0, float hh0, float hz0,
                                                float x1, float y1, float hh1, float hz1) {
        Vector3f[] r0 = {
            pt(x0, y0 + hh0, 0.0f),
            pt(x0, y0 + hh0 * 0.5f, hz0),
            pt(x0, y0 - hh0 * 0.5f, hz0),
            pt(x0, y0 - hh0, 0.0f),
            pt(x0, y0 - hh0 * 0.5f, -hz0),
            pt(x0, y0 + hh0 * 0.5f, -hz0)
        };
        Vector3f[] r1 = {
            pt(x1, y1 + hh1, 0.0f),
            pt(x1, y1 + hh1 * 0.5f, hz1),
            pt(x1, y1 - hh1 * 0.5f, hz1),
            pt(x1, y1 - hh1, 0.0f),
            pt(x1, y1 - hh1 * 0.5f, -hz1),
            pt(x1, y1 + hh1 * 0.5f, -hz1)
        };
        for (int k = 0; k < 6; k++) {
            int nxt = (k + 1) % 6;
            list.add(new Vector3f[]{r0[k], r1[k], r1[nxt], r0[nxt]});
        }
    }

    private static void addWingFin(List<Vector3f[]> list, float[][] profile, boolean positiveX) {
        for (int i = 0; i < profile.length - 1; i++) {
            float y0 = profile[i][0], in0 = profile[i][1], out0 = profile[i][2], z0 = profile[i][3];
            float y1 = profile[i + 1][0], in1 = profile[i + 1][1], out1 = profile[i + 1][2], z1 = profile[i + 1][3];
            float mid0 = (in0 + out0) * 0.5f;
            float mid1 = (in1 + out1) * 0.5f;

            Vector3f[] r0 = {
                pt(in0, y0, 0.0f),
                pt(mid0, y0, z0),
                pt(out0, y0, 0.0f),
                pt(mid0, y0, -z0)
            };
            Vector3f[] r1 = {
                pt(in1, y1, 0.0f),
                pt(mid1, y1, z1),
                pt(out1, y1, 0.0f),
                pt(mid1, y1, -z1)
            };
            for (int k = 0; k < 4; k++) {
                int nxt = (k + 1) % 4;
                if (positiveX) {
                    list.add(new Vector3f[]{r0[k], r1[k], r1[nxt], r0[nxt]});
                } else {
                    list.add(new Vector3f[]{r0[nxt], r1[nxt], r1[k], r0[k]});
                }
            }
        }
        float[] last = profile[profile.length - 1];
        float y = last[0], in = last[1], out = last[2], z = last[3];
        float mid = (in + out) * 0.5f;
        Vector3f pIn = pt(in, y + 0.25f, 0.0f);
        Vector3f pF = pt(mid, y + 0.12f, z);
        Vector3f pOut = pt(out, y - 0.15f, 0.0f);
        Vector3f pB = pt(mid, y + 0.12f, -z);
        if (positiveX) {
            list.add(new Vector3f[]{pIn, pB, pOut, pF});
        } else {
            list.add(new Vector3f[]{pIn, pF, pOut, pB});
        }
    }

    private static void addFinPommel(List<Vector3f[]> list) {
        float[][] core = {
            {-37.8f, 0.68f, 0.68f},
            {-39.2f, 0.96f, 0.78f},
            {-40.4f, 0.52f, 0.44f},
            {-40.1f, 0.0f, 0.0f}
        };
        addLoftOctagon(list, core, 0.0f, 0.0f);

        for (int xSide : new int[]{-1, 1}) {
            float[][] pommelWing = {
                {-37.9f, xSide * 0.42f, xSide * 0.72f, 0.24f},
                {-39.5f, xSide * 0.50f, xSide * 1.28f, 0.28f},
                {-40.8f, xSide * 0.58f, xSide * 1.62f, 0.22f},
                {-41.45f, xSide * 1.15f, xSide * 1.74f, 0.10f}
            };
            addWingFin(list, pommelWing, xSide > 0);
        }
    }

    private static void addHorizontalCollar(List<Vector3f[]> list, float yMid, float height, float rMax) {
        float halfH = height * 0.5f;
        float rEdge = rMax * 0.78f;
        float[][] collar = {
            {yMid - halfH, rEdge, rEdge},
            {yMid, rMax, rMax},
            {yMid + halfH, rEdge, rEdge}
        };
        addLoftOctagon(list, collar, 0.0f, 0.0f);
    }

    private static void addLoftOctagon(List<Vector3f[]> list, float[][] levels, float cx, float cz) {
        int sides = 8;
        int n = levels.length;
        Vector3f[][] rings = new Vector3f[n][sides];
        for (int i = 0; i < n; i++) {
            float y = levels[i][0], rx = levels[i][1], rz = levels[i][2];
            for (int j = 0; j < sides; j++) {
                double ang = (j + 0.5) * Math.PI / 4.0;
                rings[i][j] = pt(cx + rx * (float) Math.cos(ang), y, cz + rz * (float) Math.sin(ang));
            }
        }
        for (int i = 0; i < n - 1; i++) {
            boolean ascending = levels[i + 1][0] >= levels[i][0];
            for (int j = 0; j < sides; j++) {
                int nxt = (j + 1) % sides;
                if (ascending) {
                    list.add(new Vector3f[]{rings[i][j], rings[i + 1][j], rings[i + 1][nxt], rings[i][nxt]});
                } else {
                    list.add(new Vector3f[]{rings[i][nxt], rings[i + 1][nxt], rings[i + 1][j], rings[i][j]});
                }
            }
        }
    }

    private static Vector3f[][] createSphereQuads(float r, int latDiv, int lonDiv) {
        List<Vector3f[]> list = new ArrayList<>();
        addSphereAt(list, 0.0f, 0.0f, 0.0f, r, latDiv, lonDiv);
        return list.toArray(new Vector3f[0][]);
    }
}
