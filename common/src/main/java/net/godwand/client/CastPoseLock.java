package net.godwand.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Keeps a cast's body pose on top of other animation mods (owner, 2026-11-16). The poses are set at the end of HumanoidModel.setupAnim; PlayerModel
 * calls that first and animation mods (playerAnimator, Emotecraft, Better Combat, Not Enough Animations...) hook the end of PlayerModel.setupAnim, so
 * they ran after us and wiped the pose. Every posed part is captured here and put back by CastPoseLastMixin, which runs last.
 */
public final class CastPoseLock {
    private static final Map<ModelPart, PartPose> POSED = new IdentityHashMap<>();
    private CastPoseLock() {}

    /** Remembers the pose just given to {@code parts} (render thread). */
    public static void capture(ModelPart... parts) {
        if (POSED.size() > 64) POSED.clear(); // parts captured on a model that never reached PlayerModel
        for (ModelPart p : parts) POSED.put(p, p.storePose());
    }

    /** Puts back the captured pose of any of {@code parts}; true when at least one was posed this frame. */
    public static boolean restore(ModelPart... parts) {
        boolean any = false;
        for (ModelPart p : parts) {
            PartPose pose = POSED.remove(p);
            if (pose != null) { p.loadPose(pose); any = true; }
        }
        return any;
    }
}
