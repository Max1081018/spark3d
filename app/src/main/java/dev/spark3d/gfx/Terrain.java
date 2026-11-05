package dev.spark3d.gfx;

import android.opengl.GLES30;

import dev.spark3d.engine.Noise;

/**
 * The ground: a single static indexed mesh built from fractal value noise.
 *
 * <p>Built once, uploaded once, drawn in one call. There are no tiles, no chunks
 * and no streaming, because a 680 x 680 world at ~6-unit resolution is 25k
 * triangles and fits comfortably in one buffer. Streaming would be the right
 * answer for a 10 km world; it is pure complexity for this one.
 *
 * <p>{@link #heightAt} is the same function used to build the mesh, so gameplay
 * placement and the visual surface can never disagree — the classic bug where
 * the player floats 40 cm above the ground is structurally impossible.
 */
public final class Terrain {

    public final float size;
    public final int resolution;

    private final Noise noise;
    private final Mesh mesh;
    private final int vao;

    public Terrain(long seed, float size, int resolution) {
        this.size = size;
        this.resolution = resolution;
        this.noise = new Noise((int) (seed ^ (seed >>> 32)));

        int verts = (resolution + 1) * (resolution + 1);
        float[] v = new float[verts * Mesh.FLOATS_PER_VERTEX];
        short[] idx = new short[resolution * resolution * 6];

        float cell = size / resolution;
        float half = size * 0.5f;
        float eps = cell * 0.5f;

        int p = 0;
        for (int j = 0; j <= resolution; j++) {
            float z = -half + j * cell;
            for (int i = 0; i <= resolution; i++) {
                float x = -half + i * cell;
                float y = heightAt(x, z);

                // Central differences for the normal.
                float hl = heightAt(x - eps, z);
                float hr = heightAt(x + eps, z);
                float hd = heightAt(x, z - eps);
                float hu = heightAt(x, z + eps);
                float nx = hl - hr;
                float nz = hd - hu;
                float ny = 2f * eps;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6f) len = 1f;
                nx /= len;
                ny /= len;
                nz /= len;

                // Baked shade: flatter ground catches more sky.
                float shade = 0.45f + 0.55f * ny;

                int o = p * Mesh.FLOATS_PER_VERTEX;
                v[o] = x;
                v[o + 1] = y;
                v[o + 2] = z;
                v[o + 3] = nx;
                v[o + 4] = ny;
                v[o + 5] = nz;
                v[o + 6] = shade;
                v[o + 7] = 0f;
                p++;
            }
        }

        int q = 0;
        int row = resolution + 1;
        for (int j = 0; j < resolution; j++) {
            for (int i = 0; i < resolution; i++) {
                int a = j * row + i;
                int b = a + 1;
                int c = a + row;
                int d = c + 1;
                // Counter-clockwise seen from above (+Y).
                idx[q++] = (short) a;
                idx[q++] = (short) c;
                idx[q++] = (short) b;
                idx[q++] = (short) b;
                idx[q++] = (short) c;
                idx[q++] = (short) d;
            }
        }

        mesh = Mesh.create(v, idx);
        vao = Gl.genVertexArray();
        GLES30.glBindVertexArray(vao);
        mesh.bindAttributes();
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
        Gl.check("Terrain build");
    }

    /**
     * Ground height at a world position. Deterministic, allocation-free, and the
     * single source of truth for both the mesh and gameplay.
     */
    public float heightAt(float x, float z) {
        float h = noise.fbm(x * 0.0075f, z * 0.0075f, 4, 2.03f, 0.5f) * 15f;
        h += noise.value(x * 0.045f, z * 0.045f) * 0.9f;

        // Flatten the spawn bowl so the first ten seconds are not a scramble.
        float d = (float) Math.sqrt(x * x + z * z);
        float f = (d - 22f) / 52f;
        if (f < 0f) f = 0f;
        if (f > 1f) f = 1f;
        f = f * f * (3f - 2f * f);
        return h * f;
    }

    /** Approximate surface normal, for placing objects upright. */
    public float slopeAt(float x, float z) {
        float e = 1.5f;
        float hl = heightAt(x - e, z);
        float hr = heightAt(x + e, z);
        float hd = heightAt(x, z - e);
        float hu = heightAt(x, z + e);
        float nx = hl - hr;
        float nz = hd - hu;
        float ny = 2f * e;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1e-6f ? 1f : ny / len;
    }

    public void draw(Shader shader) {
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawElements(
                GLES30.GL_TRIANGLES, mesh.indexCount(), GLES30.GL_UNSIGNED_SHORT, 0);
        GLES30.glBindVertexArray(0);
    }

    public int triangleCount() {
        return mesh.indexCount() / 3;
    }

    public void dispose() {
        GLES30.glDeleteVertexArrays(1, new int[]{vao}, 0);
        mesh.dispose();
    }
}
