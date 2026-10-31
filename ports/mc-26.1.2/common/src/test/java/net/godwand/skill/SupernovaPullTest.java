package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupernovaPullTest {
    @Test
    void lateGravityOvercomesEscapeAndLiftsMobsTowardTheCore() {
        Vec3 toward = new Vec3(30, 20, 0);
        Vec3 velocity = new Vec3(-0.4, -0.08, 0.2);
        for (int tick = 0; tick < 6; tick++) velocity = SupernovaManager.mobPullVelocity(velocity, toward, 0.95);
        assertTrue(velocity.dot(toward.normalize()) > 2);
        assertTrue(velocity.y > 0.8);
        assertTrue(velocity.length() <= 3.5);
        Vec3 early = SupernovaManager.mobPullVelocity(Vec3.ZERO, toward, 0.1);
        Vec3 late = SupernovaManager.mobPullVelocity(Vec3.ZERO, toward, 0.95);
        assertTrue(late.length() > early.length() * 2);
    }

    @Test
    void pullSlowsNearTheCoreAndCannotAccumulateUnlimitedSpeed() {
        Vec3 velocity = new Vec3(12, 0, 0);
        for (int tick = 0; tick < 100; tick++) {
            velocity = SupernovaManager.mobPullVelocity(velocity, new Vec3(20, 0, 0), 1);
            assertTrue(velocity.length() <= 3.5);
        }
        velocity = SupernovaManager.mobPullVelocity(new Vec3(1, 0, 0), new Vec3(0.3, 0, 0), 1);
        assertTrue(velocity.length() < 0.3);
        Vec3 center = SupernovaManager.mobPullVelocity(Vec3.ZERO, Vec3.ZERO, 1);
        assertEquals(Vec3.ZERO, center);
    }
}
