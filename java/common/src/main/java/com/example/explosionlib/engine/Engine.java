package com.example.explosionlib.engine;

import com.example.explosionlib.Constants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

import static com.example.explosionlib.engine.ExplosionConfig.*;

public final class Engine {
    private Engine() {}

    private static final ExecutorService WORKER = Executors.newFixedThreadPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
        run -> { Thread t = new Thread(run, "explosionlib-worker"); t.setDaemon(true); return t; });

    public static void explode(ServerLevel level, BlockPos origin, ExplosionConfig cfg, Entity cause) {
        double r0est = Math.min(K_CRATER * Math.cbrt(Math.max(cfg.yield, 1e-6)), MAX_RADIUS);
        int half = (int) Math.ceil(r0est * SHOCKWAVE_RADIUS * 1.1) + 6;
        half = Math.min(half, MAX_REGION_HALF);                 // bound memory/CPU for giant yields
        half = Math.max(half, (int) Math.ceil(r0est) + 8);      // but always cover the full crater

        List<long[]> forced = ChunkForce.force(level, origin, half);
        VoxelRegion r = WorldAdapter.capture(level, origin, half);
        int cx = origin.getX() - r.ox;   // engine x  <- world X
        int cy = origin.getZ() - r.oy;   // engine y  <- world Z
        int cz = origin.getY() - r.oz;   // engine z  <- world Y (vertical)
        MinecraftServer server = level.getServer();

        if (cfg.entityDamage) applyEntityEffects(level, origin, r0est, cause);
        Vec3 c = Vec3.atCenterOf(origin);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.9f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);

        WORKER.execute(() -> {
            try {
                Blast.Result res = Blast.detonate(r, cx, cy, cz, cfg);
                runPasses(r, res, cfg, cx, cy, cz);
                List<ExplosionScheduler.Edit> edits = diff(r);
                server.execute(() -> ExplosionScheduler.schedule(level, origin, edits, forced));
            } catch (Throwable t) {
                Constants.LOG.error("[explosionlib] explosion compute failed", t);
                server.execute(() -> ChunkForce.release(level, forced));
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

    private static void runPasses(VoxelRegion r, Blast.Result res, ExplosionConfig cfg, int cx, int cy, int cz) {
        int margin = (int) Math.ceil(res.r0 * SHOCKWAVE_RADIUS) + 30;
        int wx0 = Math.max(cx - margin, 0), wx1 = Math.min(cx + margin + 1, r.nx);
        int wy0 = Math.max(cy - margin, 0), wy1 = Math.min(cy + margin + 1, r.ny);
        int dm = (int) Math.ceil(res.r0 * EJECTA_OUTER) + 6;
        int dx0 = Math.max(cx - dm, 0), dx1 = Math.min(cx + dm + 1, r.nx);
        int dy0 = Math.max(cy - dm, 0), dy1 = Math.min(cy + dm + 1, r.ny);

        if (cfg.gravity) Collapse.structuralCollapse(r, COLLAPSE_ITERS);
        if (cfg.debris) applyDebris(r, res, cfg, cx, cy, cz);
        if (cfg.gravity) Settle.granularSettle(r, wx0, wx1, wy0, wy1);
        Collapse.despeckle(r, dx0, dx1, dy0, dy1, DESPECKLE_ITERS);
        if (cfg.gravity) {
            Collapse.structuralCollapse(r, 3);
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

    private static void applyDebris(VoxelRegion r, Blast.Result res, ExplosionConfig cfg, int cx, int cy, int cz) {
        Noise.DetRandom rng = new Noise.DetRandom(cfg.seed * 2654435761L);
        List<int[]> coords = res.debrisStruct;
        int k = (int) Math.round(DEBRIS_FRACTION * res.openness * coords.size());
        k = Math.min(k, DEBRIS_MAX_PARTICLES);

        if (k > 0 && !coords.isEmpty()) {
            int[] order = new int[coords.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            for (int i = order.length - 1; i > 0; i--) { // deterministic Fisher-Yates
                int j = rng.nextInt(i + 1);
                int t = order[i]; order[i] = order[j]; order[j] = t;
            }
            double cfx = cx + 0.5, cfy = cy + 0.5, cfz = cz + 0.5;
            for (int s = 0; s < k; s++) {
                int[] c = coords.get(order[s]);
                double px = c[0] + 0.5, py = c[1] + 0.5, pz = c[2] + 0.5;
                double ox = px - cfx, oy = py - cfy, oz = pz - cfz;
                double n = Math.sqrt(ox * ox + oy * oy + oz * oz);
                double dx, dy, dz;
                if (n < 1e-6) { dx = 0; dy = 0; dz = 1; } else { dx = ox / n; dy = oy / n; dz = oz / n; }
                double vx = dx * DEBRIS_SPEED + rng.nextGaussian() * DEBRIS_SPEED * DEBRIS_SCATTER;
                double vy = dy * DEBRIS_SPEED + rng.nextGaussian() * DEBRIS_SPEED * DEBRIS_SCATTER;
                double vz = dz * DEBRIS_SPEED + DEBRIS_SPEED * UP_BIAS + rng.nextGaussian() * DEBRIS_SPEED * DEBRIS_SCATTER;
                for (int step = 0; step < DEBRIS_MAX_STEPS; step++) {
                    vz -= DEBRIS_GRAVITY * DEBRIS_DT;
                    px += vx * DEBRIS_DT; py += vy * DEBRIS_DT; pz += vz * DEBRIS_DT;
                    int ix = (int) Math.round(px), iy = (int) Math.round(py);
                    if (ix < 0 || iy < 0 || ix >= r.nx || iy >= r.ny) break; // off the region -> lost
                    int land = firstSolidAbove(r, ix, iy);
                    if (pz <= land) { deposit(r, ix, iy, land); break; }
                }
            }
        }
        for (int[] c : res.rim) deposit(r, c[0], c[1], firstSolidAbove(r, c[0], c[1]));
        for (int[] c : res.ejecta) deposit(r, c[0], c[1], firstSolidAbove(r, c[0], c[1]));
    }

    private static int firstSolidAbove(VoxelRegion r, int x, int y) {
        for (int z = r.nz - 1; z >= 0; z--) {
            if (Material.isSolid(r.id[r.idx(x, y, z)])) return z + 1;
        }
        return 0;
    }

    private static void deposit(VoxelRegion r, int x, int y, int land) {
        if (land < 0 || land >= r.nz) return;
        int i = r.idx(x, y, land);
        if (r.id[i] == Material.AIR) {
            r.id[i] = Material.RUBBLE;
            r.state[i] = Palette.RUBBLE;
            r.resist[i] = (float) Palette.RUBBLE.getBlock().getExplosionResistance();
        }
    }
}
