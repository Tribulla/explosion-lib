import numpy as np

from voxelboom.collapse import structural_collapse
from voxelboom.materials import AIR, BEDROCK, STONE, IS_SOLID


def _bedrock_floor(shape):
    mat = np.zeros(shape, dtype=np.int16)
    mat[:, :, 0:2] = BEDROCK
    return mat


def test_floating_chunk_falls_to_floor():
    mat = _bedrock_floor((20, 20, 20))
    mat[8:12, 8:12, 10:13] = STONE        # floating cube, nothing under it
    before = int((mat == STONE).sum())

    structural_collapse(mat)

    after = int((mat == STONE).sum())
    assert after == before                 # rigid drop conserves the chunk
    zs = np.argwhere(mat == STONE)[:, 2]
    assert zs.max() < 10                   # it actually came down
    assert zs.min() == 2                   # resting on the bedrock floor


def test_ground_connected_overhang_survives():
    mat = _bedrock_floor((20, 20, 20))
    mat[5, 10, 2:14] = STONE               # vertical pillar
    mat[5:13, 10, 13] = STONE              # overhang attached at the top
    snapshot = mat.copy()

    structural_collapse(mat)

    assert np.array_equal(mat, snapshot)   # nothing should move


def test_bedrock_is_never_dropped():
    mat = _bedrock_floor((10, 10, 10))
    structural_collapse(mat)
    assert (mat[:, :, 0] == BEDROCK).all()
