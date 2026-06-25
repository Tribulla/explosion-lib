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

import static com.example.explosionlib.engine.ExplosionConfig.*;

public final class Engine {
    private Engine() {}

    private static final int POOL_SIZE = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(POOL_SIZE,
        run -> { Thread t = new Thread(run, "explosionlib-worker"); t.setDaemon(true); return t; });

    private static final int MAX_INFLIGHT = Math.max(3, POOL_SIZE * 2);
    private static final AtomicInteger INFLIGHT = new AtomicInteger();

    private static final AtomicInteger GENERATION = new AtomicInteger();

    public static void onServerStopping() {
        GENERATION.incrementAndGet();
    }

    public static void explode(ServerLevel level, BlockPos origin, ExplosionConfig cfg, Entity cause) {
        double r0est = Math.min(K_CRATER * Math.cbrt(Math.max(cfg.yield, 1e-6)), MAX_RADIUS);
        int half = (int) Math.ceil(r0est * SHOCKWAVE_RADIUS * 1.1) + 6;
        half = Math.min(half, MAX_REGION_HALF);                 // bound memory/CPU for giant yields
        half = Math.max(half, (int) Math.ceil(r0est) + 8);      // but always cover the full crater

        if (cfg.entityDamage) applyEntityEffects(level, origin, r0est, cause);
        Vec3 c = Vec3.atCenterOf(origin);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.9f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);

        if (INFLIGHT.get() >= MAX_INFLIGHT) {                   // server already saturated with blasts
            Constants.LOG.warn("[explosionlib] {} explosion jobs in flight (max {}); skipping terrain for this blast",
                INFLIGHT.get(), MAX_INFLIGHT);
            if (cause instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                    Component.literal("Too many explosions at once — terrain skipped, try again in a moment."), true);
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
        INFLIGHT.incrementAndGet();
        WORKER.execute(() -> {
            try {
                VoxelRegion r = WorldAdapter.decode(snap);      // the heavy per-voxel decode, now off-thread
                int cx = origin.getX() - r.ox;                  // engine x  <- world X
                int cy = origin.getZ() - r.oy;                  // engine y  <- world Z
                int cz = origin.getY() - r.oz;                  // engine z  <- world Y (vertical)
                boolean[] frozen = cfg.gravity ? Collapse.computeFrozen(r) : null;
                Blast.Result res = Blast.detonate(r, cx, cy, cz, cfg);
                runPasses(r, res, cfg, cx, cy, cz, frozen);
                List<ExplosionScheduler.Edit> edits = diff(r);
                server.execute(() -> {
                    if (GENERATION.get() != gen) { ChunkForce.release(level, forced); return; }
                    ExplosionScheduler.schedule(level, origin, edits, forced);
                });
            } catch (Throwable t) {
                Constants.LOG.error("[explosionlib] explosion compute failed", t);
                server.execute(() -> ChunkForce.release(level, forced));
            } finally {
                INFLIGHT.decrementAndGet();
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

    private static void runPasses(VoxelRegion r, Blast.Result res, ExplosionConfig cfg, int cx, int cy, int cz,
                                  boolean[] frozen) {
        int margin = (int) Math.ceil(res.r0 * SHOCKWAVE_RADIUS) + 30;
        int wx0 = Math.max(cx - margin, 0), wx1 = Math.min(cx + margin + 1, r.nx);
        int wy0 = Math.max(cy - margin, 0), wy1 = Math.min(cy + margin + 1, r.ny);
        int dm = (int) Math.ceil(res.r0 * EJECTA_OUTER) + 6;
        int dx0 = Math.max(cx - dm, 0), dx1 = Math.min(cx + dm + 1, r.nx);
        int dy0 = Math.max(cy - dm, 0), dy1 = Math.min(cy + dm + 1, r.ny);

        if (cfg.gravity) Collapse.structuralCollapse(r, COLLAPSE_ITERS, frozen);
        if (cfg.gravity) Settle.granularSettle(r, wx0, wx1, wy0, wy1);
        Collapse.despeckle(r, dx0, dx1, dy0, dy1, DESPECKLE_ITERS);
        if (cfg.gravity) {
            Collapse.structuralCollapse(r, 3, frozen);
            Settle.granularSettle(r, wx0, wx1, wy0, wy1);
        }
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

}
