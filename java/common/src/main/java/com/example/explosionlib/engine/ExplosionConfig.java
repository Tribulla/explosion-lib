package com.example.explosionlib.engine;

public final class ExplosionConfig {
    public float yield = 8.0f;       // kg-TNT-equivalent; crater radius ~ K_CRATER * cbrt(yield)
    public long seed = 0L;           // same seed + same spot -> identical result
    public boolean shockwave = true;
    public boolean gravity = true;   // structural collapse + granular settle
    public boolean debris = true;    // leave cracked rubble (gravel) where the shockwave breaks structural blocks
    public boolean scorch = true;
    public boolean entityDamage = true;  // hurt + fling nearby entities

    public ExplosionConfig() {}

    public ExplosionConfig(float yield, long seed) {
        this.yield = yield;
        this.seed = seed;
    }

    public static final int MAX_RADIUS = 40;             // hard cap on crater radius R0 (yield ~1024 reaches this)
    public static final int MAX_REGION_HALF = 80;        // hard cap on captured half-extent (bounds memory/CPU)
    public static final double UNBREAKABLE_RESIST = 50_000.0; // >= this -> treated as bedrock-tier
    public static final float FLUID_RESIST = 0.5f;       // water/lava: low so blasts clear them
    public static final int EXPANSION_SPEED = 3;         // rings (blocks) of destruction applied per server tick, per blast
    public static final int MAX_APPLY_BLOCKS_PER_TICK = 40_000;   // block writes/tick across all active blasts
    public static final int MAX_CLEANUP_OPS_PER_TICK = 20_000;    // support/fluid cleanup steps/tick across all blasts
    public static final int CLEANUP_MAX_OPS = 400_000;            // hard cap on total cleanup steps for one blast

    public static final double K_CRATER = 4.0;
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
    public static final double SCORCH_PROB = 0.6;

    public static final double ENTITY_RADIUS = 1.5;    // entities within this * R0 are hit
    public static final double ENTITY_KNOCKBACK = 2.5; // fling strength multiplier
    public static final double ENTITY_UPWARD = 0.5;    // extra upward launch added to the knockback dir

    public static final double SHOCKWAVE_RADIUS = 3.5;
    public static final double SHOCKWAVE_TOUGHNESS_REF = 6.0;
    public static final double SHOCKWAVE_CRACK_RATE = 0.2;
    public static final double SHOCKWAVE_SHATTER_RATE = 1.5;

    public static final int COLLAPSE_ITERS = 4;
    public static final int REPOSE_SWEEPS = 200;
    public static final int REPOSE_DEFAULT = 1;
}
