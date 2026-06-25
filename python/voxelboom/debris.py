import numpy as np

from . import config
from .materials import AIR, RUBBLE, DENSITY, IS_SOLID


def first_solid_above(mat, x, y):
    col = IS_SOLID[mat[x, y, :]]
    if not col.any():
        return 0
    nz = mat.shape[2]
    return int((nz - 1) - np.argmax(col[::-1]) + 1)


def _deposit(mat, x, y):
    nz = mat.shape[2]
    land = first_solid_above(mat, x, y)
    if 0 <= land < nz and mat[x, y, land] == AIR:
        mat[x, y, land] = RUBBLE


def apply_debris(mat, destroyed_struct_coords, rim_targets, ejecta_targets, center, rng,
                 debris_fraction=None, up_bias=None, speed=None, g=None, dt=None, openness=1.0):
    if debris_fraction is None:
        debris_fraction = config.DEBRIS_FRACTION
    if up_bias is None:
        up_bias = config.UP_BIAS
    if speed is None:
        speed = config.DEBRIS_SPEED
    if g is None:
        g = config.DEBRIS_GRAVITY
    if dt is None:
        dt = config.DEBRIS_DT

    nx, ny, nz = mat.shape
    cf = np.array([center[0] + 0.5, center[1] + 0.5, center[2] + 0.5])

    coords = [c for (c, _m) in destroyed_struct_coords]
    mats = [m for (_c, m) in destroyed_struct_coords]
    k = int(round(debris_fraction * openness * len(coords)))
    k = min(k, config.DEBRIS_MAX_PARTICLES)
    if k > 0:
        sel = rng.choice(len(coords), size=k, replace=False)
        for i in sel:
            x, y, z = coords[i]
            m = mats[i]
            pos = np.array([x + 0.5, y + 0.5, z + 0.5])
            out = pos - cf
            n = np.linalg.norm(out)
            direction = out / n if n > 1e-6 else np.array([0.0, 0.0, 1.0])
            vel = direction * speed
            vel[2] += speed * up_bias
            vel += rng.normal(0.0, speed * config.DEBRIS_SCATTER, 3)
            vel *= config.DEBRIS_BASE_DENSITY / max(DENSITY[m], 0.1)  # heavy flies shorter
            for _step in range(config.DEBRIS_MAX_STEPS):
                vel[2] -= g * dt
                pos += vel * dt
                ix, iy = int(round(pos[0])), int(round(pos[1]))
                if ix < 0 or iy < 0 or ix >= nx or iy >= ny:
                    break  # left the grid -> lost
                land = first_solid_above(mat, ix, iy)
                if pos[2] <= land:
                    if land < nz and mat[ix, iy, land] == AIR:
                        mat[ix, iy, land] = RUBBLE
                    break

    for (x, y) in rim_targets:
        _deposit(mat, x, y)
    for (x, y) in ejecta_targets:
        _deposit(mat, x, y)

    return mat
