import numpy as np

from voxelboom.world import World, generate_terrain
from voxelboom.simulate import post_explosion


def _run(terrain_seed, blast_seed):
    world = World(generate_terrain((40, 40, 32), seed=terrain_seed))
    cx, cy = 20, 20
    surf = int(world.heightmap()[cx, cy])
    post_explosion(world, (cx, cy, max(surf - 1, 1)), yield_tnt=10.0, seed=blast_seed)
    return world.mat


def test_same_seed_is_reproducible():
    a = _run(3, 7)
    b = _run(3, 7)
    assert np.array_equal(a, b)


def test_different_blast_seed_differs():
    a = _run(3, 7)
    b = _run(3, 8)
    assert not np.array_equal(a, b)
