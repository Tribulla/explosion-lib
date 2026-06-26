from . import config
from .blast import detonate
from .collapse import despeckle


def post_explosion(world, center, yield_tnt, seed):
    mat = world.mat
    nx, ny, _nz = mat.shape
    result = detonate(mat, center, yield_tnt, seed)

    cx, cy, _ = result.center
    dm = int(result.R0 * config.EJECTA_OUTER) + 6
    dregion = (max(cx - dm, 0), min(cx + dm + 1, nx),
               max(cy - dm, 0), min(cy + dm + 1, ny))
    despeckle(mat, dregion)
    return result
