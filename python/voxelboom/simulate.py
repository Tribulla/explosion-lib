from . import config
from .blast import detonate
from .collapse import structural_collapse, despeckle, frozen_mask
from .settle import granular_settle


def post_explosion(world, center, yield_tnt, seed):
    mat = world.mat
    nx, ny, _nz = mat.shape
    frozen = frozen_mask(mat)            # capture pre-existing floaters BEFORE the blast carves anything
    result = detonate(mat, center, yield_tnt, seed)

    structural_collapse(mat, frozen=frozen)

    cx, cy, _ = result.center
    margin = int(result.R0 * config.SHOCKWAVE_RADIUS) + 30
    region = (max(cx - margin, 0), min(cx + margin + 1, nx),
              max(cy - margin, 0), min(cy + margin + 1, ny))
    dm = int(result.R0 * config.EJECTA_OUTER) + 6
    dregion = (max(cx - dm, 0), min(cx + dm + 1, nx),
               max(cy - dm, 0), min(cy + dm + 1, ny))

    granular_settle(mat, region)
    despeckle(mat, dregion)                 # smooth spikes/bumps/pillars in the crater zone
    structural_collapse(mat, max_iters=3, frozen=frozen)   # despeckle can free a chunk -> let it fall
    granular_settle(mat, region)
    return result
