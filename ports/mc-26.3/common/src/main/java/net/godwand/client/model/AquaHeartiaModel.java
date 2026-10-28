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

import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Aqua Heartia - The legendary magic staff of Rudeus Greyrat (Mushoku Tensei inspired).
 * Features a seamless welded dark graphite chassis with a left-slanted top hood, 3 cyan energy vents,
 * a polished silver metallic orbital crescent ring, a mitered right-side bracket strut,
 * and the floating Water Dragon King sapphire magic sphere.
 */
public class AquaHeartiaModel {
    private final ModelPart root, chassis, silver, vents, manaCore;
    private float animationTime;
    private float charge;

    private static final Vector3f[][] CHASSIS_QUADS = createChassisQuads();
    private static final Vector3f[][] SILVER_QUADS = createSilverQuads();
    private static final Vector3f[][] VENT_QUADS = createVentQuads();
    private static final Vector3f[][] SPHERE_QUADS = createSphereQuads(3.525f, 14, 20);
    private static final Vector3f[][] CORE_SPHERE_QUADS = createSphereQuads(2.325f, 8, 12);
    private static final Vector3f[][] AURA_SPHERE_QUADS = createSphereQuads(4.05f, 8, 12);

    public AquaHeartiaModel(ModelPart root) {
        this.root = root;
        this.chassis = root.getChild("chassis");
        this.silver = root.getChild("silver");
        this.vents = root.getChild("vents");
        this.manaCore = root.getChild("mana_core");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("silver", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("vents", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("mana_core", CubeListBuilder.create(), PartPose.offset(0.0f, 23.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupProceduralAnimations(float time, float chargeRatio, int skillMode) {
        this.animationTime = time;
        this.charge = chargeRatio;
        this.manaCore.xRot = 0.0f;
        this.manaCore.zRot = 0.0f;
        this.manaCore.yRot = time * 0.025f;
        this.manaCore.x = 0.0f;
        this.manaCore.y = 23.0f;
        this.manaCore.z = 0.0f;
    }

    public void renderToBuffer(PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        renderQuads(matrices, vertexConsumer, CHASSIS_QUADS, light, overlay, 0xFF262328);
        renderQuads(matrices, vertexConsumer, SILVER_QUADS, light, overlay, 0xFFDCEDF8);
        renderQuads(matrices, vertexConsumer, VENT_QUADS, 0x00F000F0, overlay, 0xFF00E5FF);
    }

    public void renderWand(PoseStack matrices, Supplier<VertexConsumer> solidBuffer,
                           Supplier<VertexConsumer> emissiveBuffer, int light, int overlay, int coreColor) {

        // 1. Dark charcoal graphite chassis (hood, mitered right strut, lower junction, bottom cup, shaft)
        renderQuads(matrices, solidBuffer.get(), CHASSIS_QUADS, light, overlay, 0xFF262328);

        // 2. High-sheen polished metallic silver accents (welded joint clamp, orbital crescent ring, pommel collar)
        renderQuads(matrices, solidBuffer.get(), SILVER_QUADS, light, overlay, 0xFFDCEDF8);

        // 3. Glowing cyan energy vents on top hood
        renderQuads(matrices, emissiveBuffer.get(), VENT_QUADS, 0x00F000F0, overlay, 0xFF00E5FF);

        // 4. Floating Water Dragon King Magic Sphere
        matrices.pushPose();
        manaCore.translateAndRotate(matrices);
        float bob = (float) Math.sin(animationTime * 0.035f) * 0.005f;
        matrices.translate(0, bob, 0);

        // Deep royal sapphire blue orb body
        renderQuads(matrices, solidBuffer.get(), SPHERE_QUADS, light, overlay, 0xEE1465E5);

        // Luminous radiant core
        renderQuads(matrices, emissiveBuffer.get(), CORE_SPHERE_QUADS, 0x00F000F0, overlay, 0xFFE0FFFF);

        // Ambient magical aura
        int alphaOuter = Math.min(255, 30 + (int) (charge * 55));
        renderQuads(matrices, emissiveBuffer.get(), AURA_SPHERE_QUADS, 0x00F000F0, overlay,
                (alphaOuter << 24) | 0x00D2FF);

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
        if (len > 1e-5f) { nx /= len; ny /= len; nz /= len; } else { ny = 1.0f; }

        boolean glowing = (color & 0xFFFFFF) == 0x00E5FF || (color & 0xFFFFFF) == 0xE0FFFF || (color & 0xFFFFFF) == 0x00D2FF;
        boolean metal = ((color >> 16) & 255) > 120;
        float u0 = glowing || metal ? .52f : .02f;
        float v0 = glowing ? .52f : .02f;
        float u1 = u0 + .46f, v1 = v0 + .46f;

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

    private static void enlargeHead(List<Vector3f[]> quads) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Vector3f, Boolean>());
        for (var quad : quads) for (var point : quad) if (seen.add(point) && point.y > 14f / 16f) {
            point.x *= 1.5f; point.z *= 1.5f;
            point.y = 14f / 16f + (point.y - 14f / 16f) * 1.5f;
        }
    }

    private static void addMiteredTube(List<Vector3f[]> list, float[][] points, float[] radii) {
        int n = points.length;
        if (n < 2) return;

        Vector3f[] pts = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            pts[i] = new Vector3f(points[i][0] / 16.0f, points[i][1] / 16.0f, points[i][2] / 16.0f);
        }

        Vector3f[] tangents = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                Vector3f t = new Vector3f(pts[1]).sub(pts[0]);
                t.normalize();
                tangents[i] = t;
            } else if (i == n - 1) {
                Vector3f t = new Vector3f(pts[n - 1]).sub(pts[n - 2]);
                t.normalize();
                tangents[i] = t;
            } else {
                Vector3f t1 = new Vector3f(pts[i]).sub(pts[i - 1]).normalize();
                Vector3f t2 = new Vector3f(pts[i + 1]).sub(pts[i]).normalize();
                Vector3f t = new Vector3f(t1).add(t2).normalize();
                tangents[i] = t;
            }
        }

        List<Vector3f[]> rings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Vector3f t = tangents[i];
            Vector3f up = Math.abs(t.y()) < 0.9f ? new Vector3f(0.0f, 1.0f, 0.0f) : new Vector3f(1.0f, 0.0f, 0.0f);
            Vector3f s1 = new Vector3f(t).cross(up).normalize();
            Vector3f s2 = new Vector3f(t).cross(s1).normalize();

            float r = radii[i] / 16.0f;
            Vector3f[] ring = new Vector3f[8];
            for (int j = 0; j < 8; j++) {
                double ang = (j + 0.5) * Math.PI / 4.0;
                float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                Vector3f off = new Vector3f(s1).mul(c * r).add(new Vector3f(s2).mul(s * r));
                ring[j] = new Vector3f(pts[i]).add(off);
            }
            rings.add(ring);
        }

        for (int i = 0; i < n - 1; i++) {
            Vector3f[] r1 = rings.get(i);
            Vector3f[] r2 = rings.get(i + 1);
            for (int j = 0; j < 8; j++) {
                int nxt = (j + 1) % 8;
                list.add(new Vector3f[]{r1[j], r2[j], r2[nxt], r1[nxt]});
            }
        }
    }

    private static void addBeveledBox(List<Vector3f[]> list, float minX, float maxX,
                                      float minY, float maxY, float minZ, float maxZ) {
        float x0 = minX / 16.0f, x1 = maxX / 16.0f;
        float y0 = minY / 16.0f, y1 = maxY / 16.0f;
        float z0 = minZ / 16.0f, z1 = maxZ / 16.0f;
        Vector3f p0 = new Vector3f(x0, y0, z0), p1 = new Vector3f(x1, y0, z0);
        Vector3f p2 = new Vector3f(x1, y1, z0), p3 = new Vector3f(x0, y1, z0);
        Vector3f p4 = new Vector3f(x0, y0, z1), p5 = new Vector3f(x1, y0, z1);
        Vector3f p6 = new Vector3f(x1, y1, z1), p7 = new Vector3f(x0, y1, z1);

        list.add(new Vector3f[]{p0, p1, p2, p3}); // South (-Z)
        list.add(new Vector3f[]{p5, p4, p7, p6}); // North (+Z)
        list.add(new Vector3f[]{p4, p0, p3, p7}); // West (-X)
        list.add(new Vector3f[]{p1, p5, p6, p2}); // East (+X)
        list.add(new Vector3f[]{p3, p2, p6, p7}); // Top (+Y)
        list.add(new Vector3f[]{p4, p5, p1, p0}); // Bottom (-Y)
    }

    private static Vector3f[][] createSphereQuads(float r, int latDiv, int lonDiv) {
        List<Vector3f[]> list = new ArrayList<>();
        float rs = r / 16.0f;
        for (int lat = 0; lat < latDiv; lat++) {
            double theta1 = lat * Math.PI / latDiv;
            double theta2 = (lat + 1) * Math.PI / latDiv;
            float y1 = rs * (float) Math.cos(theta1);
            float y2 = rs * (float) Math.cos(theta2);
            float r1 = rs * (float) Math.sin(theta1);
            float r2 = rs * (float) Math.sin(theta2);
            for (int lon = 0; lon < lonDiv; lon++) {
                double phi1 = lon * 2.0 * Math.PI / lonDiv;
                double phi2 = (lon + 1) * 2.0 * Math.PI / lonDiv;
                Vector3f p0 = new Vector3f(r1 * (float) Math.cos(phi1), y1, r1 * (float) Math.sin(phi1));
                Vector3f p1 = new Vector3f(r2 * (float) Math.cos(phi1), y2, r2 * (float) Math.sin(phi1));
                Vector3f p2 = new Vector3f(r2 * (float) Math.cos(phi2), y2, r2 * (float) Math.sin(phi2));
                Vector3f p3 = new Vector3f(r1 * (float) Math.cos(phi2), y1, r1 * (float) Math.sin(phi2));
                list.add(new Vector3f[]{p0, p1, p2, p3});
            }
        }
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createChassisQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Top Hood: Slanted geometric hood
        float tz = 1.25f / 16.0f;
        Vector3f f0 = new Vector3f(-2.6f / 16.0f, 28.5f / 16.0f, tz);
        Vector3f f1 = new Vector3f(1.6f / 16.0f, 24.6f / 16.0f, tz);
        Vector3f f2 = new Vector3f(1.6f / 16.0f, 22.3f / 16.0f, tz);
        Vector3f f3 = new Vector3f(-0.5f / 16.0f, 22.5f / 16.0f, tz);
        Vector3f f4 = new Vector3f(-2.4f / 16.0f, 23.2f / 16.0f, tz);

        Vector3f b0 = new Vector3f(-2.6f / 16.0f, 28.5f / 16.0f, -tz);
        Vector3f b1 = new Vector3f(1.6f / 16.0f, 24.6f / 16.0f, -tz);
        Vector3f b2 = new Vector3f(1.6f / 16.0f, 22.3f / 16.0f, -tz);
        Vector3f b3 = new Vector3f(-0.5f / 16.0f, 22.5f / 16.0f, -tz);
        Vector3f b4 = new Vector3f(-2.4f / 16.0f, 23.2f / 16.0f, -tz);

        // Front faces
        list.add(new Vector3f[]{f0, f4, f3, f1});
        list.add(new Vector3f[]{f1, f3, f2, f1});
        // Back faces
        list.add(new Vector3f[]{b0, b1, b3, b4});
        list.add(new Vector3f[]{b1, b2, b3, b1});
        // Outer faces
        Vector3f[] fp = {f0, f1, f2, f3, f4};
        Vector3f[] bp = {b0, b1, b2, b3, b4};
        for (int i = 0; i < 5; i++) {
            int nxt = (i + 1) % 5;
            list.add(new Vector3f[]{fp[i], bp[i], bp[nxt], fp[nxt]});
        }

        // 2. Right Strut (One Continuous Mitered Tube connecting silver clamp to junction node)
        float[][] rightPts = {
            {2.2f, 24.5f, 0.0f},
            {3.4f, 21.8f, 0.0f},
            {4.0f, 19.5f, 0.0f},
            {3.0f, 18.0f, 0.0f},
            {1.4f, 15.0f, 0.0f}
        };
        float[] rightRadii = {0.45f, 0.45f, 0.48f, 0.45f, 0.50f};
        addMiteredTube(list, rightPts, rightRadii);

        // 3. Lower Junction Node (Seamless bridging block between shaft, socket, and right strut)
        addBeveledBox(list, -0.6f, 1.4f, 13.8f, 15.2f, -0.9f, 0.9f);

        // 4. Bottom Socket & Cup (Continuous Mitered Tube from junction node to cup under sphere)
        float[][] socketPts = {
            {0.2f, 14.8f, 0.0f},
            {-0.8f, 16.2f, 0.0f},
            {0.0f, 17.6f, 0.0f}
        };
        float[] socketRadii = {0.60f, 0.58f, 0.55f};
        addMiteredTube(list, socketPts, socketRadii);

        // Cup ring under sphere
        for (int i = 0; i < 8; i++) {
            double ang1 = i * Math.PI / 4.0;
            double ang2 = (i + 1) * Math.PI / 4.0;
            float r1 = 1.3f / 16.0f, r2 = 1.85f / 16.0f;
            float y1 = 17.2f / 16.0f, y2 = 18.3f / 16.0f;
            Vector3f p0 = new Vector3f(r1 * (float) Math.cos(ang1), y1, r1 * (float) Math.sin(ang1));
            Vector3f p1 = new Vector3f(r2 * (float) Math.cos(ang1), y2, r2 * (float) Math.sin(ang1));
            Vector3f p2 = new Vector3f(r2 * (float) Math.cos(ang2), y2, r2 * (float) Math.sin(ang2));
            Vector3f p3 = new Vector3f(r1 * (float) Math.cos(ang2), y1, r1 * (float) Math.sin(ang2));
            list.add(new Vector3f[]{p0, p1, p2, p3});
        }

        // 5. Staff Shaft & Pommel (Continuous Mitered Tube)
        float[][] shaftPts = {
            {0.0f, 14.0f, 0.0f},
            {0.0f, -6.0f, 0.0f},
            {0.0f, -36.0f, 0.0f},
            {0.0f, -38.0f, 0.0f}
        };
        float[] shaftRadii = {0.78f, 0.76f, 0.72f, 1.15f};
        addMiteredTube(list, shaftPts, shaftRadii);

        enlargeHead(list);
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[][] createSilverQuads() {
        List<Vector3f[]> list = new ArrayList<>();

        // 1. Joint Clamp seamlessly bridging hood [1.6] and right strut [2.2]
        addBeveledBox(list, 1.58f, 2.22f, 23.8f, 25.2f, -0.68f, 0.68f);

        // 2. Orbital Crescent Ring (Smooth 3D ribbon encircling sphere at angle)
        int numPts = 36;
        Vector3f[] pts = new Vector3f[numPts];
        for (int i = 0; i < numPts; i++) {
            double t = i / (double) (numPts - 1);
            double ang = Math.toRadians(-130.0 + t * 320.0);
            float rx = 4.25f, rz = 3.65f;
            float x = rx * (float) Math.cos(ang);
            float z = rz * (float) Math.sin(ang);
            float y = 20.0f + 0.35f * x - 0.25f * z;
            pts[i] = new Vector3f(x, y, z);
        }

        float w = 0.52f, h = 0.26f;
        for (int i = 0; i < numPts - 1; i++) {
            Vector3f p1 = pts[i];
            Vector3f p2 = pts[i + 1];
            float tx = p2.x() - p1.x(), ty = p2.y() - p1.y(), tz = p2.z() - p1.z();
            float tlen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tlen; ty /= tlen; tz /= tlen;

            float mx = (p1.x() + p2.x()) * 0.5f;
            float my = (p1.y() + p2.y()) * 0.5f - 20.0f;
            float mz = (p1.z() + p2.z()) * 0.5f;
            float dot = mx * tx + my * ty + mz * tz;
            float nx = mx - dot * tx, ny = my - dot * ty, nz = mz - dot * tz;
            float nlen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nlen; ny /= nlen; nz /= nlen;

            float bx = ty * nz - tz * ny;
            float by = tz * nx - tx * nz;
            float bz = tx * ny - ty * nx;

            Vector3f[] c1 = getBandCorners(p1, nx, ny, nz, bx, by, bz, w, h);
            Vector3f[] c2 = getBandCorners(p2, nx, ny, nz, bx, by, bz, w, h);

            for (int j = 0; j < 4; j++) {
                int nxt = (j + 1) % 4;
                list.add(new Vector3f[]{c1[j], c2[j], c2[nxt], c1[nxt]});
            }
        }

        // 3. Lower pommel silver ring (Continuous Mitered Ring)
        float[][] pommelRingPts = {{0.0f, -35.2f, 0.0f}, {0.0f, -36.0f, 0.0f}};
        addMiteredTube(list, pommelRingPts, new float[]{0.95f, 0.95f});

        enlargeHead(list);
        return list.toArray(new Vector3f[0][]);
    }

    private static Vector3f[] getBandCorners(Vector3f pt, float nx, float ny, float nz,
                                             float bx, float by, float bz, float w, float h) {
        float wn = w * 0.5f / 16.0f, hb = h * 0.5f / 16.0f;
        float px = pt.x() / 16.0f, py = pt.y() / 16.0f, pz = pt.z() / 16.0f;
        return new Vector3f[]{
            new Vector3f(px - nx * wn - bx * hb, py - ny * wn - by * hb, pz - nz * wn - bz * hb),
            new Vector3f(px + nx * wn - bx * hb, py + ny * wn - by * hb, pz + nz * wn - bz * hb),
            new Vector3f(px + nx * wn + bx * hb, py + ny * wn + by * hb, pz + nz * wn + bz * hb),
            new Vector3f(px - nx * wn + bx * hb, py - ny * wn + by * hb, pz - nz * wn + bz * hb)
        };
    }

    private static Vector3f[][] createVentQuads() {
        List<Vector3f[]> list = new ArrayList<>();
        float[][] ventData = {
            {26.6f, -1.8f, 0.4f},
            {25.3f, -1.4f, 0.9f},
            {24.0f, -1.0f, 1.3f}
        };
        float h = 0.35f / 16.0f;
        float zf = 1.27f / 16.0f;
        float zb = -1.27f / 16.0f;
        for (float[] v : ventData) {
            float y = v[0] / 16.0f;
            float x0 = v[1] / 16.0f;
            float x1 = v[2] / 16.0f;
            list.add(new Vector3f[]{
                new Vector3f(x0, y - h * 0.5f, zf),
                new Vector3f(x1, y - h * 0.5f, zf),
                new Vector3f(x1, y + h * 0.5f, zf),
                new Vector3f(x0, y + h * 0.5f, zf)
            });
            list.add(new Vector3f[]{
                new Vector3f(x1, y - h * 0.5f, zb),
                new Vector3f(x0, y - h * 0.5f, zb),
                new Vector3f(x0, y + h * 0.5f, zb),
                new Vector3f(x1, y + h * 0.5f, zb)
            });
        }
        enlargeHead(list);
        return list.toArray(new Vector3f[0][]);
    }
}
