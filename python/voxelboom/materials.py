from dataclasses import dataclass

import numpy as np

AIR = 0
BEDROCK = 1
STONE = 2
DIRT = 3
GRASS = 4
SAND = 5
METAL = 6
GLASS = 7
OBSIDIAN = 8
RUBBLE = 9
SCORCHED = 10
WOOD = 11
LEAVES = 12


@dataclass(frozen=True)
class Material:
    id: int
    name: str
    density: float            # informs ejecta loft (heavier -> shorter throw)
    blast_resistance: float   # Minecraft-style explosion resistance
    rgb: tuple                # display color, components 0..1
    is_loose: bool            # participates in granular settling (sand-like)
    is_solid: bool            # occupies space / blocks rays & falling chunks
    is_struct: bool           # rigid body: drops as a chunk, never granular-flows
    destructible: bool        # can a blast remove it at all?
    scorch_id: int            # id it becomes when scorched (-1 = does not scorch)
    repose_slope: int         # angle-of-repose threshold (higher = flatter piles)
    alpha: float = 1.0        # render opacity hint
    is_brittle: bool = False  # shatters (vanishes) under the shockwave instead of cracking


#  id          name        dens   resist     rgb                       loose  solid  struct destr  scorch    repose alpha
MATERIAL_TABLE = [
    Material(AIR,      "air",      0.0,   0.0,    (0.00, 0.00, 0.00),   False, False, False, False, -1,       0),
    Material(BEDROCK,  "bedrock",  3.0,   1e9,    (0.105, 0.105, 0.110), False, True,  False, False, -1,       0),
    Material(STONE,    "stone",    2.6,   6.0,    (0.490, 0.490, 0.510), False, True,  True,  True,  SCORCHED, 0),
    Material(DIRT,     "dirt",     1.4,   0.5,    (0.420, 0.310, 0.165), True,  True,  False, True,  -1,       1),
    Material(GRASS,    "grass",    1.3,   0.5,    (0.310, 0.560, 0.245), True,  True,  False, True,  -1,       1),
    Material(SAND,     "sand",     1.5,   0.4,    (0.851, 0.761, 0.478), True,  True,  False, True,  -1,       2),
    Material(METAL,    "metal",    7.8,   25.0,   (0.624, 0.722, 0.784), False, True,  True,  True,  SCORCHED, 0),
    Material(GLASS,    "glass",    2.4,   0.3,    (0.682, 0.890, 0.925), False, True,  True,  True,  -1,       0, 0.5, True),
    Material(OBSIDIAN, "obsidian", 2.9,   1200.0, (0.106, 0.078, 0.184), False, True,  True,  True,  -1,       0),
    Material(RUBBLE,   "rubble",   1.8,   0.8,    (0.353, 0.325, 0.290), True,  True,  False, True,  -1,       1),
    Material(SCORCHED, "scorched", 2.6,   6.0,    (0.270, 0.235, 0.205), False, True,  True,  True,  -1,       0),
    Material(WOOD,     "wood",     1.0,   3.0,    (0.400, 0.260, 0.130), False, True,  True,  True,  -1,       0),
    Material(LEAVES,   "leaves",   0.4,   0.3,    (0.180, 0.430, 0.170), False, True,  True,  True,  -1,       0, 1.0, True),
]

MATERIAL_BY_ID = {m.id: m for m in MATERIAL_TABLE}
NAME_BY_ID = {m.id: m.name for m in MATERIAL_TABLE}

_N = max(m.id for m in MATERIAL_TABLE) + 1

RESIST = np.zeros(_N, dtype=np.float64)
DENSITY = np.zeros(_N, dtype=np.float64)
COLOR_RGB = np.zeros((_N, 3), dtype=np.float64)
ALPHA = np.ones(_N, dtype=np.float64)
IS_SOLID = np.zeros(_N, dtype=bool)
IS_LOOSE = np.zeros(_N, dtype=bool)
IS_STRUCT = np.zeros(_N, dtype=bool)
IS_BRITTLE = np.zeros(_N, dtype=bool)
DESTRUCTIBLE = np.zeros(_N, dtype=bool)
REPOSE = np.ones(_N, dtype=np.int64)
SCORCH_OF = np.full(_N, -1, dtype=np.int64)

for _m in MATERIAL_TABLE:
    RESIST[_m.id] = _m.blast_resistance
    DENSITY[_m.id] = _m.density
    COLOR_RGB[_m.id] = _m.rgb
    ALPHA[_m.id] = _m.alpha
    IS_SOLID[_m.id] = _m.is_solid
    IS_LOOSE[_m.id] = _m.is_loose
    IS_STRUCT[_m.id] = _m.is_struct
    IS_BRITTLE[_m.id] = _m.is_brittle
    DESTRUCTIBLE[_m.id] = _m.destructible
    REPOSE[_m.id] = _m.repose_slope
    SCORCH_OF[_m.id] = _m.scorch_id
