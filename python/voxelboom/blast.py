from dataclasses import dataclass

import numpy as np

from . import config
from .materials import (
    AIR, BEDROCK, SCORCHED, RUBBLE,
    RESIST, IS_SOLID, IS_STRUCT, IS_BRITTLE, DESTRUCTIBLE, SCORCH_OF,
)
from .noise import ValueNoise3D, fbm, hash01

_RAY_DIRS = None


def ray_directions():
    global _RAY_DIRS
    if _RAY_DIRS is None:
        dirs = []
        for gx in range(16):
            for gy in range(16):
                for gz in range(16):
                    if gx in (0, 15) or gy in (0, 15) or gz in (0, 15):
                        d = np.array([gx / 15 * 2 - 1, gy / 15 * 2 - 1, gz / 15 * 2 - 1])
                        n = np.linalg.norm(d)
                        if n > 0:
                            dirs.append(d / n)
        _RAY_DIRS = np.array(dirs)
    return _RAY_DIRS


def overpressure_brode(Z):
    Z = np.maximum(Z, 1e-3)
    ps = 0.975 / Z + 1.455 / Z ** 2 + 5.85 / Z ** 3 - 0.019
    near = 6.7 / Z ** 3 + 1.0
    return np.where(ps > 10.0, near, ps)


@dataclass
class DetonationResult:
    seed: int
    center: tuple
    R0: float
    power: float
    destroyed_struct_coords: list   # list of ((x, y, z), original_material_id)
    rim_targets: list               # voxels flagged for a raised lip
    ejecta_targets: list            # voxels flagged for ejecta deposition
    openness: float = 1.0           # 1 = open surface burst, 0 = deeply buried/contained


def detonate(mat, center, yield_tnt, seed):
    nx, ny, nz = mat.shape
    rng = np.random.default_rng(seed)
    noise = ValueNoise3D(seed)
    cx, cy, cz = (int(center[0]), int(center[1]), int(center[2]))
    center_f = np.array([cx + 0.5, cy + 0.5, cz + 0.5])

    W = max(float(yield_tnt), 1e-6)
    Wcr = W ** (1.0 / 3.0)
    R0 = config.K_CRATER * Wcr
    R0 *= (1.0 + rng.normal(0.0, config.GLOBAL_RADIUS_JITTER))      # [RND-1]
    R0 = max(R0, 1.0)
    power = float(np.clip(R0 / config.POWER_DIVISOR, config.POWER_MIN, config.POWER_MAX))

    ztop = min(cz + 1 + int(R0 * 1.5) + 1, nz)
    above = mat[cx, cy, cz + 1:ztop]
    solid_above = int(IS_SOLID[above].sum()) if above.size else 0
    openness = float(np.clip(1.0 - solid_above / max(R0, 1.0), 0.0, 1.0))

    reach = power * config.INTENSITY_HI / config.AIR_DECAY * config.RAY_STEP
    half = int(max(reach, R0 * config.EJECTA_OUTER * (1.0 + config.CRATER_ANISOTROPY))) + 2
    bx0, bx1 = max(cx - half, 0), min(cx + half + 1, nx)
    by0, by1 = max(cy - half, 0), min(cy + half + 1, ny)
    bz0, bz1 = max(cz - half, 0), min(cz + half + 1, nz)
    bxs = np.arange(bx0, bx1)
    bys = np.arange(by0, by1)
    bzs = np.arange(bz0, bz1)
    BX, BY, BZ = np.meshgrid(bxs, bys, bzs, indexing="ij")
    box_pts = np.stack([BX + 0.5, BY + 0.5, BZ + 0.5], axis=-1) * config.RES_NOISE_FREQ
    res_field = 1.0 + config.RES_NOISE_AMP * noise.sample(box_pts)   # [RND-4], shared

    def res_mult(ix, iy, iz):
        lx, ly, lz = ix - bx0, iy - by0, iz - bz0
        if 0 <= lx < res_field.shape[0] and 0 <= ly < res_field.shape[1] and 0 <= lz < res_field.shape[2]:
            return res_field[lx, ly, lz]
        return 1.0

    destroy = set()

    for base_dir in ray_directions():
        d = base_dir + rng.normal(0.0, config.RAY_JITTER_SIGMA, 3)  # [RND-2]
        n = np.linalg.norm(d)
        if n == 0:
            continue
        d = d / n
        I = power * (config.INTENSITY_LO + rng.random() * (config.INTENSITY_HI - config.INTENSITY_LO))  # [RND-3]
        pos = center_f.copy()
        while I > 0.0:
            ix, iy, iz = int(pos[0]), int(pos[1]), int(pos[2])
            if ix < 0 or iy < 0 or iz < 0 or ix >= nx or iy >= ny or iz >= nz:
                break
            b = mat[ix, iy, iz]
            if b != AIR:
                res = RESIST[b] * res_mult(ix, iy, iz)
                I -= (res + 0.3) * 0.3            # block attenuation -> shielding
                if I > 0.0 and DESTRUCTIBLE[b]:
                    destroy.add((ix, iy, iz))
            pos += d * config.RAY_STEP
            I -= config.AIR_DECAY                 # air drag (always)

    offx = (BX + 0.5) - center_f[0]
    offy = (BY + 0.5) - center_f[1]
    offz = (BZ + 0.5) - center_f[2]
    dd = np.sqrt(offx ** 2 + offy ** 2 + offz ** 2)
    safe = np.maximum(dd, 1e-9)
    dir_box = np.stack([offx / safe, offy / safe, offz / safe], axis=-1) * config.CRATER_NOISE_FREQ
    Reff = R0 * (1.0 + config.CRATER_ANISOTROPY * fbm(noise, dir_box))   # [RND-5]
    Reff = np.maximum(Reff, 1e-3)

    sub = mat[bx0:bx1, by0:by1, bz0:bz1]
    solid = IS_SOLID[sub]
    destr = DESTRUCTIBLE[sub]
    res_v = RESIST[sub] * res_field
    res_box = RESIST[sub]
    op = overpressure_brode(dd / Wcr)

    vrad = np.maximum(Reff * config.CRATER_VERTICAL, 1e-3)
    ell = (offx ** 2 + offy ** 2) / (Reff ** 2) + (offz / vrad) ** 2
    in_core = ell < (config.CORE_FRAC ** 2)
    in_cavity = ell < 1.0
    core_destroy = in_core & solid & destr     # core vaporizes everything (even obsidian)
    cavity_destroy = in_cavity & solid & destr & ((res_box < config.CRATER_PROOF_RESIST) | (op > res_v))
    crater_mask = core_destroy | cavity_destroy

    for (lx, ly, lz) in np.argwhere(crater_mask):
        destroy.add((lx + bx0, ly + by0, lz + bz0))

    GX2, GY2 = np.meshgrid(bxs, bys, indexing="ij")
    dxy = np.hypot(GX2 + 0.5 - center_f[0], GY2 + 0.5 - center_f[1])
    h2 = hash01(GX2, GY2, np.zeros_like(GX2), seed)
    rim2d = (dxy >= R0 * 0.85) & (dxy < R0 * config.RIM_FRAC) & (h2 < config.RIM_PROB * openness)  # [RND-6]
    t2 = np.clip((R0 / np.maximum(dxy, 1e-9)) ** 3, 0.0, 1.0)
    ejecta2d = (dxy >= R0 * config.RIM_FRAC) & (dxy < R0 * config.EJECTA_OUTER) & \
               (h2 < t2 * config.EJECTA_DENSITY * openness)                               # [RND-7]
    rim_targets = list(zip(GX2[rim2d].tolist(), GY2[rim2d].tolist()))
    ejecta_targets = list(zip(GX2[ejecta2d].tolist(), GY2[ejecta2d].tolist()))

    destroyed_struct_coords = []
    for (x, y, z) in destroy:
        b = mat[x, y, z]
        if b == BEDROCK:
            continue
        if IS_STRUCT[b]:
            destroyed_struct_coords.append(((x, y, z), int(b)))
    for (x, y, z) in destroy:
        if mat[x, y, z] != BEDROCK:
            mat[x, y, z] = AIR

    _apply_shockwave(mat, cx, cy, cz, center_f, R0, seed, openness)

    _apply_scorch(mat, bx0, bx1, by0, by1, bz0, bz1, dd, R0, seed)

    return DetonationResult(
        seed=int(seed), center=(cx, cy, cz), R0=float(R0), power=power,
        destroyed_struct_coords=destroyed_struct_coords,
        rim_targets=rim_targets, ejecta_targets=ejecta_targets,
        openness=openness,
    )


def _apply_shockwave(mat, cx, cy, cz, center_f, R0, seed, openness=1.0):
    nx, ny, nz = mat.shape
    Rs = R0 * config.SHOCKWAVE_RADIUS
    half = int(Rs) + 1
    x0, x1 = max(cx - half, 0), min(cx + half + 1, nx)
    y0, y1 = max(cy - half, 0), min(cy + half + 1, ny)
    z0, z1 = max(cz - half, 0), min(cz + half + 1, nz)
    BX, BY, BZ = np.meshgrid(np.arange(x0, x1), np.arange(y0, y1), np.arange(z0, z1), indexing="ij")
    dd = np.sqrt((BX + 0.5 - center_f[0]) ** 2
                 + (BY + 0.5 - center_f[1]) ** 2
                 + (BZ + 0.5 - center_f[2]) ** 2)

    sub = mat[x0:x1, y0:y1, z0:z1]                  # a view -> edits write back to mat
    solid = IS_SOLID[sub] & (sub != BEDROCK)
    pad = np.pad(sub, 1, constant_values=AIR)
    air = pad == AIR
    exposed = (
        air[2:, 1:-1, 1:-1] | air[:-2, 1:-1, 1:-1] |
        air[1:-1, 2:, 1:-1] | air[1:-1, :-2, 1:-1] |
        air[1:-1, 1:-1, 2:] | air[1:-1, 1:-1, :-2]
    )
    span = max(Rs - R0, 1e-6)
    intensity = np.clip((Rs - dd) / span, 0.0, 1.0)   # 1 at crater edge -> 0 at Rs
    in_wave = solid & exposed & (dd > R0 * 0.5) & (intensity > 0.0)
    fragility = np.clip(config.SHOCKWAVE_TOUGHNESS_REF / np.maximum(RESIST[sub], 1e-6), 0.0, 1.0)
    fragility[fragility < 0.05] = 0.0          # very tough materials (obsidian) are immune

    h = hash01(BX, BY, BZ, (int(seed) ^ 0x5BF03) & 0x7FFFFFFF)
    brittle = IS_BRITTLE[sub]
    struct = IS_STRUCT[sub]
    shatter = in_wave & brittle & (
        h < np.clip(intensity * openness * config.SHOCKWAVE_SHATTER_RATE, 0.0, 1.0))
    crack = in_wave & struct & (~brittle) & (
        h < np.clip(intensity * fragility * openness * config.SHOCKWAVE_CRACK_RATE, 0.0, 1.0))
    sub[shatter] = AIR
    sub[crack] = RUBBLE


def _apply_scorch(mat, bx0, bx1, by0, by1, bz0, bz1, dd, R0, seed):
    sub = mat[bx0:bx1, by0:by1, bz0:bz1]
    scorchable = (SCORCH_OF[sub] >= 0)
    shell = (dd > R0 * config.CORE_FRAC) & (dd < R0 * config.RIM_FRAC * 1.05)
    pad = np.pad(sub, 1, constant_values=AIR)
    air = pad == AIR
    exposed = (
        air[2:, 1:-1, 1:-1] | air[:-2, 1:-1, 1:-1] |
        air[1:-1, 2:, 1:-1] | air[1:-1, :-2, 1:-1] |
        air[1:-1, 1:-1, 2:] | air[1:-1, 1:-1, :-2]
    )
    BX, BY, BZ = np.meshgrid(np.arange(bx0, bx1), np.arange(by0, by1),
                             np.arange(bz0, bz1), indexing="ij")
    mottle = hash01(BX, BY, BZ, (int(seed) ^ 0x5C0C) & 0x7FFFFFFF) < config.SCORCH_PROB
    mask = scorchable & shell & exposed & mottle
    if mask.any():
        sub[mask] = SCORCH_OF[sub][mask]
