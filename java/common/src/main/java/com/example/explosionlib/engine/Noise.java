package com.example.explosionlib.engine;

public final class Noise {
    private Noise() {}

    static long splitmix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        long z = x;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static double hash01(long ix, long iy, long iz, long seed) {
        long h = splitmix64(ix * 0x9E3779B97F4A7C15L ^ seed);
        h = splitmix64(h ^ (iy * 0xC2B2AE3D27D4EB4FL));
        h = splitmix64(h ^ (iz * 0x165667B19E3779F9L));
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    public static final class ValueNoise3D {
        private final long seed;

        public ValueNoise3D(long seed) {
            this.seed = seed & 0x7FFFFFFFL;
        }

        private double lattice(long ix, long iy, long iz) {
            return hash01(ix, iy, iz, seed) * 2.0 - 1.0;
        }

        public double sample(double x, double y, double z) {
            long x0 = (long) Math.floor(x), y0 = (long) Math.floor(y), z0 = (long) Math.floor(z);
            double xf = x - x0, yf = y - y0, zf = z - z0;
            long x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
            double u = xf * xf * (3.0 - 2.0 * xf);
            double v = yf * yf * (3.0 - 2.0 * yf);
            double w = zf * zf * (3.0 - 2.0 * zf);
            double c000 = lattice(x0, y0, z0), c100 = lattice(x1, y0, z0);
            double c010 = lattice(x0, y1, z0), c110 = lattice(x1, y1, z0);
            double c001 = lattice(x0, y0, z1), c101 = lattice(x1, y0, z1);
            double c011 = lattice(x0, y1, z1), c111 = lattice(x1, y1, z1);
            double x00 = c000 + u * (c100 - c000), x10 = c010 + u * (c110 - c010);
            double x01 = c001 + u * (c101 - c001), x11 = c011 + u * (c111 - c011);
            double y0v = x00 + v * (x10 - x00), y1v = x01 + v * (x11 - x01);
            return y0v + w * (y1v - y0v);
        }

        public double fbm(double x, double y, double z, int octaves) {
            double total = 0.0, amp = 1.0, freq = 1.0, norm = 0.0;
            for (int i = 0; i < octaves; i++) {
                total += amp * sample(x * freq, y * freq, z * freq);
                norm += amp;
                amp *= 0.5;
                freq *= 2.0;
            }
            return total / norm;
        }
    }

    public static final class DetRandom {
        private long s;

        public DetRandom(long seed) {
            this.s = seed;
        }

        public long nextLong() {
            s += 0x9E3779B97F4A7C15L;
            long z = s;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        public double nextDouble() {
            return (nextLong() >>> 11) * (1.0 / (1L << 53));
        }

        public int nextInt(int bound) {
            return (int) (nextDouble() * bound);
        }

        public double nextGaussian() {
            double u1 = Math.max(nextDouble(), 1e-12);
            double u2 = nextDouble();
            return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        }
    }
}
