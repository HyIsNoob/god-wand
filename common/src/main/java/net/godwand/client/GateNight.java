package net.godwand.client;

import net.godwand.skill.SilentGateSequence;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The cast brings its own night: while a gate stands near this viewer, the time of day this client shows runs round to midnight (the short way) and back to the true
 * time when the cast is over. Only the viewer's copy of the clock is written; the server's day time, sleeping, crops and every timer are untouched.
 */
final class GateNight {
    private GateNight() {}
    static final long MIDNIGHT = 18000, DAY = 24000;
    /** The server's day time as last known (-1: no night is being shown), and what was written over it. */
    private static long truth = -1, written;
    private static double depthNow;

    /** How deep the night of this cast is, 0..1: it falls while the caster gathers the spell and the great circle is written (20..120) and lifts over the last
     *  three seconds of the ending. */
    static double depth(SilentGateRenderer.Frame f) {
        double k = GateDraw.smooth((f.age() - 20) / 100);
        int endStart = SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks();
        return f.commandAge() < endStart ? k : k * (1 - GateDraw.smooth((f.commandAge() - endStart - (SilentGateSequence.ENDING_TICKS - 60)) / 55));
    }

    /** Ticks from {@code time} to midnight the short way round the day, -12000..11999. */
    static long toMidnight(long time) { return Math.floorMod(MIDNIGHT - time + DAY / 2, DAY) - DAY / 2; }
    /** The day time to show at depth {@code k}. */
    static long shown(long truth, double k) {
        long t = truth + Math.round(toMidnight(truth) * GateDraw.clamp01(k));
        return t < 0 ? t + DAY : t;
    }
    /** The true day time, given the clock as found this tick: our own value (ticked on by the client or not) means the truth moved on by as much; anything else
     *  is the server's word. */
    static long follow(long truth, long written, long now) { return now == written || now == written + 1 ? truth + (now - written) : now; }

    /** Once per client tick, {@code k} the deepest night of any cast in range (0: none, the true time is put back). */
    static void tick(ClientLevel level, double k) {
        var data = level.getLevelData();
        long now = data.getDayTime();
        if (truth < 0) { if (k <= 0) return; truth = now; }
        else truth = follow(truth, written, now);
        if (k <= 0) { data.setDayTime(truth); truth = -1; return; }
        depthNow = k;
        data.setDayTime(written = shown(truth, k));
    }
    /** Before every frame, at the frame's own moment ({@code partial} into the tick): the sky turns with the clock, and set only once a tick it stepped some sixty
     *  ticks of day at a time while the night fell (owner, 2026-11-15: "the sky jerks"); set every frame it turns smoothly. A time packet from the server (once a
     *  second, handled between ticks) is taken as the truth, so the day never shows for an instant either. */
    static void frame(float partial) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var level = client.level;
        if (truth < 0 || level == null) return;
        var data = level.getLevelData();
        long now = data.getDayTime();
        truth = follow(truth, written, now);
        double k = SilentGateRenderer.night(client, partial);
        if (k <= 0) return;   // the tick puts the true time back
        depthNow = k;
        data.setDayTime(written = shown(truth, k));
    }
    /** The world is gone: nothing to put back. */
    static void reset() { truth = -1; }
}
