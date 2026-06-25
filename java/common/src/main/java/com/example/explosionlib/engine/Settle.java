package com.example.explosionlib.engine;

import net.minecraft.world.level.block.state.BlockState;

import static com.example.explosionlib.engine.ExplosionConfig.*;

public final class Settle {
    private Settle() {}

    public static void granularSettle(VoxelRegion r, int x0, int x1, int y0, int y1) {
        settleColumns(r, x0, x1, y0, y1);
        toppleRepose(r, x0, x1, y0, y1, REPOSE_SWEEPS);
        settleColumns(r, x0, x1, y0, y1);
    }

    public static void settleColumns(VoxelRegion r, int x0, int x1, int y0, int y1) {
        final int nz = r.nz;
        int[] gid = new int[nz];
        BlockState[] gst = new BlockState[nz];
        float[] grs = new float[nz];
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++) {
                int start = 0;
                for (int z = 0; z <= nz; z++) {
                    boolean barrier = z < nz && isBarrier(r.id[r.idx(x, y, z)]);
                    if (z == nz || barrier) {
                        int count = 0;
                        for (int gz = start; gz < z; gz++) {
                            int i = r.idx(x, y, gz);
                            if (Material.isLoose(r.id[i])) {
                                gid[count] = r.id[i];
                                gst[count] = r.state[i];
                                grs[count] = r.resist[i];
                                count++;
                            }
                        }
                        if (count > 0) {
                            for (int gz = start, j = 0; gz < z; gz++, j++) {
                                int i = r.idx(x, y, gz);
                                if (j < count) {
                                    r.id[i] = gid[j];
                                    r.state[i] = gst[j];
                                    r.resist[i] = grs[j];
                                } else {
                                    r.id[i] = Material.AIR;
                                    r.state[i] = Palette.AIR;
                                    r.resist[i] = 0f;
                                }
                            }
                        }
                        start = z + 1;
                    }
                }
            }
    }

    /** Slide surface loose grains downhill until slopes respect the angle of repose. */
    public static void toppleRepose(VoxelRegion r, int x0, int x1, int y0, int y1, int maxSweeps) {
        final int nx = r.nx, ny = r.ny, nz = r.nz;
        int[][] neigh = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            boolean moved = false;
            for (int x = x0; x < x1; x++)
                for (int y = y0; y < y1; y++) {
                    int h = height(r, x, y);
                    if (h <= 0) continue;
                    int topz = h - 1;
                    int tb = r.id[r.idx(x, y, topz)];
                    if (!Material.isLoose(tb)) continue;
                    int bestDh = 0, bx = -1, by = -1;
                    for (int[] d : neigh) {
                        int ax = x + d[0], ay = y + d[1];
                        if (ax < 0 || ay < 0 || ax >= nx || ay >= ny) continue;
                        int dh = h - height(r, ax, ay);
                        if (dh > bestDh) { bestDh = dh; bx = ax; by = ay; }
                    }
                    if (bx < 0 || bestDh <= REPOSE_DEFAULT) continue;
                    int dz = height(r, bx, by);
                    if (dz < nz && r.id[r.idx(bx, by, dz)] == Material.AIR) {
                        int from = r.idx(x, y, topz), to = r.idx(bx, by, dz);
                        r.id[to] = tb; r.state[to] = r.state[from]; r.resist[to] = r.resist[from];
                        r.id[from] = Material.AIR; r.state[from] = Palette.AIR; r.resist[from] = 0f;
                        moved = true;
                    }
                }
            if (!moved) break;
        }
    }

    private static int height(VoxelRegion r, int x, int y) {
        for (int z = r.nz - 1; z >= 0; z--) {
            if (Material.isSolid(r.id[r.idx(x, y, z)])) return z + 1;
        }
        return 0;
    }

    private static boolean isBarrier(int id) {
        return Material.isSolid(id) && !Material.isLoose(id); // struct / brittle / unbreakable
    }
}
