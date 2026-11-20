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
 * The caster's hand in Blue/Red mode (owner, 2026-11-20): in first person the wand is put away and the bare hand shows (no cursed-energy
 * effect on it: the owner prefers it bare); the hand reaches out while Blue is held, draws back while Red charges and punches forward on the shot. In third person the
 * arm points where they look while either is held. Light motions only.
 */
public final class BlueRedHand {
    private BlueRedHand() {}

    /** Whether this player's main hand holds the wand set to Blue/Red. */
    public static boolean holds(Player p) {
        var s = p.getMainHandItem();
        return s.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(s) == GodWandItem.MODE_BLUE_RED;
    }

    /** First person shows the bare hand instead of the wand: Purple's mode, and Fuga's (owner, 2026-11-20), whose wand is put away too. */
    public static boolean bareHand(Player p) {
        var s = p.getMainHandItem();
        if (!GodWandConfig.CLIENT.bareHand || !(s.getItem() instanceof GodWandItem)) return false;
        int mode = GodWandItem.getSkillMode(s);
        return mode == GodWandItem.MODE_BLUE_RED || mode == GodWandItem.MODE_FUGA;
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
