package net.godwand.spatial;

import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<BlockPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();

    public static boolean isPositionDisplaced(BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(pos);
    }
}
