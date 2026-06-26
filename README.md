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

The blast model aims for *plausible, varied* results — ray-cast shielding, per-material resistance, and
a shockwave that cracks and shatters — with enough seeded randomness that **no two detonations look the
same**. The two engines share one model; the Python side is the reference (the mod is a faithful port,
even down to a bit-for-bit reimplementation of the value noise).

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
   * A **containment** factor (how much solid caps the burst) scales the shockwave: an open surface
     burst spalls a wide area, while a deeply buried one stays contained (a clean cavity, almost no
     surface mess) — like a camouflet.
3. **Shockwave** — a pressure wave reaching ~3.5× the crater radius *damages* (rather than excavates)
   whatever it touches: brittle materials (glass, leaves) shatter, structural blocks crack **in place**
   into rubble (gravel). This is the **only** source of rubble — nothing is ever spawned into empty air.
   Intensity falls off with distance and is scaled by each material's fragility, so glass and trees get
   wrecked far out while obsidian and metal mostly shrug it off. It only hits **exposed** surfaces
   (spalling), so it never cracks deep buried rock into standing gravel pillars, and the crack rate is
   kept low so the blast isn't blanketed in gravel. The
   crack rate (config `shockwaveCrackRate`) is how much gravel the shockwave leaves; set it to `0` for a
   shockwave that only shatters brittle blocks and leaves structural ones intact (gravel-free).
   * **Outer shockwave.** The full 3.5× wave can reach far past the bounded voxel region a big crater fits
     in (holding it all in RAM would cost gigabytes). So past the region edge it continues as a lightweight
     *live-world* ripple (`engine/OuterShockwave.java`): it expands outward ring-by-ring over a few ticks,
     stripping foliage and shattering glass with the same falloff, in **already-loaded chunks only** (it
     never force-loads terrain). That's what makes a forest visibly ripple out to the full shockwave radius
     instead of stopping at the crater's captured region.
4. **Scorching** — surviving rock around the rim is recolored to a charred material.
5. **Despeckle** — a final morphological pass (over the crater zone only) erodes lone spikes,
   isolated single-voxel bumps, and thin 1-wide vertical pillars, so the result reads as a smooth bowl
   rather than a jagged jumble or a forest of columns.

> **No gravity simulation.** Earlier versions ran a structural-collapse pass (connected-component
> labelling that dropped blast-undercut chunks to the floor) and a granular-settle pass (loose materials
> flowing to their angle of repose). Both were removed: for big blasts they dominated the pre-first-block
> compute without changing the crater itself, so undercut terrain now stays put and loose blocks are left
> to vanilla physics.

> **No synthetic ejecta.** Earlier versions also flung a fraction of destroyed blocks ballistically and
> laid a raised rim + thinning ejecta blanket of *new* gravel around the crater. That spawned a lot of
> gravel out of thin air (and rained it onto the ground under high airbursts), so it was removed: debris
> is now strictly *existing blocks broken in place*, never newly-spawned blocks.

### Materials

A small material table drives all behaviour — each material has a colour, density, blast resistance,
and flags for whether it flows like sand or drops as a rigid chunk:

| material | blast resistance | behaviour |
| --- | --- | --- |
| dirt / grass / sand | very low | excavate easily, flow into piles |
| stone | medium | rigid, scorches, cracks to rubble near a blast |
| metal | high | strong shield, rigid |
| glass | low | brittle — shatters from the shockwave far away |
| **obsidian** | enormous | near explosion-proof — the canonical shield |
| wood (trunks) | low-med | rigid; trunks carved out directly by the crater |
| leaves (canopy) | tiny | brittle — stripped by the shockwave |
| bedrock | infinite | indestructible floor |

**Convention used throughout:** arrays are indexed `[x, y, z]`; `z` is up; `z = 0` is bedrock;
material id `0` is air.

---

## The Python sandbox — `voxelboom`

A tiny **voxel explosion sandbox** in pure Python (numpy + scipy). Generate a 3D voxel world, **click
to detonate**, and look at the crater and the scattered rubble it leaves. No animation — you
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
    collapse.py    despeckle cleanup of stray crater-surface voxels (scipy.ndimage)
    debris.py      ballistic debris + rim/ejecta deposition (retired — kept for reference, no longer in the pipeline)
    simulate.py    the full post-explosion pipeline
    render/        pyvista (primary) + matplotlib (fallback) viewers
    app.py         world + viewer + click-to-detonate wiring
  run.py           launcher
  tests/           run with `pytest tests`  (or `python tests/run_tests.py`)
```

### Tuning

Every knob (grid size, tree density, crater scaling, ray energy, randomness amplitudes, shockwave
reach/strength, render options) is in
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

Covers ray-cast shielding, the exposure-based shockwave, determinism (same seed → identical world),
trees, and the Fortran-order voxel→cell-index mapping the renderer relies on.

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
sets **yield** and toggles for **shockwave / scorch / entity damage**. Those are sent
with each blast; the server floors the yield and re-raycasts.

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
toughness drives the shielding and crater shape. The full physics tuning still lives in
`engine/ExplosionConfig.java` (same numbers as the Python `config.py`); the knobs an operator actually
wants are exposed in a config file (below).

### Configuration

On first launch the mod writes a commented **`config/explosionlib.properties`** (in the instance's
config folder — `FabricLoader#getConfigDir` / `FMLPaths.CONFIGDIR` on each loader). Edit it and restart;
missing keys are re-filled, bad values fall back per-key, and a corrupt file falls back to defaults rather
than crashing.

There are **no size or performance caps to tune** — the engine sizes its own safety limits automatically
(see below), so the file holds only what *shapes* a blast. Each scale is also a performance dial, because
cost grows with the volume affected (~ radius³):

| group | keys | notes |
| --- | --- | --- |
| **scales** | `craterScale`, `shockwaveRadius`, `shockwaveCrackRate`, `scorchChance`, `entityEffectRadius`, `entityKnockback`, `expansionSpeedRingsPerTick` | `craterScale` (radius ≈ `craterScale·cbrt(yield)`) is the biggest CPU/RAM driver; `shockwaveRadius` second; `shockwaveCrackRate=0` removes gravel; `expansionSpeedRingsPerTick` trades tick-smoothness for how fast the blast finishes |
| **materials** | `rubbleBlock`, `scorchBlock` | any block id (incl. modded), resolved at server start; no perf impact |
| **feature toggles** | `allowShockwave`, `allowScorch`, `allowEntityDamage` | set `false` to force a feature off server-wide, overriding the per-blast client toggle |

**Safety is automatic, not configured.** The captured-region size scales with the **heap** (a bigger
`-Xmx` allows bigger explosions, then clamps instead of OOM-ing) — *not* with core count, so a many-core
box still gets big craters. Concurrent blasts are bounded by **total RAM used**, not a blast count, so
many small blasts or a few huge ones both fit; the worker pool scales with cores; and a fixed per-tick
work budget bounds main-thread cost so many blasts can't multiply the tick time. None of these are knobs —
they just keep the server alive while the scales decide how big things get.

**Big yields, many blasts, no freeze.** Yield is unbounded; the crater just keeps scaling with it until it
reaches the captured-region cap, which the engine auto-sizes from the heap (`MAX_REGION_HALF`/`MAX_RADIUS`
in `ExplosionConfig`) so a blast can't exceed what the machine holds. The work is split so the server
thread only ever does a small, *bounded* amount per tick:

- **Capture is off-thread.** On the server thread we only copy the `PalettedContainer` of each chunk
  section overlapping the region (a handful of small array copies per chunk — the same trick Minecraft
  uses to serialize chunks asynchronously). The millions-of-voxels decode (classify + blast-resistance
  lookup) then runs on a worker thread off that snapshot, alongside the rest of the pipeline (pure array
  math, no world access). The result is marshalled back to the server thread to apply.
- **A RAM budget** (`REGION_BUDGET_BYTES`, ~¼ of the heap) bounds the memory all in-flight blasts use at
  once — tracked as bytes, not a blast count, so many small blasts or a few huge ones both fit. A worker
  pool (`cores/2`) does the math. When a blast would push past the budget it still booms and hurts entities
  but sheds its terrain edits (logged) rather than risking an out-of-memory.
- **Global per-tick budgets.** Edits are applied **ring-by-ring outward** over server ticks
  (`EXPANSION_SPEED` rings/tick *per blast*), but a shared `MAX_APPLY_BLOCKS_PER_TICK` budget caps the
  *total* block writes across **all** concurrent blasts in a level — so ten overlapping explosions share
  one budget instead of each adding their own load. The post-blast cleanup flood-fill is likewise spread
  across ticks under its own `MAX_CLEANUP_OPS_PER_TICK` budget instead of running as a single burst.

A boom + explosion/smoke particles play at each blast's moving front, so the shockwave reads as an
expanding wave. The affected area's chunks are **force-loaded** (ref-counted) until that blast's cleanup
finishes, so the blast also destroys blocks in chunks that weren't loaded.

**Tidy aftermath.** When a blast finishes, a cleanup pass cascades out from the removed blocks and:
(a) drops grass / vines / glow lichen / torches left floating (vanilla `canSurvive`), and (b) schedules
a fluid tick on any neighbouring water/lava so it flows into the new cavity or drains — otherwise,
since neighbour updates are suppressed during the blast, fluids would just hang frozen. The cascade is
seeded from only the **surface** of the removed region (the cleared voxels next to something that
survived — computed off-thread in `Engine.cleanupSeeds`), not its whole volume, so even a yield-thousands
crater's floating grass/snow clears in a tick or two rather than the cleanup grinding through millions of
interior air cells.

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
