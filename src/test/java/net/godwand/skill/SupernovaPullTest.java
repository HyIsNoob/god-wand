package net.godwand.skill;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupernovaPullTest {
    @Test
    void lateGravityOvercomesEscapeAndLiftsMobsTowardTheCore() {
        Vec3d toward = new Vec3d(30, 20, 0);
        Vec3d velocity = new Vec3d(-0.4, -0.08, 0.2);
        for (int tick = 0; tick < 6; tick++) velocity = SupernovaManager.mobPullVelocity(velocity, toward, 0.95);
        assertTrue(velocity.dotProduct(toward.normalize()) > 2);
        assertTrue(velocity.y > 0.8);
        assertTrue(velocity.length() <= 3.5);
        Vec3d early = SupernovaManager.mobPullVelocity(Vec3d.ZERO, toward, 0.1);
        Vec3d late = SupernovaManager.mobPullVelocity(Vec3d.ZERO, toward, 0.95);
        assertTrue(late.length() > early.length() * 2);
    }

    @Test
    void pullSlowsNearTheCoreAndCannotAccumulateUnlimitedSpeed() {
        Vec3d velocity = new Vec3d(12, 0, 0);
        for (int tick = 0; tick < 100; tick++) {
            velocity = SupernovaManager.mobPullVelocity(velocity, new Vec3d(20, 0, 0), 1);
            assertTrue(velocity.length() <= 3.5);
        }
        velocity = SupernovaManager.mobPullVelocity(new Vec3d(1, 0, 0), new Vec3d(0.3, 0, 0), 1);
        assertTrue(velocity.length() < 0.3);
        Vec3d center = SupernovaManager.mobPullVelocity(Vec3d.ZERO, Vec3d.ZERO, 1);
        assertEquals(Vec3d.ZERO, center);
    }
}
