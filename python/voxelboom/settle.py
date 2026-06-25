import numpy as np

from . import config
from .materials import AIR, IS_SOLID, IS_LOOSE, REPOSE


def _heightmap(mat):
    solid = IS_SOLID[mat]
    nz = mat.shape[2]
    has = solid.any(axis=2)
    top = (nz - 1) - np.argmax(solid[:, :, ::-1], axis=2)
    return np.where(has, top + 1, 0).astype(np.int64)


def settle_columns_fast(mat, region=None, max_iters=None):
    nx, ny, nz = mat.shape
    x0, x1, y0, y1 = region if region is not None else (0, nx, 0, ny)
    sub = mat[x0:x1, y0:y1, :]
    if max_iters is None:
        max_iters = nz
    for _ in range(max_iters):
        loose = IS_LOOSE[sub]
        air_below = np.zeros_like(loose)
        air_below[:, :, 1:] = sub[:, :, :-1] == AIR
        fall = loose & air_below              # loose voxels with air directly under them
        if not fall.any():
            break
        vals = sub[fall]
        sub[fall] = AIR
        tgt = np.zeros_like(fall)
        tgt[:, :, :-1] = fall[:, :, 1:]       # one cell lower (1:1 with `fall`, same order)
        sub[tgt] = vals
    return mat


def topple_repose(mat, max_sweeps=None, region=None):
    if max_sweeps is None:
        max_sweeps = config.REPOSE_SWEEPS
    nx, ny, nz = mat.shape
    x0, x1, y0, y1 = region if region is not None else (0, nx, 0, ny)
    neigh = ((1, 0), (-1, 0), (0, 1), (0, -1))
    for _ in range(max_sweeps):
        height = _heightmap(mat)             # global heightmap is cheap/vectorized
        moved = False
        for x in range(x0, x1):
            for y in range(y0, y1):
                h = int(height[x, y])
                if h <= 0:
                    continue
                topz = h - 1
                tb = mat[x, y, topz]
                if not IS_LOOSE[tb]:
                    continue
                best = None
                best_dh = 0
                for dx, dy in neigh:
                    nx_, ny_ = x + dx, y + dy
                    if nx_ < 0 or ny_ < 0 or nx_ >= nx or ny_ >= ny:
                        continue
                    dh = h - int(height[nx_, ny_])
                    if dh > best_dh:
                        best_dh = dh
                        best = (nx_, ny_)
                if best is None or best_dh <= REPOSE[tb]:
                    continue
                nx_, ny_ = best
                dz = int(height[nx_, ny_])
                if dz < nz and mat[nx_, ny_, dz] == AIR:
                    mat[nx_, ny_, dz] = tb
                    mat[x, y, topz] = AIR
                    height[x, y] -= 1
                    height[nx_, ny_] += 1
                    moved = True
        if not moved:
            break
    return mat


def granular_settle(mat, region=None):
    settle_columns_fast(mat, region)
    topple_repose(mat, region=region)
    settle_columns_fast(mat, region)
    return mat
