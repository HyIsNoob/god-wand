package net.godwand.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillWheelSelectionTest {
    @Test
    void dragChoosesSixSkillsClockwiseFromTheTop() {
        assertEquals(0, SkillWheelSelection.modeAt(0, -80));
        assertEquals(1, SkillWheelSelection.modeAt(69, -40));
        assertEquals(2, SkillWheelSelection.modeAt(69, 40));
        assertEquals(3, SkillWheelSelection.modeAt(0, 80));
        assertEquals(4, SkillWheelSelection.modeAt(-69, 40));
        assertEquals(5, SkillWheelSelection.modeAt(-69, -40));
    }

    @Test
    void centerCancelsButDraggingPastTheWheelStillSelects() {
        assertEquals(-1, SkillWheelSelection.modeAt(0, 0));
        assertEquals(-1, SkillWheelSelection.modeAt(30, 30));
        assertEquals(-1, SkillWheelSelection.modeAt(0, -47.9));
        assertEquals(0, SkillWheelSelection.modeAt(0, -48));
        assertEquals(5, SkillWheelSelection.modeAt(-800, -460));
    }

    @Test
    void topSeamAndNeighborBoundariesDoNotWrapToInvalidSkills() {
        assertEquals(0, SkillWheelSelection.modeAt(-0.01, -80));
        assertEquals(0, SkillWheelSelection.modeAt(0.01, -80));
        assertEquals(0, SkillWheelSelection.modeAt(45, -80));
        assertEquals(1, SkillWheelSelection.modeAt(47, -80));
        assertEquals(5, SkillWheelSelection.modeAt(-47, -80));
    }
}
