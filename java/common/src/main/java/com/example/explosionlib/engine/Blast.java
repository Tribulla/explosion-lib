package com.example.explosionlib.engine;

import java.util.ArrayList;
import java.util.List;

import static com.example.explosionlib.engine.ExplosionConfig.*;

public final class Blast {
    private Blast() {}

    static final double[][] RAY_DIRS = buildRayDirs();

    private static double[][] buildRayDirs() {
        List<double[]> dirs = new ArrayList<>();
        for (int gx = 0; gx < 16; gx++)
            for (int gy = 0; gy < 16; gy++)
                for (int gz = 0; gz < 16; gz++) {
                    if (gx == 0 || gx == 15 || gy == 0 || gy == 15 || gz == 0 || gz == 15) {
                        double dx = gx / 15.0 * 2 - 1, dy = gy / 15.0 * 2 - 1, dz = gz / 15.0 * 2 - 1;
                        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (n > 0) dirs.add(new double[]{dx / n, dy / n, dz / n});
                    }
                }
        return dirs.toArray(new double[0][]);
    }

    public static final class Result {
        public double r0;
        public double openness;
        public final List<int[]> debrisStruct = new ArrayList<>(); // local (x,y,z) of destroyed struct
        public final List<int[]> rim = new ArrayList<>();          // local (x,y) columns
        public final List<int[]> ejecta = new ArrayList<>();       // local (x,y) columns
    }

    static double overpressure(double z) {
        z = Math.max(z, 1e-3);
        double ps = 0.975 / z + 1.455 / (z * z) + 5.85 / (z * z * z) - 0.019;
        double near = 6.7 / (z * z * z) + 1.0;
        return ps > 10.0 ? near : ps;
    }

    public static Result detonate(VoxelRegion r, int cx, int cy, int cz, ExplosionConfig cfg) {
        final Noise.ValueNoise3D noise = new Noise.ValueNoise3D(cfg.seed);
        final Noise.DetRandom rng = new Noise.DetRandom(cfg.seed);
        final Result out = new Result();

        double w = Math.max(cfg.yield, 1e-6);
        double wcr = Math.cbrt(w);
        double r0 = K_CRATER * wcr;
        r0 *= (1.0 + rng.nextGaussian() * GLOBAL_RADIUS_JITTER);
        r0 = Math.max(r0, 1.0);
        r0 = Math.min(r0, MAX_RADIUS);
        double power = clamp(r0 / POWER_DIVISOR, POWER_MIN, POWER_MAX);
        out.r0 = r0;

        int solidAbove = 0;
        int zTop = Math.min(cz + 1 + (int) (r0 * 1.5) + 1, r.nz);
        for (int z = cz + 1; z < zTop; z++) {
            if (r.inBounds(cx, cy, z) && Material.isSolid(r.id[r.idx(cx, cy, z)])) solidAbove++;
        }
        double openness = clamp(1.0 - solidAbove / Math.max(r0, 1.0), 0.0, 1.0);
        out.openness = openness;

        boolean[] destroy = new boolean[r.id.length];

        for (double[] base : RAY_DIRS) {
            double dx = base[0] + rng.nextGaussian() * RAY_JITTER_SIGMA;
            double dy = base[1] + rng.nextGaussian() * RAY_JITTER_SIGMA;
            double dz = base[2] + rng.nextGaussian() * RAY_JITTER_SIGMA;
            double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (n == 0) continue;
            dx /= n; dy /= n; dz /= n;
            double intensity = power * (INTENSITY_LO + rng.nextDouble() * (INTENSITY_HI - INTENSITY_LO));
            double px = cx + 0.5, py = cy + 0.5, pz = cz + 0.5;
            while (intensity > 0.0) {
                int ix = (int) Math.floor(px), iy = (int) Math.floor(py), iz = (int) Math.floor(pz);
                if (!r.inBounds(ix, iy, iz)) break;
                int i = r.idx(ix, iy, iz);
                int role = r.id[i];
                if (role != Material.AIR) {
                    double res = r.resist[i] * resMult(r, noise, ix, iy, iz);
                    intensity -= (res + 0.3) * 0.3;
                    if (intensity > 0.0 && Material.isDestructible(role)) destroy[i] = true;
                }
                px += dx * RAY_STEP; py += dy * RAY_STEP; pz += dz * RAY_STEP;
                intensity -= AIR_DECAY;
            }
        }

        int half = (int) Math.ceil(r0 * EJECTA_OUTER * (1.0 + CRATER_ANISOTROPY)) + 2;
        int x0 = Math.max(cx - half, 0), x1 = Math.min(cx + half + 1, r.nx);
        int y0 = Math.max(cy - half, 0), y1 = Math.min(cy + half + 1, r.ny);
        int z0 = Math.max(cz - half, 0), z1 = Math.min(cz + half + 1, r.nz);
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++) {
                    double offx = x - cx, offy = y - cy, offz = z - cz;
                    double dd = Math.sqrt(offx * offx + offy * offy + offz * offz);
                    double safe = Math.max(dd, 1e-9);
                    double reff = r0 * (1.0 + CRATER_ANISOTROPY *
                        noise.fbm(offx / safe * CRATER_NOISE_FREQ, offy / safe * CRATER_NOISE_FREQ,
                                  offz / safe * CRATER_NOISE_FREQ, 4));
                    reff = Math.max(reff, 1e-3);
                    double vrad = Math.max(reff * CRATER_VERTICAL, 1e-3);
                    double ell = (offx * offx + offy * offy) / (reff * reff) + (offz / vrad) * (offz / vrad);
                    int i = r.idx(x, y, z);
                    int role = r.id[i];
                    if (!Material.isDestructible(role)) continue;
                    boolean inCore = ell < CORE_FRAC * CORE_FRAC;
                    if (inCore) {
                        destroy[i] = true;
                    } else if (ell < 1.0) {
                        double resV = r.resist[i] * resMult(r, noise, x, y, z);
                        if (r.resist[i] < CRATER_PROOF_RESIST || overpressure(dd / wcr) > resV) destroy[i] = true;
                    }
                }

        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++) {
                double offx = x - cx, offy = y - cy;
                double dxy = Math.sqrt(offx * offx + offy * offy);
                double h2 = Noise.hash01(r.ox + x, r.oy + y, 0, cfg.seed);
                if (dxy >= r0 * 0.85 && dxy < r0 * RIM_FRAC && h2 < RIM_PROB * openness) {
                    out.rim.add(new int[]{x, y});
                } else if (dxy >= r0 * RIM_FRAC && dxy < r0 * EJECTA_OUTER) {
                    double t2 = clamp(Math.pow(r0 / Math.max(dxy, 1e-9), 3), 0.0, 1.0);
                    if (h2 < t2 * EJECTA_DENSITY * openness) out.ejecta.add(new int[]{x, y});
                }
            }

        for (int i = 0; i < destroy.length; i++) {
            if (destroy[i] && Material.isStruct(r.id[i]) && r.id[i] != Material.UNBREAKABLE) {
                int z = i % r.nz, y = (i / r.nz) % r.ny, x = i / (r.nz * r.ny);
                out.debrisStruct.add(new int[]{x, y, z});
            }
        }
        for (int i = 0; i < destroy.length; i++) {
            if (destroy[i] && r.id[i] != Material.UNBREAKABLE) { r.id[i] = Material.AIR; r.state[i] = Palette.AIR; }
        }

        if (cfg.shockwave) applyShockwave(r, noise, cx, cy, cz, r0, openness, cfg.seed);

        if (cfg.scorch) applyScorch(r, cx, cy, cz, r0, cfg.seed);

        return out;
    }

    private static void applyShockwave(VoxelRegion r, Noise.ValueNoise3D noise,
                                       int cx, int cy, int cz, double r0, double openness, long seed) {
        double rs = r0 * SHOCKWAVE_RADIUS;
        double span = Math.max(rs - r0, 1e-6);
        int half = (int) Math.ceil(rs) + 1;
        int x0 = Math.max(cx - half, 0), x1 = Math.min(cx + half + 1, r.nx);
        int y0 = Math.max(cy - half, 0), y1 = Math.min(cy + half + 1, r.ny);
        int z0 = Math.max(cz - half, 0), z1 = Math.min(cz + half + 1, r.nz);
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++) {
                    int i = r.idx(x, y, z);
                    int role = r.id[i];
                    if (!Material.isSolid(role) || role == Material.UNBREAKABLE) continue;
                    if (!exposed(r, x, y, z)) continue;
                    double offx = x - cx, offy = y - cy, offz = z - cz;
                    double dd = Math.sqrt(offx * offx + offy * offy + offz * offz);
                    double intensity = clamp((rs - dd) / span, 0.0, 1.0);
                    if (intensity <= 0.0 || dd <= r0 * 0.5) continue;
                    double h = Noise.hash01(r.ox + x, r.oy + y, r.oz + z, seed ^ 0x5BF03L);
                    if (Material.isBrittle(role)) {
                        if (h < clamp(intensity * openness * SHOCKWAVE_SHATTER_RATE, 0.0, 1.0)) {
                            r.id[i] = Material.AIR; r.state[i] = Palette.AIR;
                        }
                    } else if (Material.isStruct(role)) {   // rigid blocks crack; loose/fluid are left alone
                        double fragility = clamp(SHOCKWAVE_TOUGHNESS_REF / Math.max(r.resist[i], 1e-6), 0.0, 1.0);
                        if (fragility < 0.05) fragility = 0.0;
                        if (h < clamp(intensity * fragility * openness * SHOCKWAVE_CRACK_RATE, 0.0, 1.0)) {
                            r.id[i] = Material.RUBBLE; r.state[i] = Palette.RUBBLE;
                        }
                    }
                }
    }

    private static void applyScorch(VoxelRegion r, int cx, int cy, int cz, double r0, long seed) {
        int half = (int) Math.ceil(r0 * RIM_FRAC * 1.1) + 1;
        int x0 = Math.max(cx - half, 0), x1 = Math.min(cx + half + 1, r.nx);
        int y0 = Math.max(cy - half, 0), y1 = Math.min(cy + half + 1, r.ny);
        int z0 = Math.max(cz - half, 0), z1 = Math.min(cz + half + 1, r.nz);
        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++) {
                    int i = r.idx(x, y, z);
                    int role = r.id[i];
                    if (role != Material.STRUCT) continue;   // only rigid, non-brittle, non-rubble survivors
                    double offx = x - cx, offy = y - cy, offz = z - cz;
                    double dd = Math.sqrt(offx * offx + offy * offy + offz * offz);
                    if (!(dd > r0 * CORE_FRAC && dd < r0 * RIM_FRAC * 1.05)) continue;
                    if (!exposed(r, x, y, z)) continue;
                    if (Noise.hash01(r.ox + x, r.oy + y, r.oz + z, seed ^ 0x5C0CL) < SCORCH_PROB) {
                        r.id[i] = Material.SCORCHED; r.state[i] = Palette.SCORCHED;
                    }
                }
    }

    private static boolean exposed(VoxelRegion r, int x, int y, int z) {
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1], nz = z + o[2];
            if (!r.inBounds(nx, ny, nz)) return true;            // region edge counts as exposed
            if (r.id[r.idx(nx, ny, nz)] == Material.AIR) return true;
        }
        return false;
    }

    private static double resMult(VoxelRegion r, Noise.ValueNoise3D noise, int x, int y, int z) {
        return 1.0 + RES_NOISE_AMP * noise.sample(
            (r.ox + x + 0.5) * RES_NOISE_FREQ,
            (r.oy + y + 0.5) * RES_NOISE_FREQ,
            (r.oz + z + 0.5) * RES_NOISE_FREQ);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
