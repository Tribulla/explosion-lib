import numpy as np

_U64 = np.uint64


def _splitmix64(x):
    with np.errstate(over="ignore"):
        x = x + _U64(0x9E3779B97F4A7C15)
        z = x
        z = (z ^ (z >> _U64(30))) * _U64(0xBF58476D1CE4E5B9)
        z = (z ^ (z >> _U64(27))) * _U64(0x94D049BB133111EB)
        z = z ^ (z >> _U64(31))
    return z


def hash01(ix, iy, iz, seed):
    with np.errstate(over="ignore"):
        ix = np.asarray(ix, dtype=np.int64).astype(_U64)
        iy = np.asarray(iy, dtype=np.int64).astype(_U64)
        iz = np.asarray(iz, dtype=np.int64).astype(_U64)
        s = _U64(int(seed) & 0xFFFFFFFFFFFFFFFF)
        h = _splitmix64(ix * _U64(0x9E3779B97F4A7C15) ^ s)
        h = _splitmix64(h ^ (iy * _U64(0xC2B2AE3D27D4EB4F)))
        h = _splitmix64(h ^ (iz * _U64(0x165667B19E3779F9)))
    # top 53 bits -> double in [0, 1)
    return (h >> _U64(11)).astype(np.float64) * (1.0 / float(1 << 53))


class ValueNoise3D:

    def __init__(self, seed):
        self.seed = int(seed) & 0x7FFFFFFF

    def _lattice(self, ix, iy, iz):
        return hash01(ix, iy, iz, self.seed) * 2.0 - 1.0

    def sample(self, p):
        p = np.asarray(p, dtype=np.float64)
        x, y, z = p[..., 0], p[..., 1], p[..., 2]
        x0 = np.floor(x)
        y0 = np.floor(y)
        z0 = np.floor(z)
        xf, yf, zf = x - x0, y - y0, z - z0
        x0i, y0i, z0i = x0.astype(np.int64), y0.astype(np.int64), z0.astype(np.int64)
        x1i, y1i, z1i = x0i + 1, y0i + 1, z0i + 1
        # smoothstep fade
        u = xf * xf * (3.0 - 2.0 * xf)
        v = yf * yf * (3.0 - 2.0 * yf)
        w = zf * zf * (3.0 - 2.0 * zf)
        c000 = self._lattice(x0i, y0i, z0i)
        c100 = self._lattice(x1i, y0i, z0i)
        c010 = self._lattice(x0i, y1i, z0i)
        c110 = self._lattice(x1i, y1i, z0i)
        c001 = self._lattice(x0i, y0i, z1i)
        c101 = self._lattice(x1i, y0i, z1i)
        c011 = self._lattice(x0i, y1i, z1i)
        c111 = self._lattice(x1i, y1i, z1i)
        x00 = c000 + u * (c100 - c000)
        x10 = c010 + u * (c110 - c010)
        x01 = c001 + u * (c101 - c001)
        x11 = c011 + u * (c111 - c011)
        y0v = x00 + v * (x10 - x00)
        y1v = x01 + v * (x11 - x01)
        return y0v + w * (y1v - y0v)


def fbm(noise, p, octaves=4, lacunarity=2.0, gain=0.5):
    p = np.asarray(p, dtype=np.float64)
    total = np.zeros(p.shape[:-1], dtype=np.float64)
    amp = 1.0
    freq = 1.0
    norm = 0.0
    for _ in range(octaves):
        total = total + amp * noise.sample(p * freq)
        norm += amp
        amp *= gain
        freq *= lacunarity
    return total / norm
