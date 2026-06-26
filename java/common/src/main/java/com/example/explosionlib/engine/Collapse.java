package com.example.explosionlib.engine;

public final class Collapse {
    private Collapse() {}

    private static final int[][] N6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public static void despeckle(VoxelRegion r, int x0, int x1, int y0, int y1, int iters) {
        final int nx = r.nx, ny = r.ny, nz = r.nz;
        int[] remove = new int[256];
        for (int it = 0; it < iters; it++) {
            int rc = 0;
            for (int x = x0; x < x1; x++)
                for (int y = y0; y < y1; y++)
                    for (int z = 0; z < nz; z++) {
                        int i = (x * ny + y) * nz + z;
                        int role = r.id[i];
                        if (!Material.isSolid(role) || role == Material.UNBREAKABLE || Material.isFluid(role)) continue;
                        int total = 0, horiz = 0;
                        for (int[] d : N6) {
                            int ax = x + d[0], ay = y + d[1], az = z + d[2];
                            boolean solid;
                            if (ax < 0 || ay < 0 || az < 0 || ax >= nx || ay >= ny || az >= nz) solid = true;
                            else solid = Material.isSolid(r.id[(ax * ny + ay) * nz + az]);
                            if (solid) { total++; if (d[2] == 0) horiz++; }
                        }
                        if (total <= 1 || horiz == 0) {
                            if (rc == remove.length) remove = java.util.Arrays.copyOf(remove, rc * 2);
                            remove[rc++] = i;
                        }
                    }
            if (rc == 0) break;
            for (int j = 0; j < rc; j++) {
                int i = remove[j];
                r.id[i] = Material.AIR;
                r.state[i] = Palette.AIR;
                r.resist[i] = 0f;
            }
        }
    }
}
