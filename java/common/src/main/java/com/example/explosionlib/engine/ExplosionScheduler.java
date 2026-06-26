package com.example.explosionlib.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public final class ExplosionScheduler {
    private ExplosionScheduler() {}

    private static final int WRITE_FLAGS =
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public record Edit(int x, int y, int z, BlockState state) {}

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final List<Cleanup> CLEANUPS = new ArrayList<>();

    private static final class Active {
        final ServerLevel level;
        final double cx, cy, cz;
        final List<List<Edit>> rings;   // edits grouped by floor(distance from center)
        final List<long[]> forced;      // chunks to release once cleanup finishes
        final List<BlockPos> removed;   // positions cleared to air (seed the support cleanup)
        int next = 0;                   // next ring to apply
        int ringCursor = 0;             // resume index within rings[next] when a tick's budget ran out mid-ring

        Active(ServerLevel level, double cx, double cy, double cz,
               List<List<Edit>> rings, List<long[]> forced, List<BlockPos> removed) {
            this.level = level; this.cx = cx; this.cy = cy; this.cz = cz;
            this.rings = rings; this.forced = forced; this.removed = removed;
        }
    }

    private static final class Cleanup {
        final ServerLevel level;
        final ArrayDeque<BlockPos> queue;
        final HashSet<Long> removedDone = new HashSet<>();
        final HashSet<Long> fluidDone = new HashSet<>();
        final List<long[]> forced;      // released when this cleanup completes
        int remaining = ExplosionConfig.CLEANUP_MAX_OPS;   // hard cap so a pathological cascade can't run forever

        Cleanup(ServerLevel level, List<BlockPos> removed, List<long[]> forced) {
            this.level = level;
            this.queue = new ArrayDeque<>(removed);
            this.forced = forced;
        }
    }

    public static void schedule(ServerLevel level, BlockPos center, List<Edit> edits,
                                List<BlockPos> cleanupSeeds, List<long[]> forced) {
        double cx = center.getX() + 0.5, cy = center.getY() + 0.5, cz = center.getZ() + 0.5;
        if (edits.isEmpty()) {
            ChunkForce.release(level, forced);
            return;
        }
        ACTIVE.add(new Active(level, cx, cy, cz, ringify(edits, cx, cy, cz), forced, cleanupSeeds));
    }

    private static List<List<Edit>> ringify(List<Edit> edits, double cx, double cy, double cz) {
        int maxRing = -1;
        for (Edit e : edits) {
            int d = ringOf(e, cx, cy, cz);
            if (d > maxRing) maxRing = d;
        }
        List<List<Edit>> rings = new ArrayList<>(maxRing + 1);
        for (int i = 0; i <= maxRing; i++) rings.add(new ArrayList<>());
        for (Edit e : edits) rings.get(ringOf(e, cx, cy, cz)).add(e);
        return rings;
    }

    public static void tick(ServerLevel level) {
        if (!ACTIVE.isEmpty()) {
            int budget = ExplosionConfig.MAX_APPLY_BLOCKS_PER_TICK;   // shared across every active blast this tick
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            Iterator<Active> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Active a = it.next();
                if (a.level != level) continue;
                if (budget <= 0) continue;          // out of budget; this blast advances next tick

                int front = -1;
                int ringsThisTick = 0;
                while (a.next < a.rings.size() && ringsThisTick < ExplosionConfig.EXPANSION_SPEED && budget > 0) {
                    List<Edit> ring = a.rings.get(a.next);
                    int i = a.ringCursor;
                    for (; i < ring.size() && budget > 0; i++) {
                        Edit e = ring.get(i);
                        p.set(e.x, e.y, e.z);
                        level.setBlock(p, e.state(), WRITE_FLAGS);
                        budget--;
                    }
                    front = a.next;
                    if (i < ring.size()) {          // budget ran out mid-ring; resume here next tick
                        a.ringCursor = i;
                        break;
                    }
                    a.ringCursor = 0;
                    a.next++;
                    ringsThisTick++;
                }
                if (front >= 0) spawnFront(a, front);
                if (a.next >= a.rings.size()) {     // fully applied -> hand off to amortized cleanup
                    CLEANUPS.add(new Cleanup(a.level, a.removed, a.forced));
                    it.remove();
                }
            }
        }

        tickCleanup(level);
        OuterShockwave.tick(level);   // ripple the shockwave out past the captured region (live world)
    }

    private static void tickCleanup(ServerLevel level) {
        if (CLEANUPS.isEmpty()) return;
        int budget = ExplosionConfig.MAX_CLEANUP_OPS_PER_TICK;       // shared across every active cleanup this tick
        BlockPos.MutableBlockPos np = new BlockPos.MutableBlockPos();
        Iterator<Cleanup> it = CLEANUPS.iterator();
        while (it.hasNext()) {
            Cleanup cu = it.next();
            if (cu.level != level) continue;
            if (budget <= 0) continue;

            while (!cu.queue.isEmpty() && budget > 0 && cu.remaining > 0) {
                BlockPos p = cu.queue.poll();
                for (Direction d : Direction.values()) {
                    np.setWithOffset(p, d);
                    BlockState s = level.getBlockState(np);
                    if (s.isAir()) continue;

                    FluidState fs = s.getFluidState();
                    if (!fs.isEmpty() && cu.fluidDone.add(np.asLong())) {
                        level.scheduleTick(np.immutable(), fs.getType(), fs.getType().getTickDelay(level));
                    }

                    if (!s.canSurvive(level, np) && cu.removedDone.add(np.asLong())) {
                        BlockPos rp = np.immutable();
                        level.setBlock(rp, Blocks.AIR.defaultBlockState(), WRITE_FLAGS);
                        cu.queue.add(rp);   // its removal may unsupport / un-dam further blocks
                    }
                }
                budget--;
                cu.remaining--;
            }

            if (cu.queue.isEmpty() || cu.remaining <= 0) {   // done (or hit the safety cap) -> release chunks
                ChunkForce.release(cu.level, cu.forced);
                it.remove();
            }
        }
    }

    public static void reset() {
        ACTIVE.clear();
        CLEANUPS.clear();
        OuterShockwave.reset();
    }

    private static void spawnFront(Active a, int radius) {
        if (radius < 1) return;
        var rng = a.level.getRandom();
        int puffs = Math.min(6 + radius / 2, 24);
        for (int k = 0; k < puffs; k++) {
            double theta = rng.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);
            double s = Math.sin(phi);
            double px = a.cx + radius * s * Math.cos(theta);
            double py = a.cy + radius * Math.cos(phi);
            double pz = a.cz + radius * s * Math.sin(theta);
            a.level.sendParticles(ParticleTypes.EXPLOSION, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            if ((k & 1) == 0) {
                a.level.sendParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 1, 0.0, 0.02, 0.0, 0.01);
            }
        }
    }

    private static int ringOf(Edit e, double cx, double cy, double cz) {
        double dx = e.x + 0.5 - cx, dy = e.y + 0.5 - cy, dz = e.z + 0.5 - cz;
        return (int) Math.floor(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }
}
