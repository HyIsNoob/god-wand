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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GodWandModel extends Model {
    private final ModelPart root, obsidian, gold, leather, wrapping, reactorCapsule, pommelGem;
    private final ModelPart singularityCore, orbit, satellite;
    private float charge, animationTime;

    private static final float CX = -0.55f;
    private static final float CY = 21.2f;
    private static final float C_RADIUS = 5.9f;
    private static final int NUM_SEGMENTS = 9;

    private static final Vector3f[][] SHAFT_QUADS = createShaftQuads();
    private static final Vector3f[][] GOLD_COLLARS = createGoldCollars();
    private static final Vector3f[][] LEATHER_QUADS = createLeatherQuads();
    private static final Vector3f[][] WRAPPING_QUADS = createWrappingQuads();
    private static final Vector3f[][] REACTOR_QUADS = createReactorQuads();

    private static final Vector3f[][] CRESCENT_QUADS = createCrescentQuads();
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

        root.addOrReplaceChild("obsidian", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("gold", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("leather", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("wrapping", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("reactor_capsule", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("pommel_gem", CubeListBuilder.create(), PartPose.ZERO);

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

        singularityCore.yRot = time * speed;
        singularityCore.xRot = (float)Math.sin(time * 0.035f) * 0.06f;
        singularityCore.zRot = (float)Math.cos(time * 0.025f) * 0.05f;
        singularityCore.y = CY;

        float size = activeSkillId == 3 ? 1.0f - 0.45f * charge * charge : 1.0f + charge * 0.14f;
        singularityCore.xScale = singularityCore.yScale = singularityCore.zScale = size;

        orbit.xRot = (float)Math.sin(time * 0.035f) * 0.15f;
        orbit.yRot = time * (speed * 0.9f);
        orbit.zRot = (float)Math.cos(time * 0.025f) * 0.15f;

        satellite.yRot = time * (0.05f + charge * 0.14f);
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer, Supplier<VertexConsumer> coreBuffer,
                           int light, int overlay, int coreColor) {
        VertexConsumer solid = solidBuffer.get();

        // 1. Solid octagonal shaft, beveled collars, burgundy grip & wraps
        renderQuads(matrices, solid, SHAFT_QUADS, light, overlay, 0xFF36313F);
        renderQuads(matrices, solid, GOLD_COLLARS, light, overlay, 0xFFB88A43);
        renderQuads(matrices, solid, LEATHER_QUADS, light, overlay, 0xFF632335);
        renderQuads(matrices, solid, WRAPPING_QUADS, light, overlay, 0xFF832E43);

        // 2. Beveled crescent head, 3-faceted gold rail, joint clamps, and talons
        matrices.pushPose();
        matrices.translate(CX / 16.0f, CY / 16.0f, 0.0f);
        matrices.mulPose(Axis.ZP.rotationDegrees(16.0f));
        matrices.translate(-CX / 16.0f, -CY / 16.0f, 0.0f);

        renderCrescent(matrices, solid, light, overlay);
        renderQuads(matrices, solid, GOLD_RAIL, light, overlay, 0xFFB88A43);
        renderQuads(matrices, solid, GOLD_BRACKETS, light, overlay, 0xFFB88A43);
        renderQuads(matrices, solid, TOP_TALON, light, overlay, 0xFFB88A43);
        matrices.popPose();

        // 3. Spearhead pommel blade
        renderPommelBlade(matrices, solid, light, overlay);

        // 4. Synchronized floating core assembly (Diamond bezel with 4 cardinal prongs + Faceted ruby)
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        float bob = (float)Math.sin(animationTime * 0.04f) * 0.015f;
        matrices.translate(0, bob, 0);

        renderDiamondBezel(matrices, solid, light, overlay);
        renderFacetedGem(matrices, solid, light, overlay, 1.45f, 2.25f, 1.45f, coreColor);
        matrices.popPose();

        // 5. Emissive luminous pass
        VertexConsumer emissive = coreBuffer.get();
        renderQuads(matrices, emissive, REACTOR_QUADS, LightTexture.FULL_BRIGHT, overlay, 0xFFFF2548);

        // Red vents (rotated with crescent head)
        matrices.pushPose();
        matrices.translate(CX / 16.0f, CY / 16.0f, 0.0f);
        matrices.mulPose(Axis.ZP.rotationDegrees(16.0f));
        matrices.translate(-CX / 16.0f, -CY / 16.0f, 0.0f);
        renderQuads(matrices, emissive, RED_VENTS, LightTexture.FULL_BRIGHT, overlay, 0xFFFF2548);
        matrices.popPose();

        // Glowing core heart & bloom layers
        matrices.pushPose();
        singularityCore.translateAndRotate(matrices);
        matrices.translate(0, bob, 0);
        renderFacetedGem(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 0.85f, 1.35f, 0.85f, 0xFFFFFFFF);

        int auraColor = 0xFF000000 | (((((coreColor >> 16) & 255) + 255) / 2) << 16)
                | (((((coreColor >> 8) & 255) + 255) / 2) << 8) | (((coreColor & 255) + 255) / 2);
        renderFacetedGem(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 1.60f, 2.45f, 1.60f,
                (auraColor & 0x00FFFFFF) | ((40 + (int)(charge * 36)) << 24));
        renderFacetedGem(matrices, emissive, LightTexture.FULL_BRIGHT, overlay, 1.85f, 2.80f, 1.85f,
                (auraColor & 0x00FFFFFF) | ((12 + (int)(charge * 14)) << 24));
        matrices.popPose();

        // Orbiting crystalline ember flecks
        matrices.pushPose();
        orbit.translateAndRotate(matrices);
        satellite.translateAndRotate(matrices);
        renderEmberMotes(matrices, emissive, overlay, 0xFFFF6E8A);
        matrices.popPose();
    }

    private static void renderQuads(PoseStack matrices, VertexConsumer vertices, Vector3f[][] quads,
                                    int light, int overlay, int color) {
        for (Vector3f[] q : quads) {
            quad(matrices, vertices, q[0], q[1], q[2], q[3], light, overlay, color);
        }
    }

    private static void addOctagonalPrism(List<Vector3f[]> list, float rBot, float rTop, float yBot, float yTop,
                                          float cx, float cz) {
        Vector3f[] bPts = new Vector3f[8];
        Vector3f[] tPts = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            double ang = (i + 0.5) * Math.PI / 4.0;
            float c = (float)Math.cos(ang), s = (float)Math.sin(ang);
            bPts[i] = new Vector3f(cx + rBot * c, yBot, cz + rBot * s);
            tPts[i] = new Vector3f(cx + rTop * c, yTop, cz + rTop * s);
        }
        for (int i = 0; i < 8; i++) {
            int nxt = (i + 1) % 8;
            list.add(new Vector3f[]{bPts[i], tPts[i], tPts[nxt], bPts[nxt]});
        }
        Vector3f cb = new Vector3f(cx, yBot, cz);
        Vector3f ct = new Vector3f(cx, yTop, cz);
        for (int i = 0; i < 8; i++) {
            int nxt = (i + 1) % 8;
            list.add(new Vector3f[]{cb, bPts[nxt], bPts[i], bPts[i]});
            list.add(new Vector3f[]{ct, tPts[i], tPts[nxt], tPts[nxt]});
        }
    }

    private static void addBeveledRing(List<Vector3f[]> list, float rIn, float rOut, float yMid, float height,
                                       float cx, float cz) {
        float chamfer = Math.min(0.18f, height * 0.3f);
        float y0 = yMid - height * 0.5f;
        float y1 = y0 + chamfer;
        float y2 = yMid + height * 0.5f - chamfer;
        float y3 = yMid + height * 0.5f;

        float[] yLevelsB = {y0, y1, y2};
        float[] yLevelsT = {y1, y2, y3};
        float[] rLevelsB = {rOut - chamfer, rOut, rOut};
        float[] rLevelsT = {rOut, rOut, rOut - chamfer};

        for (int layer = 0; layer < 3; layer++) {
            float yb = yLevelsB[layer], yt = yLevelsT[layer];
            float rb = rLevelsB[layer], rt = rLevelsT[layer];
            for (int i = 0; i < 8; i++) {
                double ang0 = (i + 0.5) * Math.PI / 4.0;
                double ang1 = (i + 1.5) * Math.PI / 4.0;
                float c0 = (float)Math.cos(ang0), s0 = (float)Math.sin(ang0);
                float c1 = (float)Math.cos(ang1), s1 = (float)Math.sin(ang1);
                list.add(new Vector3f[]{
                    new Vector3f(cx + rb * c0, yb, cz + rb * s0),
                    new Vector3f(cx + rt * c0, yt, cz + rt * s0),
                    new Vector3f(cx + rt * c1, yt, cz + rt * s1),
                    new Vector3f(cx + rb * c1, yb, cz + rb * s1)
                });
            }
        }
    }

    private static Vector3f[][] createShaftQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        addOctagonalPrism(list, 0.85f, 0.85f, -36.0f, -6.0f, 0.0f, 0.0f);
        addOctagonalPrism(list, 0.80f, 0.80f, 6.0f, 14.8f, 0.0f, 0.0f);
        addOctagonalPrism(list, 1.15f, 1.00f, 11.6f, 12.6f, 0.0f, 0.0f);
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createGoldCollars() {
        List<Vector3f[]> list = new ArrayList<>();
        // Shaft collar rings
        addBeveledRing(list, 0.85f, 1.25f, -35.5f, 0.75f, 0.0f, 0.0f);
        addBeveledRing(list, 0.85f, 1.10f, -24.0f, 0.55f, 0.0f, 0.0f);
        addBeveledRing(list, 0.85f, 1.10f, -15.0f, 0.55f, 0.0f, 0.0f);
        addBeveledRing(list, 0.85f, 1.35f, -6.0f, 0.75f, 0.0f, 0.0f);
        addBeveledRing(list, 0.85f, 1.35f, 5.8f, 0.75f, 0.0f, 0.0f);

        // Reactor cage rings and struts
        addBeveledRing(list, 0.80f, 1.55f, 6.8f, 0.85f, 0.0f, 0.0f);
        addBeveledRing(list, 0.80f, 1.55f, 10.4f, 0.85f, 0.0f, 0.0f);
        for (int i = 0; i < 4; i++) {
            double ang = i * Math.PI / 2.0 + Math.PI / 4.0;
            float sx = 1.35f * (float)Math.cos(ang);
            float sz = 1.35f * (float)Math.sin(ang);
            addOctagonalPrism(list, 0.22f, 0.22f, 7.2f, 10.0f, sx, sz);
        }

        // Neck stepped collars (terminating cleanly without vertical spine)
        addBeveledRing(list, 0.80f, 1.45f, 11.2f, 0.85f, 0.0f, 0.0f);
        addBeveledRing(list, 0.95f, 1.65f, 13.2f, 1.35f, 0.0f, 0.0f);
        addBeveledRing(list, 1.05f, 1.85f, 14.6f, 1.55f, 0.0f, 0.0f);

        // Pommel transition collar
        addBeveledRing(list, 0.85f, 1.35f, -37.2f, 0.85f, 0.0f, 0.0f);

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createLeatherQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        addOctagonalPrism(list, 1.10f, 1.10f, -5.7f, 5.7f, 0.0f, 0.0f);
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createWrappingQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        for (int band = 0; band < 11; band++) {
            float y = -5.2f + band * 1.04f;
            addBeveledRing(list, 1.10f, 1.20f, y, 0.22f, 0.0f, 0.0f);
            for (double ang : new double[]{0, Math.PI / 2.0, Math.PI, 3 * Math.PI / 2.0}) {
                float rx = 1.22f * (float)Math.cos(ang);
                float rz = 1.22f * (float)Math.sin(ang);
                list.add(new Vector3f[]{
                    new Vector3f(rx - 0.12f, y - 0.10f, rz - 0.12f),
                    new Vector3f(rx - 0.12f, y + 0.10f, rz - 0.12f),
                    new Vector3f(rx + 0.12f, y + 0.10f, rz + 0.12f),
                    new Vector3f(rx + 0.12f, y - 0.10f, rz + 0.12f)
                });
            }
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createReactorQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        addOctagonalPrism(list, 0.75f, 0.75f, 7.2f, 10.0f, 0.0f, 0.0f);
        Vector3f top = new Vector3f(0.0f, 9.8f, 0.0f);
        Vector3f bot = new Vector3f(0.0f, 7.4f, 0.0f);
        for (int i = 0; i < 6; i++) {
            double a0 = i * Math.PI / 3.0, a1 = (i + 1) * Math.PI / 3.0;
            Vector3f p0 = new Vector3f(0.48f * (float)Math.cos(a0), 8.6f, 0.48f * (float)Math.sin(a0));
            Vector3f p1 = new Vector3f(0.48f * (float)Math.cos(a1), 8.6f, 0.48f * (float)Math.sin(a1));
            list.add(new Vector3f[]{top, p0, p1, p1});
            list.add(new Vector3f[]{bot, p1, p0, p0});
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createCrescentQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        float cIn = 0.24f, cOut = 0.35f, cZ = 0.26f;

        for (int s = 0; s < NUM_SEGMENTS; s++) {
            double t0 = s / (double)NUM_SEGMENTS, t1 = (s + 1) / (double)NUM_SEGMENTS;
            double seamGap = 0.018;
            double aStart = Math.toRadians(40 + 268 * t0) + seamGap;
            double aEnd   = Math.toRadians(40 + 268 * t1) - seamGap;
            double tMid   = (t0 + t1) / 2.0;
            float env     = (float)Math.pow(Math.sin(tMid * Math.PI), 0.45);
            float wRad    = 1.25f + 1.35f * env;
            float tZ      = 0.70f + 0.52f * env;

            float rIn = C_RADIUS - 0.10f;
            float rOut = C_RADIUS + wRad;

            Vector3f[][] rings = new Vector3f[3][9];
            for (int step = 0; step < 3; step++) {
                double a = aStart + (aEnd - aStart) * (step / 2.0);
                float ca = (float)Math.cos(a), sa = (float)Math.sin(a);
                rings[step][0] = new Vector3f(CX + ca * rIn, CY + sa * rIn, -tZ + cZ);
                rings[step][1] = new Vector3f(CX + ca * (rIn + cIn), CY + sa * (rIn + cIn), -tZ);
                rings[step][2] = new Vector3f(CX + ca * (rOut - cOut), CY + sa * (rOut - cOut), -tZ);
                rings[step][3] = new Vector3f(CX + ca * rOut, CY + sa * rOut, -tZ + cZ);
                rings[step][4] = new Vector3f(CX + ca * (rOut + 0.22f), CY + sa * (rOut + 0.22f), 0.0f);
                rings[step][5] = new Vector3f(CX + ca * rOut, CY + sa * rOut, tZ - cZ);
                rings[step][6] = new Vector3f(CX + ca * (rOut - cOut), CY + sa * (rOut - cOut), tZ);
                rings[step][7] = new Vector3f(CX + ca * (rIn + cIn), CY + sa * (rIn + cIn), tZ);
                rings[step][8] = new Vector3f(CX + ca * rIn, CY + sa * rIn, tZ - cZ);
            }

            for (int sub = 0; sub < 2; sub++) {
                Vector3f[] r0 = rings[sub];
                Vector3f[] r1 = rings[sub + 1];
                for (int v = 0; v < 8; v++) {
                    int nxtV = v + 1;
                    list.add(new Vector3f[]{r0[v], r1[v], r1[nxtV], r0[nxtV]});
                }
            }

            Vector3f center0 = new Vector3f(CX + (float)Math.cos(aStart) * (C_RADIUS + wRad * 0.5f),
                                            CY + (float)Math.sin(aStart) * (C_RADIUS + wRad * 0.5f), 0.0f);
            for (int v = 0; v < 8; v++) {
                list.add(new Vector3f[]{center0, rings[0][v + 1], rings[0][v], rings[0][v]});
            }

            Vector3f center1 = new Vector3f(CX + (float)Math.cos(aEnd) * (C_RADIUS + wRad * 0.5f),
                                            CY + (float)Math.sin(aEnd) * (C_RADIUS + wRad * 0.5f), 0.0f);
            for (int v = 0; v < 8; v++) {
                list.add(new Vector3f[]{center1, rings[2][v], rings[2][v + 1], rings[2][v + 1]});
            }
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static void renderCrescent(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        renderQuads(matrices, vertices, CRESCENT_QUADS, light, overlay, 0xFF36313F);
    }

    private static Vector3f[][] createGoldRail() {
        List<Vector3f[]> list = new ArrayList<>();
        int steps = 38;
        for (int i = 0; i < steps - 1; i++) {
            double t0 = i / (double)(steps - 1);
            double t1 = (i + 1) / (double)(steps - 1);
            double a0 = Math.toRadians(40 + 268 * t0);
            double a1 = Math.toRadians(40 + 268 * t1);
            float rInner = C_RADIUS - 0.45f;
            float rRailMid = C_RADIUS - 0.08f;
            float rRailOut = C_RADIUS + 0.28f;

            float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);

            Vector3f p0InF = new Vector3f(CX + c0 * rInner, CY + s0 * rInner, -0.42f);
            Vector3f p1InF = new Vector3f(CX + c1 * rInner, CY + s1 * rInner, -0.42f);
            Vector3f p0MidF = new Vector3f(CX + c0 * rRailMid, CY + s0 * rRailMid, -0.55f);
            Vector3f p1MidF = new Vector3f(CX + c1 * rRailMid, CY + s1 * rRailMid, -0.55f);
            Vector3f p0OutF = new Vector3f(CX + c0 * rRailOut, CY + s0 * rRailOut, -0.44f);
            Vector3f p1OutF = new Vector3f(CX + c1 * rRailOut, CY + s1 * rRailOut, -0.44f);

            Vector3f p0InB = new Vector3f(CX + c0 * rInner, CY + s0 * rInner, 0.42f);
            Vector3f p1InB = new Vector3f(CX + c1 * rInner, CY + s1 * rInner, 0.42f);
            Vector3f p0MidB = new Vector3f(CX + c0 * rRailMid, CY + s0 * rRailMid, 0.55f);
            Vector3f p1MidB = new Vector3f(CX + c1 * rRailMid, CY + s1 * rRailMid, 0.55f);
            Vector3f p0OutB = new Vector3f(CX + c0 * rRailOut, CY + s0 * rRailOut, 0.44f);
            Vector3f p1OutB = new Vector3f(CX + c1 * rRailOut, CY + s1 * rRailOut, 0.44f);

            list.add(new Vector3f[]{p0InB, p1InB, p1InF, p0InF});
            list.add(new Vector3f[]{p0InF, p1InF, p1MidF, p0MidF});
            list.add(new Vector3f[]{p0MidF, p1MidF, p1OutF, p0OutF});
            list.add(new Vector3f[]{p1InB, p0InB, p0MidB, p1MidB});
            list.add(new Vector3f[]{p1MidB, p0MidB, p0OutB, p1OutB});
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createRedVents() {
        List<Vector3f[]> list = new ArrayList<>();
        for (int s = 0; s < NUM_SEGMENTS; s++) {
            double t0 = s / (double)NUM_SEGMENTS, t1 = (s + 1) / (double)NUM_SEGMENTS;
            double aStart = Math.toRadians(40 + 268 * t0);
            double aEnd   = Math.toRadians(40 + 268 * t1);
            double aMid   = (aStart + aEnd) * 0.5;
            double tMid   = (t0 + t1) * 0.5;
            float env     = (float)Math.pow(Math.sin(tMid * Math.PI), 0.45);
            float wRad    = 1.25f + 1.35f * env;
            float tZ      = 0.70f + 0.52f * env;

            float rVent = C_RADIUS + wRad * 0.76f;
            float caM = (float)Math.cos(aMid), saM = (float)Math.sin(aMid);

            Vector3f vf0 = new Vector3f(CX + caM * rVent - 0.22f * saM, CY + saM * rVent + 0.22f * caM, -tZ - 0.02f);
            Vector3f vf1 = new Vector3f(CX + caM * (rVent + 0.48f), CY + saM * (rVent + 0.48f), -tZ - 0.02f);
            Vector3f vf2 = new Vector3f(CX + caM * rVent + 0.22f * saM, CY + saM * rVent - 0.22f * caM, -tZ - 0.02f);
            Vector3f vf3 = new Vector3f(CX + caM * (rVent - 0.26f), CY + saM * (rVent - 0.26f), -tZ - 0.02f);
            list.add(new Vector3f[]{vf0, vf1, vf2, vf3});

            Vector3f vb0 = new Vector3f(CX + caM * rVent + 0.22f * saM, CY + saM * rVent - 0.22f * caM, tZ + 0.02f);
            Vector3f vb1 = new Vector3f(CX + caM * (rVent + 0.48f), CY + saM * (rVent + 0.48f), tZ + 0.02f);
            Vector3f vb2 = new Vector3f(CX + caM * rVent - 0.22f * saM, CY + saM * rVent + 0.22f * caM, tZ + 0.02f);
            Vector3f vb3 = new Vector3f(CX + caM * (rVent - 0.26f), CY + saM * (rVent - 0.26f), tZ + 0.02f);
            list.add(new Vector3f[]{vb0, vb1, vb2, vb3});
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createGoldBrackets() {
        List<Vector3f[]> list = new ArrayList<>();
        for (int s = 1; s < NUM_SEGMENTS; s++) {
            double t = s / (double)NUM_SEGMENTS;
            double a = Math.toRadians(40 + 268 * t);
            float env = (float)Math.pow(Math.sin(t * Math.PI), 0.45);
            float wRad = 1.25f + 1.35f * env;
            float tZ = 0.70f + 0.52f * env;

            double dAng = 0.024;
            double a0 = a - dAng, a1 = a + dAng;
            float rIn = C_RADIUS - 0.35f;
            float rOut = C_RADIUS + wRad * 0.74f;

            float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);

            Vector3f b0F = new Vector3f(CX + c0 * rIn, CY + s0 * rIn, -tZ * 0.76f);
            Vector3f b1F = new Vector3f(CX + c1 * rIn, CY + s1 * rIn, -tZ * 0.76f);
            Vector3f b2F = new Vector3f(CX + c1 * rOut, CY + s1 * rOut, -tZ * 0.34f);
            Vector3f b3F = new Vector3f(CX + c0 * rOut, CY + s0 * rOut, -tZ * 0.34f);

            Vector3f b0B = new Vector3f(CX + c1 * rIn, CY + s1 * rIn, tZ * 0.76f);
            Vector3f b1B = new Vector3f(CX + c0 * rIn, CY + s0 * rIn, tZ * 0.76f);
            Vector3f b2B = new Vector3f(CX + c0 * rOut, CY + s0 * rOut, tZ * 0.34f);
            Vector3f b3B = new Vector3f(CX + c1 * rOut, CY + s1 * rOut, tZ * 0.34f);

            list.add(new Vector3f[]{b0F, b1F, b2F, b3F});
            list.add(new Vector3f[]{b0B, b1B, b2B, b3B});
            list.add(new Vector3f[]{b0F, b1B, b0B, b1F});
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createTopTalon() {
        List<Vector3f[]> list = new ArrayList<>();
        // Top curved talon / hook at the upper tip of C (a = 40 deg, curves inwards along arc to 21 deg)
        double aTop = Math.toRadians(40);
        float ca = (float)Math.cos(aTop), sa = (float)Math.sin(aTop);
        float rIn = C_RADIUS - 0.15f;
        float rOut = C_RADIUS + 1.25f;

        Vector3f bInF  = new Vector3f(CX + ca * rIn,  CY + sa * rIn,  -0.45f);
        Vector3f bOutF = new Vector3f(CX + ca * rOut, CY + sa * rOut, -0.45f);
        Vector3f bOutB = new Vector3f(CX + ca * rOut, CY + sa * rOut,  0.45f);
        Vector3f bInB  = new Vector3f(CX + ca * rIn,  CY + sa * rIn,   0.45f);

        // Mid section of curved hook (continuing arc down to 30 deg, curving inward)
        double aMid = Math.toRadians(30);
        float caM = (float)Math.cos(aMid), saM = (float)Math.sin(aMid);
        Vector3f mInF  = new Vector3f(CX + caM * (C_RADIUS - 0.15f), CY + saM * (C_RADIUS - 0.15f), -0.32f);
        Vector3f mOutF = new Vector3f(CX + caM * (C_RADIUS + 0.95f), CY + saM * (C_RADIUS + 0.95f), -0.32f);
        Vector3f mOutB = new Vector3f(CX + caM * (C_RADIUS + 0.95f), CY + saM * (C_RADIUS + 0.95f),  0.32f);
        Vector3f mInB  = new Vector3f(CX + caM * (C_RADIUS - 0.15f), CY + saM * (C_RADIUS - 0.15f),  0.32f);

        // Sharp hook beak tip (curving down and inward to 21 deg)
        double aTip = Math.toRadians(21);
        float caT = (float)Math.cos(aTip), saT = (float)Math.sin(aTip);
        Vector3f tip = new Vector3f(CX + caT * (C_RADIUS - 0.65f), CY + saT * (C_RADIUS - 0.65f), 0.0f);

        // Base collar to mid hook
        list.add(new Vector3f[]{bOutF, bOutB, mOutB, mOutF});
        list.add(new Vector3f[]{bInB, bInF, mInF, mInB});
        list.add(new Vector3f[]{bInF, bOutF, mOutF, mInF});
        list.add(new Vector3f[]{bOutB, bInB, mInB, mOutB});

        // Mid hook to sharp beak tip
        list.add(new Vector3f[]{mOutF, mOutB, tip, tip});
        list.add(new Vector3f[]{mInB, mInF, tip, tip});
        list.add(new Vector3f[]{mInF, mOutF, tip, tip});
        list.add(new Vector3f[]{mOutB, mInB, tip, tip});

        // Bottom tip collar cap (at a = 308 deg)
        double aBot = Math.toRadians(308);
        float caB = (float)Math.cos(aBot), saB = (float)Math.sin(aBot);
        Vector3f botInF  = new Vector3f(CX + caB * rIn,  CY + saB * rIn,  -0.45f);
        Vector3f botOutF = new Vector3f(CX + caB * rOut, CY + saB * rOut, -0.45f);
        Vector3f botOutB = new Vector3f(CX + caB * rOut, CY + saB * rOut,  0.45f);
        Vector3f botInB  = new Vector3f(CX + caB * rIn,  CY + saB * rIn,   0.45f);
        Vector3f tipBot   = new Vector3f(CX + caB * (C_RADIUS + 0.55f) + 0.65f, CY + saB * (C_RADIUS + 0.55f) - 0.45f, 0.0f);

        list.add(new Vector3f[]{botOutF, botOutB, tipBot, tipBot});
        list.add(new Vector3f[]{botInB, botInF, tipBot, tipBot});
        list.add(new Vector3f[]{botInF, botOutF, tipBot, tipBot});
        list.add(new Vector3f[]{botOutB, botInB, tipBot, tipBot});

        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createPommelBlade() {
        List<Vector3f[]> list = new ArrayList<>();
        float spearTop = -37.6f, spearMid = -40.5f, spearTip = -43.8f;
        float wOut = 1.65f;
        Vector3f bladeTip = new Vector3f(0.0f, spearTip, 0.0f);

        Vector3f[] midPts = new Vector3f[8];
        Vector3f[] topPts = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4.0;
            float scaleX = 1.0f + 0.60f * (float)Math.abs(Math.cos(ang));
            midPts[i] = new Vector3f(wOut * scaleX * (float)Math.cos(ang), spearMid, 0.58f * (float)Math.sin(ang));
            topPts[i] = new Vector3f(0.95f * (float)Math.cos(ang), spearTop, 0.50f * (float)Math.sin(ang));
        }

        for (int i = 0; i < 8; i++) {
            int nxt = (i + 1) % 8;
            list.add(new Vector3f[]{topPts[i], midPts[i], midPts[nxt], topPts[nxt]});
            list.add(new Vector3f[]{midPts[i], bladeTip, midPts[nxt], midPts[nxt]});
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static void renderPommelBlade(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        renderQuads(matrices, vertices, POMMEL_BLADE, light, overlay, 0xFFB88A43);
    }

    private void renderDiamondBezel(PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
        float sizeX = 2.45f, sizeY = 3.35f, width = 0.48f, halfT = 0.24f;
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

            // 4 Cardinal 3D needle prongs
            Vector3f pt = outPts[i];
            float dx = pt.x / (pt.x != 0 ? sizeX : 1.0f);
            float dy = pt.y / (pt.y != 0 ? sizeY : 1.0f);
            Vector3f prongTip = new Vector3f(pt.x + dx * 0.95f, pt.y + dy * 0.95f, 0.0f);

            float pw = 0.40f;
            Vector3f ptF = new Vector3f(pt.x - dy * pw, pt.y + dx * pw, halfT * 0.8f);
            Vector3f ptB = new Vector3f(pt.x - dy * pw, pt.y + dx * pw, -halfT * 0.8f);
            Vector3f ptIn = new Vector3f(pt.x - dx * 0.35f, pt.y - dy * 0.35f, 0.0f);
            Vector3f ptSide = new Vector3f(pt.x + dy * pw, pt.y - dx * pw, 0.0f);

            quad(matrices, vertices, ptF, prongTip, ptSide, ptSide, light, overlay, 0xFFD8AA53);
            quad(matrices, vertices, ptSide, prongTip, ptB, ptB, light, overlay, 0xFFB88A43);
            quad(matrices, vertices, ptB, prongTip, ptIn, ptIn, light, overlay, 0xFF9E7432);
            quad(matrices, vertices, ptIn, prongTip, ptF, ptF, light, overlay, 0xFFB88A43);
        }
    }

    private void renderFacetedGem(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                                  float rx, float ry, float rz, int color) {
        int facets = 8;
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
