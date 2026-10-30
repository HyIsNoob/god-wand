package net.godwand.skill;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.concurrent.*;

/** Only immutable coordinates cross this worker boundary; no world or chunk is captured. */
final class AtomicTerrainPlanner {
    record Columns(int[] offsets, int[] distances) {}
    private static final ExecutorService WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "God Wand terrain planner");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    static CompletableFuture<Columns> request(int radius) {
        try { return CompletableFuture.supplyAsync(() -> plan(radius), WORKER); }
        catch (RejectedExecutionException busy) { return null; }
    }
    static Columns plan(int radius) {
        if (radius < 0 || radius > AtomicSequence.MAX_RADIUS)
            throw new IllegalArgumentException("Atomic terrain radius out of bounds");
        var offsets = new IntArrayList(); var distances = new IntArrayList();
        var cursor = new SupernovaCraterCursor(radius);
        while (!cursor.finished()) {
            int x=cursor.x(), z=cursor.z(), distance=x*x+z*z;
            if (distance>9 && distance<=radius*radius) {
                offsets.add((x & 65535) | (z << 16)); distances.add(distance);
            }
            cursor.advance();
        }
        int[] radialDistances=distances.toIntArray(), radialOffsets=offsets.toIntArray();
        // A square spiral is not distance-ordered; every budgeted prefix must follow the circular front.
        it.unimi.dsi.fastutil.ints.IntArrays.quickSort(radialDistances,radialOffsets);
        return new Columns(radialOffsets,radialDistances);
    }
}
