import numpy as np

NX, NY, NZ = 160, 160, 80
SPACING = np.array([1.0, 1.0, 1.0])
ORIGIN = np.array([0.0, 0.0, 0.0])

TREE_SPACING = 7          # gap between candidate tree positions
TREE_DENSITY = 0.55       # fraction of candidates that actually grow a tree
TREE_MIN_H, TREE_MAX_H = 5, 10    # trunk height range
TREE_CANOPY_MIN, TREE_CANOPY_MAX = 2, 4   # canopy radius range

DEFAULT_YIELD = 8.0
K_CRATER = 4.0            # crater radius per kg^(1/3); R0 = K_CRATER * W^(1/3)
POWER_DIVISOR = 1.3
POWER_MIN, POWER_MAX = 1.0, 12.0

RAY_STEP = 0.3            # ray march step
AIR_DECAY = 0.22500001    # air-drag literal (= 0.3 * 0.75)
INTENSITY_LO = 0.6        # per-ray intensity lower bound
INTENSITY_HI = 1.4        # per-ray intensity upper bound
RAY_JITTER_SIGMA = 0.06   # gaussian angular jitter

GLOBAL_RADIUS_JITTER = 0.08   # global radius wobble
RES_NOISE_AMP = 0.5           # resistance noise amplitude
RES_NOISE_FREQ = 0.7
CRATER_ANISOTROPY = 0.15      # crater boundary jitteryness
CRATER_NOISE_FREQ = 3.0       # frequency of directional fBm

CRATER_VERTICAL = 0.6     # cavity vertical radius (lower = shallower bowl)
CORE_FRAC = 0.6
RIM_FRAC = 1.15           # rim zone outer edge
EJECTA_OUTER = 1.8        # ejecta outer edge
RIM_PROB = 0.6
EJECTA_DENSITY = 0.3      # ejecta deposition multiplier
CRATER_PROOF_RESIST = 60.0
DESPECKLE_ITERS = 2       # erodes lone spikes / bumps left in the blast zone
SCORCH_PROB = 0.6

SHOCKWAVE_RADIUS = 3.5        # outer reach of the wave (* crater radius)
SHOCKWAVE_TOUGHNESS_REF = 6.0  # blast_resistance at/below which stuff cracks freely
SHOCKWAVE_CRACK_RATE = 0.2    # fraction of exposed structural surface cracked to rubble in place;
SHOCKWAVE_SHATTER_RATE = 1.2

COLLAPSE_ITERS = 4        # max collapse passes (Jenga cascades)
REPOSE_SWEEPS = 200       # max angle-of-repose sweeps for loose material

DEBRIS_FRACTION = 0.25
DEBRIS_MAX_PARTICLES = 300
UP_BIAS = 0.6             # upward loft added to debris launch velocity
DEBRIS_SPEED = 18.0
DEBRIS_GRAVITY = 24.0
DEBRIS_DT = 0.08
DEBRIS_SCATTER = 0.15     # velocity scatter
DEBRIS_MAX_STEPS = 400
DEBRIS_BASE_DENSITY = 2.6

SHOW_EDGES = False        # black outline on every voxel
MPL_MAX_DIM = 40
