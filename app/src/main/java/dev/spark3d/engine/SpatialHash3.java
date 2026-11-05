package dev.spark3d.engine;

/**
 * Uniform-grid spatial hash over the XZ plane.
 *
 * <p>The 3D world is taller than it is deep, so a flat grid is the right shape:
 * a full 3D grid spends most of its cells on empty sky. Everything that needs
 * neighbour queries — pickups, drones, collision — uses XZ and then compares Y
 * directly.
 *
 * <p>Storage is rebucketed every frame by clearing counts, not by allocating.
 * The bucket arrays grow once to their high-water mark and stay there.
 */
public final class SpatialHash3 {

    private final float invCell;
    private final int dim;
    private final int[] counts;
    private final int[] slots;      // counts.length * MAX_PER_CELL
    private final int[] touched;    // cell indices touched since clear
    private int touchedCount;

    private static final int MAX_PER_CELL = 24;

    public SpatialHash3(float cellSize, float worldSize) {
        invCell = 1f / cellSize;
        dim = Math.max(1, (int) Math.ceil(worldSize / cellSize) + 2);
        counts = new int[dim * dim];
        slots = new int[dim * dim * MAX_PER_CELL];
        touched = new int[dim * dim];
    }

    public void clear() {
        for (int i = 0; i < touchedCount; i++) counts[touched[i]] = 0;
        touchedCount = 0;
    }

    private int cellOf(float x, float z) {
        int cx = clampCell((int) Math.floor(x * invCell) + dim / 2);
        int cz = clampCell((int) Math.floor(z * invCell) + dim / 2);
        return cx + cz * dim;
    }

    private int clampCell(int v) {
        if (v < 0) return 0;
        if (v >= dim) return dim - 1;
        return v;
    }

    public void insert(int id, float x, float z) {
        int c = cellOf(x, z);
        int n = counts[c];
        if (n >= MAX_PER_CELL) return;              // silently drop; a cell that full is a bug anyway
        if (n == 0) touched[touchedCount++] = c;
        slots[c * MAX_PER_CELL + n] = id;
        counts[c] = n + 1;
    }

    /**
     * Append ids in the 3x3 cell neighbourhood to {@code out}. Returns the count.
     * The caller owns {@code out} and its high-water size, so repeated queries
     * allocate nothing.
     */
    public int query(float x, float z, int[] out) {
        int cx = clampCell((int) Math.floor(x * invCell) + dim / 2);
        int cz = clampCell((int) Math.floor(z * invCell) + dim / 2);
        int n = 0;
        int cap = out.length;
        for (int dz = -1; dz <= 1; dz++) {
            int zz = cz + dz;
            if (zz < 0 || zz >= dim) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int xx = cx + dx;
                if (xx < 0 || xx >= dim) continue;
                int c = xx + zz * dim;
                int cnt = counts[c];
                int base = c * MAX_PER_CELL;
                for (int i = 0; i < cnt && n < cap; i++) out[n++] = slots[base + i];
            }
        }
        return n;
    }
}
