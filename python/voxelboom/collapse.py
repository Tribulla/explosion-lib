import numpy as np
from scipy import ndimage

from . import config
from .materials import AIR, BEDROCK, IS_SOLID

CONN6 = ndimage.generate_binary_structure(3, 1)    # face neighbors (6)
CONN18 = ndimage.generate_binary_structure(3, 2)   # + edge neighbors (18)
CONN26 = ndimage.generate_binary_structure(3, 3)   # + corner neighbors (26)

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


def structural_collapse(mat, connectivity=CONN6, max_iters=None):
    if max_iters is None:
        max_iters = config.COLLAPSE_ITERS
    nz = mat.shape[2]

    for _ in range(max_iters):
        solid = IS_SOLID[mat]
        labels, n = ndimage.label(solid, structure=connectivity)
        if n == 0:
            break
        anchored = np.unique(labels[:, :, 0])
        anchored = anchored[anchored != 0]
        supported = np.isin(labels, anchored)
        floating = solid & ~supported & (mat != BEDROCK)
        if not floating.any():
            break

        occ = supported | (mat == BEDROCK)   # what a falling chunk can land on
        float_labels = np.unique(labels[floating])
        float_labels = float_labels[float_labels != 0]

        comps = []
        for lbl in float_labels:
            vox = np.argwhere(labels == lbl)
            comps.append((int(vox[:, 2].min()), lbl, vox))
        comps.sort(key=lambda c: c[0])   # lowest first

        moved_any = False
        for _minz, _lbl, vox in comps:
            xs, ys, zs = vox[:, 0], vox[:, 1], vox[:, 2]
            k = 0
            while True:
                nzs = zs - (k + 1)
                if nzs.min() < 0:
                    break
                if occ[xs, ys, nzs].any():
                    break
                k += 1
            vals = mat[xs, ys, zs]
            if k > 0:
                mat[xs, ys, zs] = AIR
                mat[xs, ys, zs - k] = vals
                moved_any = True
            occ[xs, ys, zs - k] = True
        if not moved_any:
            break

    return mat
