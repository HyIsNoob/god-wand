package net.godwand.client;

import net.godwand.network.SelectSkillPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectSkillPayloadTest {
    @Test
    void acceptsOnlyValidSkillsForTheCurrentlyHeldHotbarOrOffhandSlot() {
        assertTrue(new SelectSkillPayload(0, 2).matchesHeldSlot(2));
        assertTrue(new SelectSkillPayload(4, 8).matchesHeldSlot(8));
        assertTrue(new SelectSkillPayload(3, 40).matchesHeldSlot(2));
        assertFalse(new SelectSkillPayload(1, 2).matchesHeldSlot(3));
        assertFalse(new SelectSkillPayload(-1, 2).matchesHeldSlot(2));
        assertTrue(new SelectSkillPayload(5, 2).matchesHeldSlot(2));
        assertFalse(new SelectSkillPayload(6, 2).matchesHeldSlot(2));
        assertFalse(new SelectSkillPayload(0, -1).matchesHeldSlot(-1));
        assertFalse(new SelectSkillPayload(0, 9).matchesHeldSlot(9));
        assertFalse(new SelectSkillPayload(0, 39).matchesHeldSlot(2));
    }
}
