package net.godwand.skill;

import net.godwand.item.GodWandItem;

/** Tick-based timers shared by server authority and the client display. */
public final class SkillCooldownState {
    public static final int MAX_TICKS=720000;
    private final long[] ends=new long[GodWandItem.TOTAL_MODES];
    private final int[] durations=new int[GodWandItem.TOTAL_MODES];
    public void set(int mode,int duration,int remaining,long now) {
        if(mode<0||mode>=ends.length||duration<0||duration>MAX_TICKS||remaining<0||remaining>duration)throw new IllegalArgumentException("Invalid skill cooldown");
        durations[mode]=duration;ends[mode]=now+remaining;
    }
    public int remaining(int mode,long now) {return mode<0||mode>=ends.length?0:(int)Math.max(0,ends[mode]-now);}
    public int duration(int mode) {return mode<0||mode>=ends.length?0:durations[mode];}
    public float fraction(int mode,long now) {return duration(mode)==0?0:remaining(mode,now)/(float)duration(mode);}
}
