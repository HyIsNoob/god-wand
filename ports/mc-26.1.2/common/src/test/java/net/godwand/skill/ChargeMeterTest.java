package net.godwand.skill;

import net.godwand.item.GodWandItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeMeterTest {
    @Test
    void meterAlwaysShowsAllAvailableStages() {
        for (int maximum : new int[]{3, 4}) {
            for (int level = 1; level <= maximum; level++) {
                String meter = GodWandItem.chargeMeter(level, maximum);
                assertEquals(level, meter.chars().filter(c -> c == '\u25a0').count());
                assertEquals(maximum - level, meter.chars().filter(c -> c == '\u25a1').count());
            }
        }
    }
}
