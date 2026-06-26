package com.example.explosionlib.engine;

import com.example.explosionlib.Constants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.explosionlib.engine.ExplosionConfig.*;

public final class Engine {
    private Engine() {}

    private static final int POOL_SIZE = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(POOL_SIZE,
        run -> { Thread t = new Thread(run, "explosionlib-worker"); t.setDaemon(true); return t; });

    private static final AtomicLong INFLIGHT_BYTES = new AtomicLong();

    private static final AtomicInteger GENERATION = new AtomicInteger();

    public static void onServerStopping() {
        GENERATION.incrementAndGet();
    }

    public static void explode(ServerLevel level, BlockPos origin, ExplosionConfig cfg, Entity cause) {
        double r0est = Math.min(K_CRATER * Math.cbrt(Math.max(cfg.yield, 1e-6)), MAX_RADIUS);
        int half = (int) Math.ceil(r0est * SHOCKWAVE_RADIUS * 1.1) + 6;
        half = Math.min(half, MAX_REGION_HALF);                 // bound memory/CPU for giant yields
        half = Math.max(half, (int) Math.ceil(r0est) + 8);      // but always cover the full crater
        final int regionHalf = half;                            // captured (effectively-final) for the worker lambda

        if (cfg.entityDamage) applyEntityEffects(level, origin, r0est, cause);
        Vec3 c = Vec3.atCenterOf(origin);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.9f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);

        long thisBytes = ExplosionConfig.regionBytes(half);
        long inflight = INFLIGHT_BYTES.get();
        if (inflight > 0 && inflight + thisBytes > REGION_BUDGET_BYTES) {
            Constants.LOG.warn("[explosionlib] explosion RAM budget reached ({} MB in flight); skipping terrain for this blast",
                inflight >> 20);
            if (cause instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                    Component.literal("Too many large explosions at once — terrain skipped, try again in a moment."), true);
            }
            return;
        }

        List<long[]> forced = ChunkForce.force(level, origin, half);
        WorldAdapter.Snapshot snap;
        try {
            snap = WorldAdapter.snapshot(level, origin, half);  // cheap section copies, on the server thread
        } catch (Throwable t) {
            Constants.LOG.error("[explosionlib] explosion snapshot failed", t);
            ChunkForce.release(level, forced);
            return;
        }

        MinecraftServer server = level.getServer();
        int gen = GENERATION.get();                            // the server epoch this blast belongs to
        INFLIGHT_BYTES.addAndGet(thisBytes);
        WORKER.execute(() -> {
            try {
                long t0 = System.nanoTime();
                VoxelRegion r = WorldAdapter.decode(snap);      // the heavy per-voxel decode, now off-thread
                long tDecode = System.nanoTime();
                int cx = origin.getX() - r.ox;                  // engine x  <- world X
                int cy = origin.getZ() - r.oy;                  // engine y  <- world Z
                int cz = origin.getY() - r.oz;                  // engine z  <- world Y (vertical)
                Blast.Result res = Blast.detonate(r, cx, cy, cz, cfg);
                long tDet = System.nanoTime();
                runPasses(r, res, cx, cy);
                long tPasses = System.nanoTime();
                List<ExplosionScheduler.Edit> edits = diff(r);
                List<BlockPos> cleanupSeeds = cleanupSeeds(r);   // only the crater surface, not its volume
                long tDiff = System.nanoTime();
                Constants.LOG.info(
                    "[explosionlib] compute {}ms (region {}^3, {} edits): decode={} detonate={} passes={} diff+seeds={}",
                    (tDiff - t0) / 1_000_000, r.nx, edits.size(), (tDecode - t0) / 1_000_000,
                    (tDet - tDecode) / 1_000_000, (tPasses - tDet) / 1_000_000, (tDiff - tPasses) / 1_000_000);
                server.execute(() -> {
                    if (GENERATION.get() != gen) { ChunkForce.release(level, forced); return; }
                    ExplosionScheduler.schedule(level, origin, edits, cleanupSeeds, forced);
                    // The full shockwave reaches past the captured region; fill in that far ring on the live
                    // world (strips foliage / shatters glass out to the true reach, in loaded chunks only).
                    if (cfg.shockwave) {
                        OuterShockwave.start(level, origin, res.r0, regionHalf, res.openness, cfg.seed);
                    }
                });
            } catch (Throwable t) {
                Constants.LOG.error("[explosionlib] explosion compute failed", t);
                server.execute(() -> ChunkForce.release(level, forced));
            } finally {
                INFLIGHT_BYTES.addAndGet(-thisBytes);
            }
        });
    }

    private static void applyEntityEffects(ServerLevel level, BlockPos origin, double r0, Entity cause) {
        double radius = r0 * ENTITY_RADIUS;
        Vec3 center = Vec3.atCenterOf(origin);
        AABB box = new AABB(origin).inflate(radius);
        DamageSource src = level.damageSources().explosion(null, cause);
        for (Entity entity : level.getEntities((Entity) null, box)) {
            double dist = Math.sqrt(entity.distanceToSqr(center));
            if (dist > radius) continue;
            double seen = Explosion.getSeenPercent(center, entity);
            double impact = (1.0 - dist / radius) * seen;
            if (impact <= 0.0) continue;

            entity.hurt(src, (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0));

            Vec3 dir = entity.position().subtract(center);
            if (dir.lengthSqr() < 1e-8) dir = new Vec3(0.0, 1.0, 0.0);
            dir = dir.normalize().add(0.0, ENTITY_UPWARD, 0.0).normalize();
            entity.setDeltaMovement(entity.getDeltaMovement().add(dir.scale(impact * ENTITY_KNOCKBACK)));
            entity.hurtMarked = true;   // sync the new velocity to clients (incl. players)
        }
    }

    private static void runPasses(VoxelRegion r, Blast.Result res, int cx, int cy) {
        int dm = (int) Math.ceil(res.r0 * EJECTA_OUTER) + 6;
        int dx0 = Math.max(cx - dm, 0), dx1 = Math.min(cx + dm + 1, r.nx);
        int dy0 = Math.max(cy - dm, 0), dy1 = Math.min(cy + dm + 1, r.ny);

        long s = System.nanoTime();
        Collapse.despeckle(r, dx0, dx1, dy0, dy1, DESPECKLE_ITERS);
        Constants.LOG.info("[explosionlib]   passes: despeckle={}ms", (System.nanoTime() - s) / 1_000_000);
    }

    private static List<ExplosionScheduler.Edit> diff(VoxelRegion r) {
        List<ExplosionScheduler.Edit> edits = new ArrayList<>();
        for (int ex = 0; ex < r.nx; ex++)
            for (int ey = 0; ey < r.ny; ey++)
                for (int ez = 0; ez < r.nz; ez++) {
                    int i = r.idx(ex, ey, ez);
                    if (r.state[i] != r.orig[i]) {
                        edits.add(new ExplosionScheduler.Edit(r.ox + ex, r.oz + ez, r.oy + ey, r.state[i]));
                    }
                }
        return edits;
    }

    private static List<BlockPos> cleanupSeeds(VoxelRegion r) {
        List<BlockPos> seeds = new ArrayList<>();
        for (int ex = 0; ex < r.nx; ex++)
            for (int ey = 0; ey < r.ny; ey++)
                for (int ez = 0; ez < r.nz; ez++) {
                    if (!clearedToAir(r, r.idx(ex, ey, ez))) continue;
                    if (onRemovedBoundary(r, ex, ey, ez)) {
                        seeds.add(new BlockPos(r.ox + ex, r.oz + ez, r.oy + ey));
                    }
                }
        return seeds;
    }

    private static boolean clearedToAir(VoxelRegion r, int i) {
        return r.state[i] != r.orig[i] && r.state[i].isAir();   // a solid voxel the blast turned to air
    }

    private static boolean onRemovedBoundary(VoxelRegion r, int x, int y, int z) {
        int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : n6) {
            int ax = x + d[0], ay = y + d[1], az = z + d[2];
            if (!r.inBounds(ax, ay, az)) return true;                 // region edge: the world beyond may need cleanup
            if (!clearedToAir(r, r.idx(ax, ay, az))) return true;     // neighbour survived / was air / is a decoration
        }
        return false;
    }

}
