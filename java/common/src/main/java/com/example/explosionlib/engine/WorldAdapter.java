package com.example.explosionlib.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.HashMap;
import java.util.Map;

public final class WorldAdapter {
    private WorldAdapter() {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public static final class Snapshot {
        public final int nx, ny, nz;
        public final int ox, oy, oz;   // world coords of voxel (0,0,0); see VoxelRegion
        final int sy0;                 // section-Y of the lowest captured section
        final Map<Long, PalettedContainer<BlockState>[]> chunks;

        Snapshot(int nx, int ny, int nz, int ox, int oy, int oz, int sy0,
                 Map<Long, PalettedContainer<BlockState>[]> chunks) {
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.sy0 = sy0; this.chunks = chunks;
        }
    }

    @SuppressWarnings("unchecked")
    public static Snapshot snapshot(ServerLevel level, BlockPos origin, int half) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int yLo = Math.max(origin.getY() - half, minY);
        int yHi = Math.min(origin.getY() + half, maxY);

        int ox = origin.getX() - half;   // world X of engine x=0
        int oy = origin.getZ() - half;   // world Z of engine y=0
        int oz = yLo;                    // world Y of engine z=0 (vertical)
        int nx = 2 * half + 1, ny = 2 * half + 1, nz = yHi - yLo + 1;

        int wx1 = ox + nx - 1;           // inclusive world-X max
        int wz1 = oy + ny - 1;           // inclusive world-Z max
        int sy0 = yLo >> 4, sy1 = yHi >> 4;
        int nSec = sy1 - sy0 + 1;

        Map<Long, PalettedContainer<BlockState>[]> chunks = new HashMap<>();
        for (int cx = ox >> 4; cx <= (wx1 >> 4); cx++) {
            for (int cz = oy >> 4; cz <= (wz1 >> 4); cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);   // force-loaded already; loads/generates if needed
                LevelChunkSection[] secs = chunk.getSections();
                PalettedContainer<BlockState>[] copies = null;
                for (int sy = sy0; sy <= sy1; sy++) {
                    int arrIdx = level.getSectionIndex(sy << 4);
                    if (arrIdx < 0 || arrIdx >= secs.length) continue;
                    LevelChunkSection sec = secs[arrIdx];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    if (copies == null) copies = new PalettedContainer[nSec];
                    copies[sy - sy0] = sec.getStates().copy();
                }
                if (copies != null) chunks.put(ChunkPos.asLong(cx, cz), copies);
            }
        }
        return new Snapshot(nx, ny, nz, ox, oy, oz, sy0, chunks);
    }

    public static VoxelRegion decode(Snapshot s) {
        VoxelRegion r = new VoxelRegion(s.nx, s.ny, s.nz, s.ox, s.oy, s.oz);
        for (int ex = 0; ex < s.nx; ex++) {
            int worldX = s.ox + ex;
            int cx = worldX >> 4, lx = worldX & 15;
            for (int ey = 0; ey < s.ny; ey++) {
                int worldZ = s.oy + ey;
                int cz = worldZ >> 4, lz = worldZ & 15;
                PalettedContainer<BlockState>[] secs = s.chunks.get(ChunkPos.asLong(cx, cz));
                for (int ez = 0; ez < s.nz; ez++) {
                    int worldY = s.oz + ez;
                    BlockState st;
                    if (secs == null) {
                        st = AIR;
                    } else {
                        PalettedContainer<BlockState> c = secs[(worldY >> 4) - s.sy0];
                        st = (c == null) ? AIR : c.get(lx, worldY & 15, lz);
                    }
                    int i = r.idx(ex, ey, ez);
                    r.orig[i] = st;
                    r.state[i] = st;
                    int role = classify(st);
                    r.id[i] = role;
                    if (role == Material.AIR) r.resist[i] = 0f;
                    else if (role == Material.FLUID) r.resist[i] = ExplosionConfig.FLUID_RESIST;
                    else r.resist[i] = (float) resistanceOf(st);
                }
            }
        }
        return r;
    }

    static int classify(BlockState s) {
        if (s.isAir()) return Material.AIR;
        Block b = s.getBlock();
        if (b instanceof LiquidBlock) return Material.FLUID;   // water/lava: blasts clear them
        if (s.canBeReplaced()) return Material.AIR;             // grass, flowers, snow layer, etc.
        double res = resistanceOf(s);
        if (res >= ExplosionConfig.UNBREAKABLE_RESIST) return Material.UNBREAKABLE;
        if (b instanceof FallingBlock) return Material.LOOSE;
        if (isBrittle(s)) return Material.BRITTLE;
        return Material.STRUCT;
    }

    static double resistanceOf(BlockState s) {
        return s.getBlock().getExplosionResistance();          // no-arg overload; the rich one needs an Explosion
    }

    static boolean isBrittle(BlockState s) {
        if (s.is(BlockTags.LEAVES)) return true;
        Block b = s.getBlock();
        if (b instanceof HalfTransparentBlock) return true;    // glass / stained / tinted / ice / honey / slime
        if (b instanceof IronBarsBlock) return true;           // iron bars + glass panes (GlassPaneBlock extends it)
        return b == Blocks.GLOWSTONE || b == Blocks.SEA_LANTERN;
    }
}
