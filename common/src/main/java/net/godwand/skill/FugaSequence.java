package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import net.godwand.network.FugaFxPayload;

/** Shared server/client clock; impact age starts when the arrow stops. */
public final class FugaSequence {
    public static final int CHARGE_TICKS=200, ORB_TICKS=80, RETRACT_TICK=225, END_TICK=305;
    public static final int COLUMN_HEIGHT=384, SETTLE_TICKS=240;
    public static final double SPEED=4, MAX_RANGE=160, RADIUS=48;
    /** Ticks between the arrow's landing and the meteor's first frame (hit, hit-stop with the skyward beam, impact
     *  beats). The impact clock starts after it, so the sphere appears at its full height and the blast stays in sync. */
    public static final int TRANSITION_TICKS=14;
    private FugaSequence() {}
    public static double chargeAge(double age) { return age*120/CHARGE_TICKS; }
    /** Meteor at half speed, the blast at near real time, then the column at half speed. */
    public static double visualAge(double age) { return age<ORB_TICKS?age*.5:age<ORB_TICKS+25?40+(age-ORB_TICKS)*.8:60+(age-ORB_TICKS-25)*.5; }
    public static boolean movementLocked(int phase) {return phase==FugaFxPayload.CHARGE;}
    public static boolean ready(long now,long started) { return now>=started && now-started>=CHARGE_TICKS; }
    public static double impactAge(double now,long phaseAt) { return Math.max(0,now-phaseAt); }
    /** Starts under the imploding sphere; the outer rim may keep settling briefly after the visuals end. */
    public static boolean terrainActive(double age) { return age>=ORB_TICKS-8 && age<END_TICK+SETTLE_TICKS; }
    public static float columnRadius(double age) {
        if(age<ORB_TICKS || age>=END_TICK)return 0;
        return (float)(RADIUS*(age<=RETRACT_TICK?1:(END_TICK-age)/(END_TICK-RETRACT_TICK)));
    }
    public static Vec3 launchTarget(Vec3 origin,Vec3 look) {
        Vec3 direction=look.normalize();double range=MAX_RANGE;
        double[] starts={origin.x,origin.y,origin.z}, axes={direction.x,direction.y,direction.z};
        for(int i=0;i<3;i++)if(Math.abs(axes[i])>1e-9)
            range=Math.min(range,Math.max(0,((axes[i]>0?29_999_999:-29_999_999)-starts[i])/axes[i]));
        return origin.add(direction.scale(range));
    }
    /** Blocks flown after release: the arrow leaves the bow slowly (a beat of slow motion, ~1 block/tick) and
     *  accelerates to SPEED. Server travel and client visuals share it so the impact never drifts. */
    public static double flightDistance(double age) {
        if(!(age>0))return 0;
        return SPEED*age-FLIGHT_EASE*(SPEED-1)*(1-Math.exp(-age/FLIGHT_EASE));
    }
    static final double FLIGHT_EASE=6;
    public static Vec3 arrowPosition(Vec3 origin,Vec3 target,double age) {
        double distance=origin.distanceTo(target);
        if(distance<1e-9)return origin;
        return origin.lerp(target,Math.max(0,Math.min(1,flightDistance(age)/distance)));
    }
}
