import numpy as np

from voxelboom.blast import detonate
from voxelboom.materials import AIR, DIRT, OBSIDIAN, IS_SOLID

CENTER = (6, 20, 20)
WALL_X = 15                       # obsidian plane sits here (clear of the radial core)
BLOCK = (slice(19, 27), slice(14, 26), slice(14, 26))   # dirt target, in the ray-only zone past the bowl
YIELD = 24.0
SEED = 1


def _scene(with_wall):
    mat = np.zeros((52, 40, 40), dtype=np.int16)
    mat[BLOCK] = DIRT
    if with_wall:
        mat[WALL_X, :, :] = OBSIDIAN
    return mat


def _block_solid(mat):
    return int(IS_SOLID[mat[BLOCK]].sum())


def test_shield_drastically_reduces_destruction_behind_it():
    bare = _scene(with_wall=False)
    full = _block_solid(bare)
    detonate(bare, CENTER, YIELD, SEED)
    bare_destroyed = full - _block_solid(bare)

    shielded = _scene(with_wall=True)
    detonate(shielded, CENTER, YIELD, SEED)
    shielded_destroyed = full - _block_solid(shielded)

    assert bare_destroyed > 30, f"bare blast should carve the block (got {bare_destroyed})"
    assert shielded_destroyed <= 0.2 * bare_destroyed, (
        f"shield should block most rays (bare={bare_destroyed}, shielded={shielded_destroyed})"
    )


def test_obsidian_wall_survives_the_blast():
    shielded = _scene(with_wall=True)
    before = int((shielded == OBSIDIAN).sum())
    detonate(shielded, CENTER, YIELD, SEED)
    after = int((shielded == OBSIDIAN).sum())
    assert after == before, "obsidian wall (outside the core) should be untouched"
