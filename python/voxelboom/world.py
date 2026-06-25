from dataclasses import dataclass, field

import numpy as np

from . import config
from .materials import (
    AIR, BEDROCK, STONE, DIRT, GRASS, SAND, METAL, GLASS, OBSIDIAN, WOOD, LEAVES,
    IS_SOLID,
)
from .noise import ValueNoise3D, fbm, hash01


@dataclass
class World:
    mat: np.ndarray
    spacing: np.ndarray = field(default_factory=lambda: config.SPACING.copy())
    origin: np.ndarray = field(default_factory=lambda: config.ORIGIN.copy())

    def solid_mask(self):
        return IS_SOLID[self.mat]

    def heightmap(self):
        solid = IS_SOLID[self.mat]
        nz = self.mat.shape[2]
        has = solid.any(axis=2)
        top = (nz - 1) - np.argmax(solid[:, :, ::-1], axis=2)
        return np.where(has, top + 1, 0).astype(np.int64)

    def first_solid_above(self, x, y):
        col = IS_SOLID[self.mat[x, y, :]]
        if not col.any():
            return 0
        nz = self.mat.shape[2]
        return int((nz - 1) - np.argmax(col[::-1]) + 1)

    def snapshot(self):
        return self.mat.copy()

    def restore(self, snap):
        self.mat[...] = snap


def _place_box(mat, x0, y0, z0, sx, sy, sz, material, hollow=False, wall=1):
    nx, ny, nz = mat.shape
    x1, y1, z1 = min(x0 + sx, nx), min(y0 + sy, ny), min(z0 + sz, nz)
    x0, y0, z0 = max(x0, 0), max(y0, 0), max(z0, 0)
    sub = mat[x0:x1, y0:y1, z0:z1]
    if not hollow:
        sub[:] = material
        return
    shell = np.ones(sub.shape, dtype=bool)
    if all(d > 2 * wall for d in sub.shape):
        shell[wall:-wall, wall:-wall, wall:-wall] = False  # carve interior -> hollow
    sub[shell] = material


def generate_terrain(shape=None, seed=0):
    if shape is None:
        shape = (config.NX, config.NY, config.NZ)
    nx, ny, nz = shape
    mat = np.zeros(shape, dtype=np.int16)
    noise = ValueNoise3D(seed)

    xs = np.arange(nx)
    ys = np.arange(ny)
    gx, gy = np.meshgrid(xs, ys, indexing="ij")
    coords = np.stack([gx * 0.045, gy * 0.045, np.zeros_like(gx, dtype=float)], axis=-1)
    base = nz * 0.42
    amp = nz * 0.16
    h = base + amp * fbm(noise, coords, octaves=5)
    heights = np.clip(h, 5, nz - 8).astype(np.int64)

    z_idx = np.arange(nz)[None, None, :]
    H = heights[:, :, None]
    solid = z_idx < H
    depth = (H - 1) - z_idx  # 0 at surface, grows downward

    mat[solid] = STONE
    mat[solid & (depth >= 1) & (depth <= 3)] = DIRT
    mat[solid & (depth == 0)] = GRASS

    sand_coords = np.stack([gx * 0.06 + 100.0, gy * 0.06 - 50.0, np.zeros_like(gx, dtype=float)], axis=-1)
    sand_mask2d = fbm(noise, sand_coords, octaves=3) > 0.35
    sand3d = sand_mask2d[:, :, None] & solid & (depth <= 3)
    mat[sand3d] = SAND

    mat[:, :, 0:2] = BEDROCK

    sx = nx // 4
    sy = ny // 4
    ground = int(heights[sx, sy])
    _place_box(mat, sx, sy, ground, 14, 14, 12, OBSIDIAN, hollow=True, wall=1)
    mx, my = int(nx * 0.62), int(ny * 0.30)
    _place_box(mat, mx, my, int(heights[mx, my]), 5, 5, 20, METAL)
    gxr, gyr = int(nx * 0.7), int(ny * 0.7)
    _place_box(mat, gxr, gyr, int(heights[gxr, gyr]), 8, 8, 8, GLASS)
    wx, wy = int(nx * 0.4), int(ny * 0.72)
    _place_box(mat, wx, wy, int(heights[wx, wy]), 16, 2, 14, STONE)
    _place_box(mat, wx, wy, int(heights[wx, wy]) + 14, 16, 2, 2, STONE)  # lintel/overhang

    _scatter_trees(mat, noise, seed)
    return mat


def _column_top(mat, x, y):
    col = IS_SOLID[mat[x, y, :]]
    if not col.any():
        return -1
    nz = mat.shape[2]
    return int((nz - 1) - np.argmax(col[::-1]))


def _place_tree(mat, x, y, ground_z, trunk_h, canopy_r):
    nx, ny, nz = mat.shape
    top = min(ground_z + trunk_h, nz - 1)
    mat[x, y, ground_z:top] = WOOD
    r = canopy_r
    cz = top                                  # canopy centered just above the trunk
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dy * dy + dz * dz > r * r + 1:
                    continue
                px, py, pz = x + dx, y + dy, cz + dz
                if 0 <= px < nx and 0 <= py < ny and 0 <= pz < nz and mat[px, py, pz] == AIR:
                    mat[px, py, pz] = LEAVES


def _scatter_trees(mat, noise, seed):
    nx, ny, nz = mat.shape
    step = config.TREE_SPACING
    for gx in range(2, nx - 2, step):
        for gy in range(2, ny - 2, step):
            x = min(gx + int(hash01(gx, gy, 0, seed ^ 0x7A11) * step), nx - 3)
            y = min(gy + int(hash01(gx, gy, 1, seed ^ 0x7A11) * step), ny - 3)
            if noise.sample(np.array([x * 0.05, y * 0.05, 7.0])) < 0.0:
                continue                      # only the "forested" half of the map
            if hash01(x, y, 2, seed ^ 0x7A11) > config.TREE_DENSITY:
                continue
            tz = _column_top(mat, x, y)
            if tz < 0 or mat[x, y, tz] != GRASS:
                continue                      # only on bare grass (skip sand/structures)
            ground_z = tz + 1
            th = config.TREE_MIN_H + int(
                hash01(x, y, 3, seed) * (config.TREE_MAX_H - config.TREE_MIN_H + 1))
            cr = config.TREE_CANOPY_MIN + int(
                hash01(x, y, 4, seed) * (config.TREE_CANOPY_MAX - config.TREE_CANOPY_MIN + 1))
            if ground_z + th + cr + 1 >= nz:
                continue
            _place_tree(mat, x, y, ground_z, th, cr)
