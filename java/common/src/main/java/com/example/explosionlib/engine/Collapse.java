package com.example.explosionlib.engine;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static com.example.explosionlib.engine.ExplosionConfig.*;

public final class Collapse {
    private Collapse() {}

    private static final int[][] N6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static final class Comp {
        int[] vox;
        int minZ;
        boolean anchored;
    }

    public static boolean[] computeFrozen(VoxelRegion r) {
        final int nx = r.nx, ny = r.ny, nz = r.nz, n = r.id.length;
        boolean[] anchored = new boolean[n];
        int[] stack = new int[n];
        int top = 0;
        for (int x = 0; x < nx; x++)
            for (int y = 0; y < ny; y++)
                for (int z = 0; z < nz; z++) {
                    int i = (x * ny + y) * nz + z;
                    if (!Material.isSolid(r.id[i]) || Material.isFluid(r.id[i])) continue;
                    if (x == 0 || x == nx - 1 || y == 0 || y == ny - 1 || z == 0 || z == nz - 1
                            || r.id[i] == Material.UNBREAKABLE) {
                        anchored[i] = true;
                        stack[top++] = i;
                    }
                }
        while (top > 0) {
            int cur = stack[--top];
            int z = cur % nz, y = (cur / nz) % ny, x = cur / (nz * ny);
            for (int[] d : N6) {
                int ax = x + d[0], ay = y + d[1], az = z + d[2];
                if (ax < 0 || ay < 0 || az < 0 || ax >= nx || ay >= ny || az >= nz) continue;
                int ni = (ax * ny + ay) * nz + az;
                if (!anchored[ni] && Material.isSolid(r.id[ni]) && !Material.isFluid(r.id[ni])) {
                    anchored[ni] = true;
                    stack[top++] = ni;
                }
            }
        }
        boolean[] frozen = new boolean[n];
        for (int i = 0; i < n; i++) {
            frozen[i] = Material.isSolid(r.id[i]) && !Material.isFluid(r.id[i]) && !anchored[i];
        }
        return frozen;
    }

    public static void structuralCollapse(VoxelRegion r, int maxIters, boolean[] frozen) {
        final int nx = r.nx, ny = r.ny, nz = r.nz, n = r.id.length;
        int[] label = new int[n];
        int[] stack = new int[n];
        int[] members = new int[n];

        for (int iter = 0; iter < maxIters; iter++) {
            java.util.Arrays.fill(label, -1);
            List<Comp> comps = new ArrayList<>();

            for (int s = 0; s < n; s++) {
                if (label[s] != -1 || !Material.isSolid(r.id[s]) || Material.isFluid(r.id[s])) continue;
                int top = 0, count = 0, minZ = Integer.MAX_VALUE;
                boolean anchored = false;
                stack[top++] = s;
                label[s] = comps.size();
                while (top > 0) {
                    int cur = stack[--top];
                    members[count++] = cur;
                    int z = cur % nz, y = (cur / nz) % ny, x = cur / (nz * ny);
                    if (z < minZ) minZ = z;
                    if (z == 0 || z == nz - 1 || x == 0 || x == nx - 1 || y == 0 || y == ny - 1
                            || r.id[cur] == Material.UNBREAKABLE || (frozen != null && frozen[cur])) {
                        anchored = true;
                    }
                    for (int[] d : N6) {
                        int ax = x + d[0], ay = y + d[1], az = z + d[2];
                        if (ax < 0 || ay < 0 || az < 0 || ax >= nx || ay >= ny || az >= nz) continue;
                        int ni = (ax * ny + ay) * nz + az;
                        if (label[ni] == -1 && Material.isSolid(r.id[ni]) && !Material.isFluid(r.id[ni])) {
                            label[ni] = comps.size();
                            stack[top++] = ni;
                        }
                    }
                }
                Comp c = new Comp();
                c.vox = java.util.Arrays.copyOf(members, count);
                c.minZ = minZ;
                c.anchored = anchored;
                comps.add(c);
            }

            boolean[] occ = new boolean[n];
            List<Comp> floating = new ArrayList<>();
            for (Comp c : comps) {
                if (c.anchored) for (int vi : c.vox) occ[vi] = true;
                else floating.add(c);
            }
            if (floating.isEmpty()) break;
            floating.sort((a, b) -> Integer.compare(a.minZ, b.minZ)); // lowest first

            boolean moved = false;
            for (Comp c : floating) {
                int k = 0;
                outer:
                while (true) {
                    for (int vi : c.vox) {
                        int z = vi % nz, y = (vi / nz) % ny, x = vi / (nz * ny);
                        int below = z - (k + 1);
                        if (below < 0) break outer;
                        if (occ[(x * ny + y) * nz + below]) break outer;
                    }
                    k++;
                }
                if (k > 0) {
                    int[] sid = new int[c.vox.length];
                    BlockState[] sst = new BlockState[c.vox.length];
                    float[] srs = new float[c.vox.length];
                    for (int j = 0; j < c.vox.length; j++) {
                        sid[j] = r.id[c.vox[j]];
                        sst[j] = r.state[c.vox[j]];
                        srs[j] = r.resist[c.vox[j]];
                        r.id[c.vox[j]] = Material.AIR;
                        r.state[c.vox[j]] = Palette.AIR;
                        r.resist[c.vox[j]] = 0f;
                    }
                    for (int j = 0; j < c.vox.length; j++) {
                        int vi = c.vox[j];
                        int z = vi % nz, y = (vi / nz) % ny, x = vi / (nz * ny);
                        int ni = (x * ny + y) * nz + (z - k);
                        r.id[ni] = sid[j];
                        r.state[ni] = sst[j];
                        r.resist[ni] = srs[j];
                        occ[ni] = true;
                    }
                    moved = true;
                } else {
                    for (int vi : c.vox) occ[vi] = true;
                }
            }
            if (!moved) break;
        }
    }

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
