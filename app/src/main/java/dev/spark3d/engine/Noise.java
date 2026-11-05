package dev.spark3d.engine;

/**
 * Deterministic value noise and fBm for terrain.
 *
 * <p>Not Perlin — value noise with quintic interpolation is a few lines, has no
 * lattice artefacts at the scales a heightmap actually uses, and costs about
 * half as much. The whole world is generated from a single seed, so the same
 * seed always produces the same map without storing anything.
 */
public final class Noise {

    private final int seed;

    public Noise(int seed) {
        this.seed = seed;
    }

    /** Integer hash → [0, 1). Deterministic across JVMs (no String.hashCode). */
    private float hash(int x, int y) {
        int h = seed;
        h ^= x * 0x27d4eb2d;
        h ^= y * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return (h >>> 8) / 16777216f;
    }

    private static float quintic(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /** Value noise in [-1, 1]. */
    public float value(float x, float y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;
        float u = quintic(xf);
        float v = quintic(yf);

        float a = hash(xi, yi);
        float b = hash(xi + 1, yi);
        float c = hash(xi, yi + 1);
        float d = hash(xi + 1, yi + 1);

        float top = a + (b - a) * u;
        float bot = c + (d - c) * u;
        return (top + (bot - top) * v) * 2f - 1f;
    }

    /** Fractal sum. {@code octaves} layers, each at double frequency and half amplitude. */
    public float fbm(float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0f;
        float amp = 1f;
        float norm = 0f;
        float fx = x;
        float fy = y;
        for (int i = 0; i < octaves; i++) {
            sum += value(fx, fy) * amp;
            norm += amp;
            amp *= gain;
            fx *= lacunarity;
            fy *= lacunarity;
        }
        return norm > 0f ? sum / norm : 0f;
    }
}
