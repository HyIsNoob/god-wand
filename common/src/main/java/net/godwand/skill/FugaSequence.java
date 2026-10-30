package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import net.godwand.network.FugaFxPayload;

/** Shared server/client clock; impact age starts when the arrow stops. */
public final class FugaSequence {
    public static final int CHARGE_TICKS=120, ORB_TICKS=40, RETRACT_TICK=120, END_TICK=160;
    public static final int COLUMN_HEIGHT=384;
    public static final double SPEED=4, MAX_RANGE=160, RADIUS=24;
    private FugaSequence() {}
    public static boolean movementLocked(int phase) {return phase==FugaFxPayload.CHARGE;}
    public static boolean ready(long now,long started) { return now>=started && now-started>=CHARGE_TICKS; }
    public static double impactAge(double now,long phaseAt) { return Math.max(0,now-phaseAt); }
    public static boolean terrainActive(double age) { return age>=ORB_TICKS && age<RETRACT_TICK; }
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
    public static Vec3 arrowPosition(Vec3 origin,Vec3 target,double age) {
        double distance=origin.distanceTo(target);
        if(distance<1e-9)return origin;
        return origin.lerp(target,Math.max(0,Math.min(1,age*SPEED/distance)));
    }
}
