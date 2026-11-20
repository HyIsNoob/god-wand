package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The caster's hand in Blue/Red mode (owner, 2026-11-20): in first person the wand is put away and the bare hand shows, wreathed in blue
 * cursed energy; the hand reaches out while Blue is held, draws back while Red charges and punches forward on the shot. In third person the
 * arm points where they look while either is held. Light motions only.
 */
public final class BlueRedHand {
    private BlueRedHand() {}

    /** Whether this player's main hand holds the wand set to Blue/Red. */
    public static boolean holds(Player p) {
        var s = p.getMainHandItem();
        return s.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(s) == GodWandItem.MODE_BLUE_RED;
    }

    /** How far into each motion the hand is: Blue held, Red charging (more as it fills), the shot's punch; and the clock. */
    record Motion(double blue, double red, double punch, double now) {}

    /** Shown blue, shown red, last clock: eased per player so the hand never snaps. */
    private static final Map<Integer, double[]> SHOWN = new HashMap<>();

    static Motion motion(int id, float partial) {
        double b = BlueRedRenderer.blueAge(id, partial), r = BlueRedRenderer.redChargeAge(id, partial), s = BlueRedRenderer.sinceShot(id, partial);
        double now = BlueRedRenderer.now(partial), full = Math.max(1, GodWandConfig.SERVER.redChargeTicks);
        double tb = b < 0 ? 0 : 1, tr = r < 0 ? 0 : .5 + .5 * Math.min(1, r / full);
        double[] st = SHOWN.computeIfAbsent(id, k -> new double[]{0, 0, now});
        if (now < st[2]) st[2] = now;
        double k = 1 - Math.exp(-Math.min(5, now - st[2]) / 2.5);
        st[0] += (tb - st[0]) * k; st[1] += (tr - st[1]) * k; st[2] = now;
        if (tb == 0 && tr == 0 && st[0] < 1e-3 && st[1] < 1e-3) SHOWN.remove(id);
        return new Motion(st[0], st[1], s < 0 ? 0 : Math.exp(-s / 3), now);
    }

    /** The bare hand's offset in view space (x right, y up, z towards the camera), before vanilla draws the arm. */
    static Vec3 offset(Motion m, int side) {
        double t = m.now(), breathe = .012 * Math.sin(t * .07);
        double tremble = .004 * m.blue() + (.003 + .008 * m.red()) * m.red();
        double x = side * (-.10 * m.blue() + .04 * m.red() - .06 * m.punch()) + tremble * BlueRedDraw.snoise(11, t * 1.7);
        double y = breathe + .10 * m.blue() - .05 * m.red() + .05 * m.punch() + tremble * BlueRedDraw.snoise(12, t * 1.9);
        double z = -.18 * m.blue() + .10 * m.red() - .30 * m.punch();
        return new Vec3(x, y, z);
    }

    public static void firstPerson(PoseStack pose, Player p, float partial) {
        Vec3 o = offset(motion(p.getId(), partial), p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
        pose.translate(o.x, o.y, o.z);
    }

    /** Cursed energy burning on the first-person arm (owner, 2026-11-20): drawn right after vanilla's arm, with vanilla's own arm placement
     *  (the stack as ItemInHandRenderer.renderPlayerArm leaves it, then the arm part's pivot and its idle sway), so the shell sits on the arm. */
    public static void curse(PoseStack pose, net.minecraft.client.player.AbstractClientPlayer p, float partial) {
        // Vanilla's renderPlayerArm leaves its placement on the stack (no push/pop), so only the arm part's own pivot and idle sway remain.
        boolean right = p.getMainArm() == HumanoidArm.RIGHT;
        float f = right ? 1 : -1;
        pose.pushPose();
        boolean slim = p.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        pose.translate(f * -5 / 16f, (slim ? 2.5f : 2f) / 16f, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(f * .1f));
        double w = (slim ? 3 : 4) / 16.0, inner = 1 / 16.0;
        // Right arm box: x from -3 (outer) to +1 px; mirrored for the left.
        double x0 = right ? inner - w : -inner, x1 = right ? inner : w - inner;
        var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        GateDraw.Ctx c = new GateDraw.Ctx();
        c.v = buffer; c.m = pose.last().pose(); c.eye = Vec3.ZERO; c.layer = -1; c.nearMin = 0; c.nearMax = 0;
        BlueRedDraw.curseShell(c, x0, x1, -2 / 16.0, 2 / 16.0, -.25, 10 / 16.0, power(p.getId(), partial));
        pose.popPose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        BlueRedAtlas.bind();
        BlueRedShader.bind((float) ((BlueRedRenderer.now(partial) / 20.0) % 3600));
        try {
            var mesh = buffer.build();
            if (mesh != null) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            com.mojang.blaze3d.systems.RenderSystem.enableCull();
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }

    /** Where the first-person hand is in the world, roughly (for the flames round it). */
    /** {@code fovScale}: the world view's tan(fov/2) over the hand's (drawn at a fixed 70 degrees), so the point follows the drawn hand. */
    static Vec3 handPoint(Vec3 eye, Vec3 look, Player p, float partial, double fovScale) {
        int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 o = offset(motion(p.getId(), partial), side);
        // Measured on QA frames: the top of the fist sits about here at the default FOV.
        return eye.add(look.scale(.75 - o.z)).add(right.scale((.36 * side + o.x) * fovScale)).add(up.scale((-.2 + o.y) * fovScale));
    }

    /** Where the hand is in third person: hanging at the side, swung up to point where they look while casting. */
    static Vec3 handPoint3rd(Player p, float partial) {
        Motion m = motion(p.getId(), partial);
        double w = Math.min(1, Math.max(m.blue(), m.red()) + m.punch());
        double yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(side);
        Vec3 shoulder = p.getPosition(partial).add(0, p.isCrouching() ? 1.1 : 1.38, 0).add(right.scale(.36));
        Vec3 hang = shoulder.add(0, -.62, 0), reach = shoulder.add(p.getViewVector(partial).scale(.62));
        return hang.add(reach.subtract(hang).scale(w));
    }

    /** Auto third person (option, owner 2026-11-20): the view type the player had before, while we hold them in third person. */
    private static net.minecraft.client.CameraType before, cut;
    private static int idle;

    /** Once a tick: while the local player casts Blue or Red (and a little after the shot), third person; then back to what they had. */
    static void tickCamera() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var me = mc.player;
        if (me == null) { before = null; cut = null; return; }
        // Purple's cutscene camera starts at the eye: in third person their own head would fill the screen, so first person while it runs,
        // then back to the view they had before casting (or before the cutscene).
        if (PurpleCinematic.active()) {
            if (cut == null) {
                cut = before != null ? before : mc.options.getCameraType();
                before = null;
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
            return;
        }
        if (cut != null) {
            if (mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(cut);
            cut = null;
        }
        double shot = BlueRedRenderer.sinceShot(me.getId(), 0);
        boolean casting = GodWandConfig.CLIENT.blueRedThirdPerson
                && (BlueRedRenderer.blueAge(me.getId(), 0) >= 0 || BlueRedRenderer.redChargeAge(me.getId(), 0) >= 0 || shot >= 0 && shot < 25);
        if (casting) {
            idle = 0;
            if (before == null) {
                before = mc.options.getCameraType();
                if (before.isFirstPerson()) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            }
        } else if (before != null && ++idle > 15) {
            if (mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(before);
            before = null;
        }
    }

    /** How strongly the cursed energy burns on the hand: a low flame at rest, full while Blue is held, between while Red charges. */
    static double power(int id, float partial) {
        Motion m = motion(id, partial);
        return .35 + .65 * Math.max(m.blue(), .55 * m.red()) + .3 * m.punch();
    }

    /** Third person: while Blue or Red is held, the main arm points where they look (Red a little lower, drawn in); the shot snaps it out. */
    public static void thirdPerson(LivingEntity e, float partial, ModelPart rightArm, ModelPart leftArm, ModelPart head) {
        if (!(e instanceof Player p) || !holds(p)) return;
        Motion m = motion(e.getId(), partial);
        double w = Math.min(1, Math.max(m.blue(), m.red()) + m.punch());
        if (w < 1e-3) return;
        ModelPart arm = p.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm;
        float side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float aim = head.xRot - (float) (Math.PI / 2) + (float) (.35 * m.red() * (1 - m.punch()));
        arm.xRot = Mth.lerp((float) w, arm.xRot, aim);
        arm.yRot = Mth.lerp((float) w, arm.yRot, head.yRot - .1f * side);
    }
}
