package net.godwand.skill;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SupernovaCraterCursorTest {
    @Test
    void visitsEachColumnOnceFromCenterOutwardAndResumesAcrossTicks() {
        var cursor = new SupernovaCraterCursor(4);
        var visited = new HashSet<String>();
        int previousRing = 0;
        while (!cursor.finished()) {
            for (int budget = 0; budget < 7 && !cursor.finished(); budget++) {
                int ring = Math.max(Math.abs(cursor.x()), Math.abs(cursor.z()));
                assertTrue(ring >= previousRing);
                assertTrue(ring <= 4);
                assertTrue(visited.add(cursor.x() + "," + cursor.z()));
                previousRing = ring;
                cursor.advance();
            }
        }
        assertEquals(81, visited.size());
        assertTrue(visited.contains("0,0"));
    }
}
