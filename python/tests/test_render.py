import numpy as np

from voxelboom.materials import GLASS


def test_fortran_cell_index_maps_known_voxel():
    nx, ny, nz = 4, 5, 6
    mat = np.zeros((nx, ny, nz), dtype=np.int16)
    i, j, l = 1, 2, 3
    mat[i, j, l] = GLASS

    flat = mat.flatten(order="F")
    k = i + nx * j + nx * ny * l

    assert flat[k] == GLASS
    assert flat.sum() == GLASS
