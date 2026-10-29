package net.godwand.skill;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicTerrainPlannerTest {
    @Test void backgroundPlanContainsEveryDestructibleColumnExactlyOnce() throws Exception {
        int radius=147;
        var future=AtomicTerrainPlanner.request(radius);
        assertNotNull(future);
        var plan=future.get(5,TimeUnit.SECONDS);
        var seen=new HashSet<Integer>();
        for(int i=0;i<plan.offsets().length;i++) {
            int packed=plan.offsets()[i], x=(short)packed, z=(short)(packed>>16);
            assertTrue(seen.add(packed));
            assertEquals(x*x+z*z,plan.distances()[i]);
            assertTrue(plan.distances()[i]>9 && plan.distances()[i]<=radius*radius);
        }
        int expected=0;
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(x*x+z*z>9 && x*x+z*z<=radius*radius)expected++;
        assertEquals(expected,seen.size());
        assertEquals(0,AtomicTerrainPlanner.plan(0).offsets().length);
        assertThrows(IllegalArgumentException.class,()->AtomicTerrainPlanner.plan(148));
    }
}
