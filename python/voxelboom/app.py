import argparse

import numpy as np

from . import config
from .materials import IS_SOLID, AIR, NAME_BY_ID
from .world import World, generate_terrain
from .simulate import post_explosion
from .render import make_viewer


def point_to_voxel(point, world):
    mat = world.mat
    shape = np.array(mat.shape)
    p = np.asarray(point, dtype=float)
    center_world = world.origin + 0.5 * world.spacing * shape
    inward = center_world - p
    n = np.linalg.norm(inward)
    step = inward / n if n > 0 else np.array([0.0, 0.0, -1.0])
    p = p + step * (0.25 * world.spacing.min())
    idx = np.floor((p - world.origin) / world.spacing).astype(int)
    idx = np.clip(idx, 0, shape - 1)
    if mat[tuple(idx)] == AIR:
        for s in range(1, 5):
            cand = np.floor((p + step * (s * 0.5) - world.origin) / world.spacing).astype(int)
            cand = np.clip(cand, 0, shape - 1)
            if IS_SOLID[mat[tuple(cand)]]:
                idx = cand
                break
    return tuple(int(i) for i in idx)


_seed_state = [12345]


def next_seed():
    _seed_state[0] += 1
    return (_seed_state[0] * 2654435761) & 0x7FFFFFFF


def _ascii_heightmap(world, cols=48, rows=24):
    h = world.heightmap().astype(float)
    nx, ny = h.shape
    nz = world.mat.shape[2]
    ramp = " .:-=+*#%@"
    xs = np.linspace(0, nx - 1, cols).astype(int)
    ys = np.linspace(0, ny - 1, rows).astype(int)
    lines = []
    for y in ys[::-1]:
        row = []
        for x in xs:
            level = int(h[x, y] / max(nz, 1) * (len(ramp) - 1))
            row.append(ramp[min(level, len(ramp) - 1)])
        lines.append("".join(row))
    return "\n".join(lines)


def headless_demo(world, yield_tnt, seed=None):
    nx, ny, nz = world.mat.shape
    before = int(IS_SOLID[world.mat].sum())
    cx, cy = nx // 2, ny // 2
    surf = int(world.heightmap()[cx, cy])
    center = (cx, cy, max(surf - 1, 1))
    s = next_seed() if seed is None else int(seed)

    print(f"[voxelboom] headless demo: detonating yield={yield_tnt} at {center} (seed={s})")
    print("Top-down terrain BEFORE:")
    print(_ascii_heightmap(world))

    post_explosion(world, center, yield_tnt, s)

    after = int(IS_SOLID[world.mat].sum())
    print("\nTop-down terrain AFTER:")
    print(_ascii_heightmap(world))
    print(f"\nSolid voxels: {before} -> {after}  (net {after - before:+d})")
    out = "voxel_world.npy"
    np.save(out, world.mat)
    print(f"Saved resulting voxel grid to {out} (load with numpy.load).")


def build_parser():
    p = argparse.ArgumentParser(
        prog="voxelboom",
        description="Voxel explosion sandbox: click in the 3D view to detonate.",
    )
    p.add_argument("--size", type=int, default=config.NX, help="grid X/Y size")
    p.add_argument("--height", type=int, default=config.NZ, help="grid Z size")
    p.add_argument("--seed", type=int, default=0, help="terrain seed")
    p.add_argument("--yield", dest="yield_tnt", type=float, default=config.DEFAULT_YIELD,
                   help="explosion yield in kg-TNT-equivalent")
    p.add_argument("--headless", action="store_true",
                   help="skip the GUI and run the scripted demo")
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    shape = (args.size, args.size, args.height)
    world = World(generate_terrain(shape, seed=args.seed))

    viewer = None if args.headless else make_viewer()
    if viewer is None:
        if not args.headless:
            print("[voxelboom] No 3D backend found.")
            print("  pip install pyvista      # interactive click-to-detonate viewer (recommended)")
            print("  pip install matplotlib   # slower static fallback")
            print("  Running the headless demo instead.\n")
        headless_demo(world, args.yield_tnt, seed=None if args.seed == 0 else args.seed)
        return 0

    current_yield = [args.yield_tnt]

    def on_pick(world_pt):
        center = point_to_voxel(world_pt, world)
        seed = next_seed()
        post_explosion(world, center, current_yield[0], seed)
        viewer.refresh(world.mat)
        print(f"[boom] center={center} yield={current_yield[0]:.1f} seed={seed}")

    viewer.build_from(world.mat)
    viewer.set_pick_callback(on_pick)

    pl = getattr(viewer, "pl", None)
    if pl is not None:
        snap = [world.snapshot()]

        def bigger():
            current_yield[0] = min(current_yield[0] * 1.5, 1e6)
            print(f"[yield] {current_yield[0]:.1f}")

        def smaller():
            current_yield[0] = max(current_yield[0] / 1.5, 0.1)
            print(f"[yield] {current_yield[0]:.1f}")

        def reset():
            world.restore(snap[0])
            viewer.refresh(world.mat)
            print("[reset] terrain restored")

        pl.add_key_event("Prior", bigger)    # Page Up
        pl.add_key_event("Next", smaller)    # Page Down
        pl.add_key_event("r", reset)
        print("Controls: left-drag = rotate | hover + P = detonate | "
              "Page Up/Down = yield | r = reset terrain")

    viewer.show()
    return 0
