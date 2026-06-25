package com.example.explosionlib.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldAdapter {
    private WorldAdapter() {}

    public static VoxelRegion capture(ServerLevel level, BlockPos origin, int half) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int yLo = Math.max(origin.getY() - half, minY);
        int yHi = Math.min(origin.getY() + half, maxY);

        int ox = origin.getX() - half;   // world X of engine x=0
        int oy = origin.getZ() - half;   // world Z of engine y=0
        int oz = yLo;                    // world Y of engine z=0 (vertical)
        int nx = 2 * half + 1, ny = 2 * half + 1, nz = yHi - yLo + 1;

        VoxelRegion r = new VoxelRegion(nx, ny, nz, ox, oy, oz);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int ex = 0; ex < nx; ex++)
            for (int ey = 0; ey < ny; ey++)
                for (int ez = 0; ez < nz; ez++) {
                    p.set(ox + ex, oz + ez, oy + ey);
                    BlockState s = level.getBlockState(p);
                    int i = r.idx(ex, ey, ez);
                    r.orig[i] = s;
                    r.state[i] = s;
                    int role = classify(s);
                    r.id[i] = role;
                    if (role == Material.AIR) r.resist[i] = 0f;
                    else if (role == Material.FLUID) r.resist[i] = ExplosionConfig.FLUID_RESIST;
                    else r.resist[i] = (float) resistanceOf(s);
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
