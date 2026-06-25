import numpy as np

from .. import config
from ..materials import COLOR_RGB, ALPHA, IS_SOLID

try:
    import matplotlib.pyplot as plt
    _HAVE = True
except Exception:  # pragma: no cover
    plt = None
    _HAVE = False


def _crop(mat):
    m = config.MPL_MAX_DIM
    if max(mat.shape[0], mat.shape[1], mat.shape[2]) <= m:
        return mat, (0, 0, 0)
    nx, ny, nz = mat.shape
    cx, cy = nx // 2, ny // 2
    x0 = max(cx - m // 2, 0)
    y0 = max(cy - m // 2, 0)
    print(f"[voxelboom] matplotlib fallback: cropping {mat.shape} -> {m}^3 region for speed.")
    return mat[x0:x0 + m, y0:y0 + m, :min(nz, m)], (x0, y0, 0)


class MatplotlibViewer:
    def __init__(self):
        if not _HAVE:
            raise ImportError("matplotlib is not installed (pip install matplotlib)")
        self.fig = None
        self.ax3d = None
        self.ax2d = None
        self.mat = None
        self._pick_cb = None
        self._offset = (0, 0, 0)

    def _facecolors(self, m):
        filled = m != 0
        colors = np.zeros(m.shape + (4,), dtype=float)
        for mid in np.unique(m):
            if mid == 0:
                continue
            colors[m == mid] = (*COLOR_RGB[mid], ALPHA[mid])
        return filled, colors

    def _draw(self, mat):
        m, self._offset = _crop(mat)
        filled, colors = self._facecolors(m)
        self.ax3d.clear()
        self.ax3d.voxels(filled, facecolors=colors, edgecolor="k", linewidth=0.2)
        self.ax3d.set_box_aspect(m.shape)
        self.ax3d.set_title("voxelboom (matplotlib fallback)")
        height = np.where(IS_SOLID[mat].any(axis=2),
                          mat.shape[2] - 1 - np.argmax(IS_SOLID[mat][:, :, ::-1], axis=2) + 1,
                          0)
        self.ax2d.clear()
        self.ax2d.imshow(height.T, origin="lower", cmap="terrain")
        self.ax2d.set_title("click here to detonate (top-down)")

    def build_from(self, mat):
        self.mat = mat
        self.fig = plt.figure(figsize=(12, 6))
        self.ax3d = self.fig.add_subplot(1, 2, 1, projection="3d")
        self.ax2d = self.fig.add_subplot(1, 2, 2)
        self._draw(mat)

    def refresh(self, mat):
        self.mat = mat
        self._draw(mat)
        self.fig.canvas.draw_idle()

    def set_pick_callback(self, fn):
        self._pick_cb = fn
        self.fig.canvas.mpl_connect("button_press_event", self._on_click)

    def _on_click(self, event):
        if self._pick_cb is None or event.inaxes is not self.ax2d:
            return
        if event.xdata is None or event.ydata is None:
            return
        x = int(round(event.xdata))
        y = int(round(event.ydata))
        nx, ny, nz = self.mat.shape
        x = min(max(x, 0), nx - 1)
        y = min(max(y, 0), ny - 1)
        col = IS_SOLID[self.mat[x, y, :]]
        surf = int((nz - 1) - np.argmax(col[::-1])) if col.any() else 0
        world_pt = np.array([x + 0.5, y + 0.5, surf + 0.5]) * config.SPACING + config.ORIGIN
        self._pick_cb(world_pt)

    def show(self):
        plt.show()
