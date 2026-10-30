package net.godwand.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Vanilla model joints only: palms meet, pull flame apart, then hold a drawn bow. */
public final class FugaPose {
    private FugaPose() {}
    public record Arms(float rightPitch, float rightYaw, float rightRoll,
                       float leftPitch, float leftYaw, float leftRoll, float bodyYaw, float weight) {}

    public static boolean apply(int casterId, float partial, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head) {
        var frame = FugaRenderer.frameFor(casterId, partial);
        if (frame == null || frame.phase() > 2 || frame.phase() == 2 && frame.age() >= 14) return false;
        var pose = arms(frame.phase(), frame.age(), head.xRot, head.yRot);
        float w = pose.weight();
        rightArm.xRot = Mth.lerp(w, rightArm.xRot, pose.rightPitch());
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, pose.rightYaw());
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, pose.rightRoll());
        leftArm.xRot = Mth.lerp(w, leftArm.xRot, pose.leftPitch());
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, pose.leftYaw());
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, pose.leftRoll());
        body.yRot = Mth.lerp(w, body.yRot, pose.bodyYaw());
        return true;
    }
    public static Arms arms(int phase, double age, float pitch, float yaw) {
        float draw = phase == 0 ? FugaCinematic.smooth(age, 58, 108) : 1;
        float join = FugaCinematic.smooth(age, 10, 30);
        float separate = FugaCinematic.smooth(age, 35, 70);
        float rise = phase == 0 ? FugaCinematic.smooth(age, 0, 16) : 1;
        float release = phase == 0 ? 0 : phase == 1 ? FugaCinematic.smooth(age, 0, 7) : 1;
        float settle = phase == 1 ? FugaCinematic.smooth(age, 7, 18) : phase == 2 ? 1 : 0;
        float idle = phase == 0 && age >= 120 ? (float)Math.sin((age - 120) * .065) * .012f : 0;
        float handsPitch = -.65f - .7f * join;
        float handsYaw = .65f * join * (1 - separate);
        float rightPitch = Mth.lerp(draw, handsPitch, -1.5f + pitch) + idle;
        float leftPitch = Mth.lerp(draw, handsPitch, -(float)Math.PI / 2 + pitch) - idle;
        float rightYaw = Mth.lerp(draw, -handsYaw, yaw - .94f);
        float leftYaw = Mth.lerp(draw, handsYaw, yaw + .08f);
        rightPitch += release * .13f + settle * .5f;
        rightYaw -= release * .32f;
        leftPitch += settle * .65f;
        float weight = rise * (phase == 2 ? 1 - FugaCinematic.smooth(age, 0, 14) : 1);
        return new Arms(rightPitch, rightYaw, -.08f * draw - release * .2f,
                leftPitch, leftYaw, .08f * draw, -.18f * draw, weight);
    }
}
