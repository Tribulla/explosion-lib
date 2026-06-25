# Voxel Explosion Engine

A voxel-based explosion engine for carving **realistic, varied craters** into voxel terrain — built
twice over: a Python sandbox where the physics were designed and tuned, then a Minecraft mod that runs
the same model on real blocks.

- **[The Python sandbox — `voxelboom`](#the-python-sandbox--voxelboom)** — a standalone engine + an
  interactive 3D viewer (PyVista). Generate a voxel world, click to detonate, inspect the result.
  Deterministic and unit-tested; the reference implementation.
- **[The Minecraft mod — `ExplosionLib`](#the-minecraft-mod--explosionlib)** — a **1.21.1** multiloader
  mod (**NeoForge + Fabric**) that ports the engine to live Minecraft blocks, with a debug item that
  blows up the world in real time.

The blast model aims for *plausible, varied* results — ray-cast shielding, per-material resistance, a
shockwave, gravity collapse, ejected debris — with enough seeded randomness that **no two detonations
look the same**. The two engines share one model; the Python side is the reference (the mod is a
faithful port, even down to a bit-for-bit reimplementation of the value noise).

---

## The explosion model

Both engines run the same ordered pipeline on a captured region of voxels. Each blast is seeded from
one value, so a fixed seed reproduces a crater exactly while a fresh seed (every detonation gets one)
gives a different-but-believable one.

1. **Ray-cast shielding** — 1352 rays are fired toward the surface of a 16×16×16 cube (the Minecraft
   scheme). Each ray loses energy to air drag and, crucially, to the **blast resistance** of every
   voxel it passes through. A dense material (obsidian, metal) drains a ray almost instantly, so
   voxels *behind* it are never reached — that's the shielding. Rays carry per-ray random intensity
   and a little angular jitter.
2. **Stochastic crater carve** — yield maps to a crater radius via cube-root (Hopkinson–Cranz)
   scaling. A turbulent, anisotropic radius (fBm noise) carves a clean **ellipsoid cavity** (everything
   but blast-proof obsidian is cleared inside it, so no spiky leftover "teeth"). At the surface its
   upper half is just air, so it reads as a bowl; a *buried* burst clears the whole void instead of
   leaving pillars. The ray pass then extends the reach in soft materials, so dirt excavates farther
   than stone.
   * A **containment** factor (how much solid caps the burst) scales the shockwave, ejecta and debris:
     an open surface burst throws a full ejecta blanket, while a deeply buried one stays contained
     (a clean cavity, almost no surface mess) — like a camouflet.
3. **Shockwave** — a pressure wave reaching ~3.5× the crater radius *damages* (rather than excavates)
   whatever it touches: brittle materials (glass, leaves) shatter, structures crack into rubble.
   Intensity falls off with distance and is scaled by each material's fragility, so glass and trees get
   wrecked far out while obsidian and metal mostly shrug it off. It only hits **exposed** surfaces
   (spalling), so it never cracks deep buried rock into standing gravel pillars. Cracked rubble then
   crumbles when it settles.
4. **Scorching** — surviving rock around the rim is recolored to a charred material.
5. **Structural collapse** — connected-component labelling finds solid chunks no longer attached to
   bedrock; they drop as rigid bodies and land on whatever has already settled. (This is what makes an
   undercut tree topple.)
6. **Debris** — a fraction of destroyed structural voxels are flung ballistically and re-deposited as
   rubble; a raised rim and a thinning ejecta blanket are laid around the crater.
7. **Granular settling** — loose materials (sand, dirt, rubble) flow to their angle of repose;
   structural materials keep their overhangs. Localized to the blast region, so cost scales with the
   blast, not the world.
8. **Despeckle** — a final morphological pass (over the crater/ejecta zone only) erodes lone spikes,
   isolated single-voxel bumps, and thin 1-wide vertical pillars, so the result reads as a smooth bowl
   rather than a jagged jumble or a forest of columns.

### Materials

A small material table drives all behaviour — each material has a colour, density, blast resistance,
and flags for whether it flows like sand or drops as a rigid chunk:

| material | blast resistance | behaviour |
| --- | --- | --- |
| dirt / grass / sand | very low | excavate easily, flow into piles |
| stone | medium | rigid, scorches, cracks to rubble near a blast |
| metal | high | strong shield, rigid, heavy debris |
| glass | low | brittle — shatters from the shockwave far away |
| **obsidian** | enormous | near explosion-proof — the canonical shield |
| wood (trunks) | low-med | rigid, so undercut trees topple as a whole |
| leaves (canopy) | tiny | brittle — stripped by the shockwave |
| bedrock | infinite | indestructible floor & collapse anchor |

**Convention used throughout:** arrays are indexed `[x, y, z]`; `z` is up; `z = 0` is bedrock;
material id `0` is air.

---

## The Python sandbox — `voxelboom`

A tiny **voxel explosion sandbox** in pure Python (numpy + scipy). Generate a 3D voxel world, **click
to detonate**, and look at the crater, collapsed structures and scattered rubble. No animation — you
set off a blast and inspect the terrain it leaves behind. The default world is a 160×160×80 landscape
with forests of destructible trees.

### Install & run

```bash
cd python
pip install -r requirements.txt        # numpy + scipy required; pyvista is the recommended viewer
python run.py                          # opens the interactive 3D window
```

If neither `pyvista` nor `matplotlib` is installed, `voxelboom` falls back to the headless demo and
tells you what to `pip install`.

#### Controls (PyVista viewer)

| Action | Effect |
| --- | --- |
| **left-drag / scroll** | orbit / zoom the camera |
| **hover + press `P`** | detonate just inside the surface under the cursor |
| **`Page Up` / `Page Down`** | increase / decrease the explosion yield |
| **`r`** | reset the terrain to its original state |

Detonation is on the `P` key (not left-click) so the left mouse button stays free for rotating the
camera. Per-voxel grid lines are off by default — set `SHOW_EDGES = True` in
[python/voxelboom/config.py](python/voxelboom/config.py) to bring them back.

### Architecture

```
python/
  voxelboom/
    config.py      all tunable parameters
    materials.py   material table + vectorized lookup arrays
    noise.py       seeded value-noise / fBm / hash (numpy-only)
    world.py       World container + terrain generation
    blast.py       ray-cast shielding + stochastic crater + scorch
    collapse.py    rigid-body structural collapse (scipy.ndimage)
    debris.py      ballistic debris + rim/ejecta deposition
    settle.py      granular settling for loose materials
    simulate.py    the full post-explosion pipeline
    render/        pyvista (primary) + matplotlib (fallback) viewers
    app.py         world + viewer + click-to-detonate wiring
  run.py           launcher
  tests/           run with `pytest tests`  (or `python tests/run_tests.py`)
```

### Tuning

Every knob (grid size, tree density, crater scaling, ray energy, randomness amplitudes, shockwave
reach/strength, gravity/repose, debris ballistics, render options) is in
[python/voxelboom/config.py](python/voxelboom/config.py), grouped and commented. Bigger craters? Raise
`K_CRATER` or the `--yield`. Lumpier edges? Raise `CRATER_ANISOTROPY` / `RES_NOISE_AMP`. Wider/stronger
shockwave? Raise `SHOCKWAVE_RADIUS` / `SHOCKWAVE_CRACK_RATE`. Denser forests? Raise `TREE_DENSITY` or
lower `TREE_SPACING`. Smaller/faster world? Lower `NX/NY/NZ`. Material behaviour is the ~dozen rows in
[python/voxelboom/materials.py](python/voxelboom/materials.py).

### Tests

```bash
cd python
pytest tests            # or: python tests/run_tests.py   (no pytest needed)
```

Covers ray-cast shielding, the exposure-based shockwave, gravity collapse (floating chunks fall,
anchored overhangs survive), determinism (same seed → identical world), trees, and the Fortran-order
voxel→cell-index mapping the renderer relies on.

---

## The Minecraft mod — `ExplosionLib`

A Minecraft **1.21.1** multiloader mod (**NeoForge + Fabric** from one `common` module) that ports the
`voxelboom` engine to operate on real Minecraft blocks. Adds an **Exploder** debug item that carves
the same realistic craters into the live world, in real time.

### Controls (Exploder item)

| Gesture | Effect |
| --- | --- |
| **left-click** | explode where you're standing |
| **shift + right-click** | open the config screen (yield + toggles) |

Get the item from the *Tools & Utilities* creative tab (id `explosionlib:exploder`). The config screen
sets **yield** (0.5–1024) and toggles for **shockwave / gravity collapse / debris / scorch / entity
damage**. Those are sent with each blast; the server clamps the yield and re-raycasts.

**Entities** get hurt and flung at detonation: vanilla-style falloff with line-of-sight
(`Explosion.getSeenPercent`, so blocks shield them) and an upward-biased knockback (synced to clients
via `hurtMarked`). Damage is attributed to the player who set it off. The boom sound + explosion flash
fire instantly at detonation; the block destruction then plays out over the following ticks.

### Build

Requires JDK 21. From the `java/` folder:

```bash
./gradlew build                 # builds both loader jars
#   fabric/build/libs/explosionlib-fabric-1.21.1-1.0.0.jar
#   neoforge/build/libs/explosionlib-neoforge-1.21.1-1.0.0.jar

./gradlew :fabric:runClient     # test in a dev client (Fabric)
./gradlew :neoforge:runClient   # test in a dev client (NeoForge)
```

> The Fabric build needs the **Fabric API** mod present at runtime (it's pulled in dev automatically).

If IntelliJ complains about the project name vs folder, that's the known
[IDEA-317606](https://youtrack.jetbrains.com/issue/IDEA-317606) quirk (the Gradle root folder is
`java` but `rootProject.name` is `ExplosionLib`); it builds fine from the CLI.

### How the engine maps onto Minecraft

The handler captures a cuboid of blocks around the impact into a flat `VoxelRegion`, runs the whole
`voxelboom` pipeline on it, then writes only the changed blocks back. Blocks are classified by
behaviour, not a custom registry:

| voxel role | Minecraft blocks | from |
| --- | --- | --- |
| `AIR` | air / replaceable (grass, flowers, snow) | `isAir` / `canBeReplaced` |
| `FLUID` | water, lava | `instanceof LiquidBlock` (cleared by blasts; not cracked/flung) |
| `LOOSE` | sand, gravel, … | `instanceof FallingBlock` |
| `BRITTLE` | glass, panes, ice, leaves | `HalfTransparentBlock` / `IronBarsBlock` / `BlockTags.LEAVES` |
| `UNBREAKABLE` | bedrock-tier (resist ≥ 50 000) | `getExplosionResistance()` |
| `STRUCT` | everything else solid | — |

Per-voxel blast resistance comes straight from `Block#getExplosionResistance()`, so vanilla material
toughness drives the shielding and crater shape. Cracked rubble → `GRAVEL`, scorch → `BLACKSTONE`
(edit `engine/Palette.java`). All tunables live in `engine/ExplosionConfig.java` (same numbers as the
Python `config.py`).

**Big yields, no freeze.** Yield goes up to **1024** (crater radius capped at `MAX_RADIUS = 40`,
captured region at `MAX_REGION_HALF = 80`). The heavy pipeline runs **off the main thread** on the
captured snapshot (pure array math, no world access), then the result is marshalled back to the server
thread to apply — so even a yield-1024 blast doesn't lock up the tick.

**Real-time playout.** The result is applied **ring-by-ring outward** over server ticks
(`EXPANSION_SPEED` blocks/tick) with a boom + explosion/smoke particles at the moving front, so the
shockwave reads as an expanding wave. The affected area's chunks are **force-loaded** (ref-counted)
for the duration, so the blast also destroys blocks in chunks that weren't loaded.

**Tidy aftermath.** When a blast finishes, a cleanup pass cascades out from the removed blocks and:
(a) drops grass / vines / glow lichen / torches left floating (vanilla `canSurvive`), and (b) schedules
a fluid tick on any neighbouring water/lava so it flows into the new cavity or drains — otherwise,
since neighbour updates are suppressed during the blast, fluids would just hang frozen. Fluids are also
excluded from the collapse pass so a disturbed water pocket isn't dropped as a chunk.

### Project layout

```
java/
  settings.gradle, build.gradle, gradle.properties      # versions pinned for 1.21.1
  buildSrc/…                                             # the two convention plugins
  common/   ← all the real logic (compiled into BOTH loader jars)
    engine/        the ported voxelboom engine (no loader/client coupling)
    item/          ExploderItem (use/useOn -> open screen)
    network/       ExplodePayload (C2S) + ServerExplodeHandler
    client/        ExploderConfigScreen, ExplodeRequests, ClientConfig
    platform/      Services + NetworkPlatform/RegistrationPlatform/ClientPlatform interfaces
  fabric/   ← ModInitializer, payload register/receive, AttackBlock/PreAttack input, platform impls
  neoforge/ ← @Mod, DeferredRegister, RegisterPayloadHandlers, LeftClick events, platform impls
```

Loader-specific bits are abstracted behind `common/platform` interfaces resolved with a `ServiceLoader`
(`META-INF/services/…` in each loader module) — the classic MultiLoader-Template pattern, no
Architectury runtime dependency. No mixins or access wideners/transformers are needed.

> The mod was authored without a local Gradle/Minecraft toolchain: the pure engine was compiled and
> run-tested against stubs (carves craters, deterministic), and the loader glue was validated by API
> review against 1.21.1 rather than a full `gradlew build`. If a version-specific API drifted, the fix
> is usually a one-liner — check the build output and the pinned versions in `gradle.properties`.

---

## Repository layout

```
.
├── README.md      this file
├── .gitignore
├── python/        voxelboom — the engine + PyVista/matplotlib sandbox, tests
└── java/          ExplosionLib — the 1.21.1 NeoForge+Fabric multiloader mod
    ├── common/    shared engine port + item + networking + config screen
    ├── fabric/    Fabric loader glue
    └── neoforge/  NeoForge loader glue
```
