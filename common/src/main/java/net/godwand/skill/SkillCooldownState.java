package net.godwand.skill;

import net.godwand.item.GodWandItem;

/** Tick-based timers shared by server authority and the client display. */
public final class SkillCooldownState {
    public static final int MAX_TICKS=720000;
    /** One slot per wheel mode, plus Red's own slot: mode 8 holds Blue (left click) and Red (right click). */
    public static final int RED_SLOT=GodWandItem.TOTAL_MODES, SLOTS=GodWandItem.TOTAL_MODES+1;
    private final long[] ends=new long[SLOTS];
    private final int[] durations=new int[SLOTS];
    public void set(int mode,int duration,int remaining,long now) {
        if(mode<0||mode>=ends.length||duration<0||duration>MAX_TICKS||remaining<0||remaining>duration)throw new IllegalArgumentException("Invalid skill cooldown");
        durations[mode]=duration;ends[mode]=now+remaining;
    }
    public int remaining(int mode,long now) {return mode<0||mode>=ends.length?0:(int)Math.max(0,ends[mode]-now);}
    public int duration(int mode) {return mode<0||mode>=ends.length?0:durations[mode];}
    public float fraction(int mode,long now) {return duration(mode)==0?0:remaining(mode,now)/(float)duration(mode);}
}
