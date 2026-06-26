import numpy as np
from scipy import ndimage

from . import config
from .materials import AIR, BEDROCK, IS_SOLID

_K6 = np.zeros((3, 3, 3), dtype=np.int8)
_K6[1, 1, 0] = _K6[1, 1, 2] = _K6[1, 0, 1] = _K6[1, 2, 1] = _K6[0, 1, 1] = _K6[2, 1, 1] = 1

_K4H = np.zeros((3, 3, 3), dtype=np.int8)
_K4H[1, 0, 1] = _K4H[1, 2, 1] = _K4H[0, 1, 1] = _K4H[2, 1, 1] = 1


def despeckle(mat, region, iters=None):
    if iters is None:
        iters = config.DESPECKLE_ITERS
    nx, ny, nz = mat.shape
    x0, x1, y0, y1 = region
    hx0, hx1 = max(x0 - 1, 0), min(x1 + 1, nx)
    hy0, hy1 = max(y0 - 1, 0), min(y1 + 1, ny)
    ax0, ay0 = x0 - hx0, y0 - hy0
    ax1, ay1 = ax0 + (x1 - x0), ay0 + (y1 - y0)
    for _ in range(iters):
        halo = mat[hx0:hx1, hy0:hy1, :]
        solid = IS_SOLID[halo]
        solid_i8 = solid.astype(np.int8)
        nb = ndimage.convolve(solid_i8, _K6, mode="constant", cval=0)
        nbh = ndimage.convolve(solid_i8, _K4H, mode="constant", cval=0)
        thin = solid & (halo != BEDROCK) & ((nb <= 1) | (nbh == 0))
        remove = np.zeros_like(thin)
        remove[ax0:ax1, ay0:ay1, :] = thin[ax0:ax1, ay0:ay1, :]   # only edit the real window
        if not remove.any():
            break
        halo[remove] = AIR
    return mat
