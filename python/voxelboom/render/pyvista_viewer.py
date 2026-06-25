import numpy as np

from .. import config
from ..materials import COLOR_RGB, IS_SOLID

try:
    import pyvista as pv
    _HAVE = True
except Exception:  # pragma: no cover - exercised only without pyvista
    pv = None
    _HAVE = False


class PyVistaViewer:
    def __init__(self):
        if not _HAVE:
            raise ImportError("pyvista is not installed (pip install pyvista)")
        self.pl = pv.Plotter()
        self._actor = None
        self._pick_cb = None
        self.mat = None

    def _build_solid(self, mat):
        nx, ny, nz = mat.shape
        grid = pv.ImageData(
            dimensions=(nx + 1, ny + 1, nz + 1),   # +1: dimensions count POINTS
            spacing=tuple(config.SPACING),
            origin=tuple(config.ORIGIN),
        )
        grid.cell_data["material"] = mat.flatten(order="F").astype(np.float64)
        return grid.threshold(0.5, scalars="material")  # drop AIR (id 0)

    def _add(self, mat):
        solid = self._build_solid(mat)
        if solid.n_cells == 0:
            self._actor = None
            return
        try:
            mesh = solid.extract_surface(algorithm="dataset_surface")
        except TypeError:                      # older pyvista without the kwarg
            mesh = solid.extract_surface()
        if mesh.n_cells == 0 or "material" not in mesh.cell_data:
            mesh = solid
        ids = mesh.cell_data["material"].astype(np.int64)
        rgb = (COLOR_RGB[ids] * 255.0).astype(np.uint8)
        mesh.cell_data["rgb"] = rgb
        self._actor = self.pl.add_mesh(
            mesh, scalars="rgb", rgb=True, show_edges=config.SHOW_EDGES,
        )

    def build_from(self, mat):
        self.mat = mat
        self._add(mat)
        self.pl.add_axes()   # small XYZ orientation gizmo in the corner (no grid lines)

    def refresh(self, mat):
        self.mat = mat
        if self._actor is not None:
            self.pl.remove_actor(self._actor)   # threshold() returns a new grid each call
            self._actor = None
        self._add(mat)
        self.pl.render()                        # required while interactor is live

    def set_pick_callback(self, fn):
        self._pick_cb = fn
        self.pl.enable_surface_point_picking(
            callback=self._on_pick,
            use_picker=True,
            show_point=False,
            show_message="Hover over the terrain and press P to detonate",
            tolerance=0.025,
            left_clicking=False,
        )

    def _on_pick(self, *args):
        if self._pick_cb is None or not args:
            return
        point = args[0]
        picker = args[1] if len(args) > 1 else None
        if picker is not None and hasattr(picker, "GetPickPosition"):
            world_pt = np.asarray(picker.GetPickPosition(), dtype=float)
        else:
            world_pt = np.asarray(point, dtype=float)
        self._pick_cb(world_pt)

    def show(self):
        self.pl.show()
