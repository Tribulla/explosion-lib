import numpy as np

from voxelboom.blast import detonate
from voxelboom.materials import GLASS, OBSIDIAN, STONE, RUBBLE, AIR

CENTER = (10, 20, 20)
FAR_BLOCK = (slice(41, 43), slice(16, 24), slice(16, 24))


def _scene(material, shape=(80, 40, 40)):
    mat = np.zeros(shape, dtype=np.int16)
    mat[FAR_BLOCK] = material
    return mat


def test_shockwave_shatters_glass_beyond_the_crater():
    mat = _scene(GLASS)
    before = int((mat == GLASS).sum())
    detonate(mat, CENTER, 60.0, 1)            # crater ~17 voxels; block is ~30 away
    after = int((mat == GLASS).sum())
    assert after < before * 0.5, f"shockwave should shatter most distant glass ({before}->{after})"


def test_shockwave_cracks_stone_to_rubble():
    mat = _scene(STONE)
    detonate(mat, CENTER, 60.0, 1)
    assert int((mat == RUBBLE).sum()) > 0


def test_shockwave_spares_obsidian():
    mat = _scene(OBSIDIAN)
    before = int((mat == OBSIDIAN).sum())
    detonate(mat, CENTER, 60.0, 1)
    assert int((mat == OBSIDIAN).sum()) == before, "obsidian is too tough for the shockwave"


def test_shockwave_has_finite_range():
    mat = np.zeros((150, 40, 40), dtype=np.int16)
    mat[130:136, 16:24, 16:24] = GLASS        # ~120 voxels away, past the wave radius
    before = int((mat == GLASS).sum())
    detonate(mat, CENTER, 60.0, 1)
    assert int((mat == GLASS).sum()) == before, "shockwave must not reach across the whole map"
