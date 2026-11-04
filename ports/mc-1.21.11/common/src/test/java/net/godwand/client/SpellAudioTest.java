package net.godwand.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpellAudioTest {
    @Test
    void drillPlaceholderKeepsFallbackAndAllLevelsUseOneFixedTimeline() throws Exception {
        byte[] custom = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../../build/shared/mc-1.21.11/src/main/resources/assets/godwand/sounds/earth_drill_sequence.ogg"));
        assertTrue(EarthDrillSoundscape.isCustomAudio(custom));
        assertFalse(EarthDrillSoundscape.isCustomAudio(new byte[0]));
        assertFalse(EarthDrillSoundscape.isCustomAudio(new byte[]{1, 2, 3, 4}));

        java.nio.file.Path backup = java.nio.file.Path.of(".local-backups/20261024-111339-before-feedback-fixes/src/main/resources/assets/godwand/sounds/earth_drill_sequence.ogg");
        if (java.nio.file.Files.exists(backup)) {
            byte[] placeholder = java.nio.file.Files.readAllBytes(backup);
            assertFalse(EarthDrillSoundscape.isCustomAudio(placeholder));
        }

        assertEquals(88, net.godwand.skill.RatioCollapseManager.ASSEMBLY_END);
        assertEquals(158, net.godwand.skill.RatioCollapseManager.SPIN_END);
        assertEquals(167, net.godwand.skill.RatioCollapseManager.IMPACT_TICK);
        assertEquals(176, net.godwand.skill.RatioCollapseManager.SEQUENCE_END);
        for (double distance : new double[]{28, 45, 65, 85, 100})
            assertTrue(EarthDrillSoundscape.distanceGain(distance) >= .53f);
        assertEquals(0, EarthDrillSoundscape.distanceGain(160));
        assertEquals(0, EarthDrillSoundscape.distanceGain(1000));
        assertEquals(1, EarthDrillSoundscape.distanceGain(0));
    }

    @Test
    void cannonStoneIsAudibleFromTheStartAndBoundedWhileHolding() {
        float previous = 0;
        for (int tick = 0; tick < 1000; tick++) {
            float volume = net.godwand.skill.StoneCannonManager.chargeStoneVolume(tick);
            assertTrue(volume >= .28f && volume <= .421f);
            assertTrue(volume >= previous);
            previous = volume;
        }
    }

    @Test
    void cannonWindWaitsForHighChargeAndGrowsWithoutSpikes() {
        for(int ticks=-5;ticks<=50;ticks++) assertEquals(0,StoneCannonWindSound.windVolume(ticks));
        float previous=0;
        for(int ticks=51;ticks<=140;ticks++) {
            float current=StoneCannonWindSound.windVolume(ticks);
            assertTrue(current>=previous && current<=0.18f);
            assertTrue(current-previous<0.013f);
            previous=current;
        }
    }

    @Test
    void supernovaUsesVanillaChargeOnlyBeforeActivation() throws Exception {
        byte[] custom = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../../build/shared/mc-1.21.11/src/main/resources/assets/godwand/sounds/supernova_sequence.ogg"));
        assertTrue(SupernovaSoundscape.isCustomAudio(custom));
        assertFalse(SupernovaSoundscape.isCustomAudio(new byte[0]));
        assertFalse(SupernovaSoundscape.isCustomAudio(new byte[]{1, 2, 3, 4}));

        java.nio.file.Path backup = java.nio.file.Path.of(".local-backups/20261024-111339-before-feedback-fixes/src/main/resources/assets/godwand/sounds/supernova_sequence.ogg");
        if (java.nio.file.Files.exists(backup)) {
            byte[] placeholder = java.nio.file.Files.readAllBytes(backup);
            assertFalse(SupernovaSoundscape.isCustomAudio(placeholder));
        }

        assertEquals(0,SupernovaSoundscape.volume(-1,0));
        assertTrue(SupernovaSoundscape.volume(-1,1)>0);
        for(int age=0;age<700;age++) assertEquals(0,SupernovaSoundscape.volume(age,1));
        assertEquals(280,net.godwand.skill.SupernovaSequence.BURST_TICKS);
    }
}
