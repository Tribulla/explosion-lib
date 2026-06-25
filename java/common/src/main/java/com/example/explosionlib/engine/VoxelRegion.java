package com.example.explosionlib.engine;

import net.minecraft.world.level.block.state.BlockState;

public final class VoxelRegion {
    public final int nx, ny, nz;
    public final int ox, oy, oz;        // world coords of voxel (0,0,0)

    public final int[] id;              // current role/tag (Material.*), mutated by the pipeline
    public final float[] resist;        // per-voxel explosion resistance
    public final BlockState[] state;    // current block state, permuted/edited by the pipeline
    public final BlockState[] orig;     // captured original state, for diffing on write-back

    public VoxelRegion(int nx, int ny, int nz, int ox, int oy, int oz) {
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.ox = ox; this.oy = oy; this.oz = oz;
        int n = nx * ny * nz;
        this.id = new int[n];
        this.resist = new float[n];
        this.state = new BlockState[n];
        this.orig = new BlockState[n];
    }

    public int idx(int x, int y, int z) {
        return (x * ny + y) * nz + z;
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz;
    }
}
