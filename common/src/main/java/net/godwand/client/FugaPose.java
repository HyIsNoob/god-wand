package net.godwand.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Vanilla model joints only: palms meet, pull flame apart, then hold a drawn bow. */
public final class FugaPose {
    private FugaPose() {}
    /** Torso turn while drawing; arm angles stay Minecraft's BOW_AND_ARROW branch. */
    static final float TWIST=.85f;
    /** Charge tick at which the palms meet. */
    static final double CLAP=95;
    public record Arms(float rightPitch, float rightYaw, float rightRoll,
                       float leftPitch, float leftYaw, float leftRoll, float bodyYaw, float weight) {}

    public static boolean apply(int casterId, float partial, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head) {
        var frame = FugaRenderer.frameFor(casterId, partial);
        if (frame == null || frame.phase() > 2 || frame.phase() == 2 && frame.age() >= 14) return false;
        boolean aligned=frame.phase()==0&&FugaAimRig.availableFor.test(casterId);
        var pose = arms(frame.phase(), frame.age(), head.xRot, aligned?0:head.yRot);
        if(aligned)head.yRot=0;
        float w = pose.weight();
        rightArm.xRot = Mth.lerp(w, rightArm.xRot, pose.rightPitch());
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, pose.rightYaw());
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, pose.rightRoll());
        leftArm.xRot = Mth.lerp(w, leftArm.xRot, pose.leftPitch());
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, pose.leftYaw());
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, pose.leftRoll());
        body.yRot = Mth.lerp(w, body.yRot, pose.bodyYaw());
        // Vanilla setupAnim resets these pivots every frame; rotate the shoulders with the torso only for Fuga.
        rightArm.x=-5*(float)Math.cos(pose.bodyYaw());rightArm.z=5*(float)Math.sin(pose.bodyYaw());
        leftArm.x=5*(float)Math.cos(pose.bodyYaw());leftArm.z=-5*(float)Math.sin(pose.bodyYaw());
        return true;
    }
    public static Arms arms(int phase, double age, float pitch, float yaw) {
        double charge = phase == 0 ? Math.min(age, net.godwand.skill.FugaSequence.CHARGE_TICKS) : 200;
        // Pull the flame apart, then the palms snap shut on it (the clap) before the screen goes black.
        float pull = FugaCinematic.smooth(charge, 48, 64) * (1 - FugaCinematic.smooth(charge, CLAP - 5, CLAP));
        float draw = FugaCinematic.smooth(charge, 144, 190);
        float settle = FugaCinematic.smooth(charge, 172, 200);
        float idle = phase == 0 && age > 200 ? (float)Math.sin((age - 200) * .065)
                * .009f * FugaCinematic.smooth(age, 200, 212) : 0;
        // The pull stacks the hands: the right one rises, the left sinks, stretching the flame between them.
        float rightPitch = Mth.lerp(draw, -1.35f - .32f * pull, phase==0?-(float)Math.PI/2:-1.50f);
        float leftPitch = Mth.lerp(draw, -1.35f + .26f * pull, -(float)Math.PI / 2);
        float rightYaw = Mth.lerp(draw, Mth.lerp(pull, -.55f, .22f), -.5f);
        float leftYaw = Mth.lerp(draw, Mth.lerp(pull, .55f, -.22f), phase==0?.1f:.42f);
        rightPitch += pitch * draw + (phase==0?0:idle);
        leftPitch += pitch * draw;
        rightYaw += yaw * draw;
        leftYaw += yaw * draw;
        float release = phase == 0 ? 0 : phase == 1 ? FugaCinematic.smooth(age, 0, 7) : 1;
        float relax = phase == 1 ? FugaCinematic.smooth(age, 7, 18) : phase == 2 ? 1 : 0;
        // Follow-through: the string hand slips back past the jaw a little, then both arms drop; a wide yaw swung it behind the back.
        rightPitch += release * .2f;
        rightYaw -= release * .22f;
        // Recoil kicks the extended bow arm upward before it relaxes.
        leftPitch -= release * (1 - relax) * .32f;
        // Then arms and torso settle back toward a natural stance instead of staying crossed over the chest.
        float rest = relax * .8f;
        rightPitch = Mth.lerp(rest, rightPitch, -.2f); rightYaw = Mth.lerp(rest, rightYaw, 0);
        leftPitch = Mth.lerp(rest, leftPitch, -.2f); leftYaw = Mth.lerp(rest, leftYaw, 0);
        float weight = (phase == 2 ? 1 - FugaCinematic.smooth(age, 0, 14) : 1);
        float tension = .04f * (float)Math.sin(Math.PI * settle);
        return new Arms(rightPitch - (phase==0?0:tension), rightYaw,
                -.12f * (1 - draw) - (phase==0?0:.08f)*draw - release * .2f,
                leftPitch, leftYaw, .12f * (1 - draw),
                 phase==0?TWIST*draw:((TWIST+.23f*release) * draw + tension) * (1 - rest), weight);
    }
    /** Archer's stance: right foot back under the drawing shoulder, released smoothly after the shot. */
    public static void stance(int casterId,float partial,ModelPart rightLeg,ModelPart leftLeg) {
        var f=FugaRenderer.frameFor(casterId,partial);if(f==null||f.phase()>1)return;
        float draw=f.phase()==0?FugaCinematic.smooth(f.age(),144,190):1-FugaCinematic.smooth(f.age(),10,24);
        float hips=TWIST*.7f*draw;
        rightLeg.xRot+=.34f*draw;leftLeg.xRot-=.18f*draw;
        rightLeg.yRot+=hips;leftLeg.yRot+=hips;
        rightLeg.z+=1.9f*Mth.sin(hips);leftLeg.z-=1.9f*Mth.sin(hips);
    }
    /** Approximate wrist centers in player-right/up/forward coordinates; skin geometry stays vanilla. */
    static net.minecraft.world.phys.Vec3 handFocus(double age, float pitch) {
        return handFocus(age,pitch,false);
    }
    static net.minecraft.world.phys.Vec3 handFocus(double age,float pitch,boolean rig) {
        return hand(age,pitch,true,rig).add(hand(age,pitch,false,rig)).scale(.5);
    }
    static net.minecraft.world.phys.Vec3 hand(double age,float pitch,boolean right) {
        return hand(age,pitch,right,false);
    }
    static net.minecraft.world.phys.Vec3 hand(double age,float pitch,boolean right,boolean rig) {
        var pose=arms(0,age,pitch,0);
        var hand=right ? wrist(pose.rightPitch(),pose.rightYaw(),pose.rightRoll(),true)
                : wrist(pose.leftPitch(),pose.leftYaw(),pose.leftRoll(),false);
        double side=right?-1:1;
        hand=hand.add(-side*5*(Math.cos(pose.bodyYaw())-1)/16,0,side*5*Math.sin(pose.bodyYaw())/16);
        return right&&rig?hand.lerp(FugaAimRig.hand(age,pitch),FugaAimRig.weight(age)):hand;
    }
    private static net.minecraft.world.phys.Vec3 wrist(float pitch,float yaw,float roll,boolean right) {
        var position=new org.joml.Quaternionf().rotationZYX(roll,yaw,pitch)
                .transform(new org.joml.Vector3f(right?-1:1,9,0));
        return new net.minecraft.world.phys.Vec3(-(position.x+(right?-5:5))/16.0,
                1.5-(position.y+2)/16.0,-position.z/16.0);
    }

}
