package com.example.explosionlib.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class OuterShockwave {
    private OuterShockwave() {}

    private static final int WRITE_FLAGS =
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int SCAN_DEPTH = 48;   // how far below a column's top to look (capped; canopies are shallow)

    private static final List<Wave> ACTIVE = new ArrayList<>();

    private static final class Wave {
        final ServerLevel level;
        final int cx, cy, cz;            // world center
        final double cxf, cyf, czf;      // world center + 0.5
        final double r0, rOuter, openness;
        final long seed;
        int d;                           // current horizontal expansion radius

        Wave(ServerLevel level, BlockPos center, double r0, int rInner, double openness, long seed) {
            this.level = level;
            this.cx = center.getX(); this.cy = center.getY(); this.cz = center.getZ();
            this.cxf = cx + 0.5; this.cyf = cy + 0.5; this.czf = cz + 0.5;
            this.r0 = r0;
            this.rOuter = r0 * ExplosionConfig.SHOCKWAVE_RADIUS;
            this.openness = openness;
            this.seed = seed;
            this.d = rInner;             // start where the in-region shockwave left off
        }
    }

    public static void start(ServerLevel level, BlockPos center, double r0, int regionHalf, double openness, long seed) {
        int rOuter = (int) Math.ceil(r0 * ExplosionConfig.SHOCKWAVE_RADIUS);
        if (rOuter <= regionHalf) return;   // the in-region shockwave already covered the full reach
        ACTIVE.add(new Wave(level, center, r0, regionHalf, openness, seed));
    }

    public static void tick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        int budget = ExplosionConfig.OUTER_SHOCKWAVE_BUDGET;   // columns visited/tick, shared across waves
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        Iterator<Wave> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            if (w.level != level) continue;
            while (w.d < w.rOuter && budget > 0) {
                budget -= processRing(w, p);
                w.d++;
            }
            if (w.d >= w.rOuter) it.remove();
        }
    }

    public static void reset() {
        ACTIVE.clear();
    }

    private static int processRing(Wave w, BlockPos.MutableBlockPos p) {
        int d = w.d;
        long dSq = (long) d * d, dPlus1Sq = (long) (d + 1) * (d + 1);
        int visited = 0;
        for (int dx = -d; dx <= d; dx++) {
            long dx2 = (long) dx * dx;
            if (dx2 >= dPlus1Sq) continue;
            int zHi = (int) Math.sqrt((double) (dPlus1Sq - dx2) - 1e-9);   // farthest dz still inside [d, d+1)
            long loSq = dSq - dx2;
            int zLo = loSq <= 0 ? 0 : (int) Math.ceil(Math.sqrt((double) loSq));
            for (int adz = zLo; adz <= zHi; adz++) {
                visited += visit(w, w.cx + dx, w.cz + adz, p);
                if (adz != 0) visited += visit(w, w.cx + dx, w.cz - adz, p);
            }
        }
        return visited;
    }

    private static int visit(Wave w, int x, int z, BlockPos.MutableBlockPos p) {
        ServerLevel level = w.level;
        if (level.hasChunkAt(x, z)) {                          // never force-load for the outer shockwave
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            double dx = x + 0.5 - w.cxf, dz = z + 0.5 - w.czf;
            double horiz2 = dx * dx + dz * dz;
            int floor = top - 1 - SCAN_DEPTH;
            int solidRun = 0;
            for (int y = top - 1; y >= floor && solidRun < 3; y--) {
                p.set(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.isAir()) { solidRun = 0; continue; }
                if (WorldAdapter.isBrittle(s)) {
                    double dy = y + 0.5 - w.cyf;
                    double d3d = Math.sqrt(horiz2 + dy * dy);
                    double intensity = (w.rOuter - d3d) / Math.max(w.rOuter - w.r0, 1e-6);
                    if (intensity > 0.0) {
                        if (intensity > 1.0) intensity = 1.0;
                        double prob = intensity * w.openness * ExplosionConfig.SHOCKWAVE_SHATTER_RATE;
                        if (Noise.hash01(x, z, y, w.seed ^ 0x5BF03L) < prob) {   // same seed as the in-region wave
                            level.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), WRITE_FLAGS);
                        }
                    }
                    solidRun = 0;
                } else {
                    solidRun++;   // a run of solid non-brittle blocks => below the canopy, stop scanning down
                }
            }
        }
        return 1;
    }
}
