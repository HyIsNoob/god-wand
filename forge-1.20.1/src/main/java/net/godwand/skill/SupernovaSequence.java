package net.godwand.skill;

import net.minecraft.util.Mth;

/** Shared choreography for server debris, client ribbons, camera and sound. */
public final class SupernovaSequence {
    public static final int CHARGE_TICKS = 140;
    public static final int SKY_RELEASE = 348;
    public static final int SKY_CLOSE_START = SKY_RELEASE + 94;
    public static final int SKY_CLOSE_END = SKY_RELEASE + 186;
    public static final int BURST_TICKS = 280;
    public static final int SEQUENCE_END = SKY_RELEASE + BURST_TICKS;

    public static final int PEEL_END = 250;
    public static final int SWALLOW_END = 326;
    public static final int QUIET_START = 324;
    public static final int CUT_START = 328;
    public static final int CUT_END = 330;
    public static final int FLARE_START = 332;
    public static final int IMPACT = 340;

    public static float phase(double age, double start, double end) {
        float t = (float)Mth.clamp((age - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static float chargeRiftScale(double ticks) { return phase(ticks, 0, CHARGE_TICKS); }
    public static float skyCollapse(double age) { return phase(age, SKY_CLOSE_START, SKY_CLOSE_END); }
    public static float skyBlackout(double age) {
        return phase(age, SKY_RELEASE, SKY_RELEASE + 20) * (1 - skyCollapse(age));
    }
    public static float skyScale(double age) {
        return (1 + .65f * phase(age, SKY_RELEASE, SKY_RELEASE + 20)) * (1 - skyCollapse(age));
    }
    public static float activationPulse(double age) {
        return phase(age, 0, 2) * (1 - phase(age, 5, 22));
    }
    public static float skyPulseRadius(double age) {
        return age < SKY_CLOSE_START ? 6 + 90 * phase(age, 0, 22) : 225 * (1 - skyCollapse(age));
    }
    public static float skyPulseStrength(double age) {
        return age < SKY_CLOSE_START ? activationPulse(age) * .65f
                : .65f * phase(age, SKY_CLOSE_START, SKY_CLOSE_START + 6) * (1 - phase(age, SKY_CLOSE_END - 6, SKY_CLOSE_END));
    }
    public static float swallow(double age, double seed) {
        return phase(age, PEEL_END + Mth.clamp(seed, 0, 1) * 14, SWALLOW_END);
    }
    public static float pressure(double age) {
        return phase(age, 12, 220) * (0.35f + 0.65f * phase(age, 175, 320));
    }
    public static float ribbonVisibility(double age) {
        return phase(age, 0, 25) * (1 - phase(age, 322, CUT_START));
    }
    public static float coreRadius(double age) {
        float grow = (float)Math.pow(Mth.clamp(age / IMPACT, 0, (double)CUT_START / IMPACT), 0.95);
        return (0.15f + grow * 8.35f) * (1 - phase(age, CUT_START, CUT_END));
    }
    public static float cameraFov(double age) {
        float wide = 0.96f + 0.09f * phase(age,0,80) + 0.03f * phase(age,80,220);
        return wide + (0.88f - wide) * phase(age,PEEL_END,QUIET_START) + .055f * activationPulse(age);
    }
    public static float environmentGain(double age) {
        return (1 - 0.2f * pressure(age)) * (1 - 0.96f * phase(age, 316, QUIET_START));
    }
}
