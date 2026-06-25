import numpy as np

from voxelboom.world import World, generate_terrain, _place_tree
from voxelboom.simulate import post_explosion
from voxelboom.materials import WOOD, LEAVES, GRASS, BEDROCK


def test_terrain_grows_trees_with_canopy_above_trunk():
    mat = generate_terrain((96, 96, 64), seed=2)
    assert int((mat == WOOD).sum()) > 0, "expected some tree trunks"
    assert int((mat == LEAVES).sum()) > 0, "expected some leaves"
    trunk_z = np.argwhere(mat == WOOD)[:, 2]
    leaf_z = np.argwhere(mat == LEAVES)[:, 2]
    assert leaf_z.mean() > trunk_z.mean(), "canopy should sit above the trunks"


def test_blast_topples_a_tree():
    mat = np.zeros((40, 40, 40), dtype=np.int16)
    mat[:, :, 0:2] = BEDROCK
    mat[:, :, 2] = GRASS                       # a grass surface at z=2
    _place_tree(mat, 20, 20, 3, trunk_h=8, canopy_r=3)
    before = int(((mat == WOOD) | (mat == LEAVES)).sum())

    world = World(mat)
    post_explosion(world, (20, 20, 4), yield_tnt=20.0, seed=1)   # blast at the trunk base

    after = int(((world.mat == WOOD) | (world.mat == LEAVES)).sum())
    assert after < before, "the tree should be wrecked by a blast at its base"
