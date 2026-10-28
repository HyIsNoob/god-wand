package net.godwand.skill;

/** Resumable center-out traversal without allocating a list of crater blocks. */
final class SupernovaCraterCursor {
    private final int columns;
    private int visited;
    private int x, z;
    private int dx, dz = -1;

    SupernovaCraterCursor(int radius) { columns = (radius * 2 + 1) * (radius * 2 + 1); }
    int x() { return x; }
    int z() { return z; }
    boolean finished() { return visited >= columns; }
    void advance() {
        if (finished()) return;
        if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
            int previousDx = dx;
            dx = -dz;
            dz = previousDx;
        }
        x += dx;
        z += dz;
        visited++;
    }
}
