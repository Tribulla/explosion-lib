package com.example.explosionlib.engine;

public final class ExplosionConfig {
    public float yield = 8.0f;       // kg-TNT-equivalent; crater radius ~ K_CRATER * cbrt(yield)
    public long seed = 0L;           // same seed + same spot -> identical result
    public boolean shockwave = true;
    public boolean scorch = true;
    public boolean entityDamage = true;  // hurt + fling nearby entities

    public ExplosionConfig() {}

    public ExplosionConfig(float yield, long seed) {
        this.yield = yield;
        this.seed = seed;
    }

    public static final long PER_VOXEL_BYTES = 48;             // VoxelRegion arrays + frozen + edits/overhead
    public static final long REGION_BUDGET_BYTES = Runtime.getRuntime().maxMemory() / 4;  // RAM blasts may use at once
    public static final int MAX_REGION_HALF = autoRegionHalf();
    public static final int MAX_RADIUS = Math.max(8, (MAX_REGION_HALF * 7) / 10);
    public static final int MAX_APPLY_BLOCKS_PER_TICK = 40_000;
    public static final int MAX_CLEANUP_OPS_PER_TICK = 64_000;
    public static final int CLEANUP_MAX_OPS = 2_000_000;       // safety cap only; real cleanups are far smaller now
    public static final int OUTER_SHOCKWAVE_BUDGET = 8_000;

    private static int autoRegionHalf() {
        int desired = 144;                                      // covers yield ~36k at the default craterScale
        long voxels = Math.max(1, REGION_BUDGET_BYTES / PER_VOXEL_BYTES);   // a single region may use the whole budget
        int heapCap = ((int) Math.cbrt((double) voxels) - 1) / 2;
        return Math.max(24, Math.min(desired, heapCap));
    }

    public static long regionBytes(int half) {
        long side = 2L * half + 1;
        return side * side * side * PER_VOXEL_BYTES;
    }

    public static int EXPANSION_SPEED = 3;     // rings of destruction applied per tick, per blast (playout speed)
    public static double K_CRATER = 4.0;       // crater radius ~ K_CRATER * cbrt(yield)

    public static boolean ALLOW_SHOCKWAVE = true;
    public static boolean ALLOW_SCORCH = true;
    public static boolean ALLOW_ENTITY_DAMAGE = true;

    public static final double UNBREAKABLE_RESIST = 50_000.0; // >= this -> treated as bedrock-tier
    public static final float FLUID_RESIST = 0.5f;       // water/lava: low so blasts clear them
    public static final double POWER_DIVISOR = 1.3;
    public static final double POWER_MIN = 1.0, POWER_MAX = 12.0;
    public static final double RAY_STEP = 0.3;
    public static final double AIR_DECAY = 0.22500001;   // EXACT Minecraft air-drag literal
    public static final double INTENSITY_LO = 0.6, INTENSITY_HI = 1.4;
    public static final double RAY_JITTER_SIGMA = 0.06;
    public static final double GLOBAL_RADIUS_JITTER = 0.08;
    public static final double RES_NOISE_AMP = 0.5, RES_NOISE_FREQ = 0.7;
    public static final double CRATER_ANISOTROPY = 0.15, CRATER_NOISE_FREQ = 3.0;
    public static final double CRATER_VERTICAL = 0.6;
    public static final double CORE_FRAC = 0.6, RIM_FRAC = 1.15, EJECTA_OUTER = 1.8;
    public static final double CRATER_PROOF_RESIST = 60.0;
    public static final int DESPECKLE_ITERS = 2;
    public static double SCORCH_PROB = 0.6;            // configurable

    public static double ENTITY_RADIUS = 1.5;          // configurable; entities within this * R0 are hit
    public static double ENTITY_KNOCKBACK = 2.5;       // configurable; fling strength multiplier
    public static final double ENTITY_UPWARD = 0.5;    // extra upward launch added to the knockback dir

    public static double SHOCKWAVE_RADIUS = 3.5;       // configurable
    public static final double SHOCKWAVE_TOUGHNESS_REF = 6.0;
    public static double SHOCKWAVE_CRACK_RATE = 0.2;   // configurable
    public static final double SHOCKWAVE_SHATTER_RATE = 1.5;
}
