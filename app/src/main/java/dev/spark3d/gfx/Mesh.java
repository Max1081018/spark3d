package dev.spark3d.gfx;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * An indexed triangle mesh with an interleaved vertex format.
 *
 * <p>Layout per vertex, 32 bytes:
 * <pre>
 *   0  vec3  position
 *   12 vec3  normal
 *   24 float baked shade (a crude ambient term, 0..1)
 *   28 float padding — keeps the stride 32 bytes, which is a cache line half
 * </pre>
 *
 * <p>Meshes are generated at runtime. A 6-file asset pipeline to ship a cube is
 * not worth the APK space; the crystal, sphere and prism builders below are
 * about 120 lines total and produce better-looking low-poly shapes than anything
 * I would have exported from Blender.
 */
public final class Mesh {

    public static final int FLOATS_PER_VERTEX = 8;
    public static final int STRIDE_BYTES = FLOATS_PER_VERTEX * 4;

    private final int vbo;
    private final int ebo;
    private final int indexCount;

    private Mesh(float[] vertices, short[] indices) {
        indexCount = indices.length;

        FloatBuffer vb = ByteBuffer
                .allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vb.put(vertices).position(0);

        ShortBuffer ib = ByteBuffer
                .allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        ib.put(indices).position(0);

        vbo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.length * 4, vb, GLES30.GL_STATIC_DRAW);

        ebo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.length * 2, ib, GLES30.GL_STATIC_DRAW);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Bind this mesh's buffers and wire attributes 0..2 into the currently bound
     * VAO. Called by {@link InstanceBatch} and by the terrain, which each own a
     * VAO because they add their own extra attributes.
     */
    public void bindAttributes() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, STRIDE_BYTES, 24);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo);
    }

    public int indexCount() {
        return indexCount;
    }

    /** Wrap caller-generated vertex/index arrays (used by the terrain). */
    public static Mesh create(float[] vertices, short[] indices) {
        return new Mesh(vertices, indices);
    }

    public void dispose() {
        GLES30.glDeleteBuffers(1, new int[]{vbo}, 0);
        GLES30.glDeleteBuffers(1, new int[]{ebo}, 0);
    }

    // ------------------------------------------------------------------ builders

    /** Growable vertex/index accumulator. */
    private static final class Builder {
        float[] v = new float[FLOATS_PER_VERTEX * 256];
        short[] i = new short[384];
        int nv;
        int ni;

        int vert(float x, float y, float z, float nx, float ny, float nz, float shade) {
            int need = (nv + 1) * FLOATS_PER_VERTEX;
            if (need > v.length) {
                float[] bigger = new float[v.length * 2];
                System.arraycopy(v, 0, bigger, 0, v.length);
                v = bigger;
            }
            int o = nv * FLOATS_PER_VERTEX;
            v[o] = x;
            v[o + 1] = y;
            v[o + 2] = z;
            v[o + 3] = nx;
            v[o + 4] = ny;
            v[o + 5] = nz;
            v[o + 6] = shade;
            v[o + 7] = 0f;
            return nv++;
        }

        void tri(int a, int b, int c) {
            if ((ni + 3) > i.length) {
                short[] bigger = new short[i.length * 2];
                System.arraycopy(i, 0, bigger, 0, i.length);
                i = bigger;
            }
            i[ni++] = (short) a;
            i[ni++] = (short) b;
            i[ni++] = (short) c;
        }

        void quad(int a, int b, int c, int d) {
            tri(a, b, c);
            tri(a, c, d);
        }

        Mesh build() {
            float[] vv = new float[nv * FLOATS_PER_VERTEX];
            System.arraycopy(v, 0, vv, 0, vv.length);
            short[] ii = new short[ni];
            System.arraycopy(i, 0, ii, 0, ni);
            return new Mesh(vv, ii);
        }
    }

    /**
     * A hexagonal bipyramid: the crystal archetype. Sharp point up, short taper
     * down, flat shaded. 12 triangles — cheap enough to draw ten thousand of.
     */
    public static Mesh crystal() {
        Builder b = new Builder();
        int sides = 6;
        float ringY = 0.05f;
        float ringR = 0.40f;
        float topY = 1.15f;
        float botY = -0.32f;

        for (int s = 0; s < sides; s++) {
            float a0 = (float) (s * Math.PI * 2 / sides);
            float a1 = (float) ((s + 1) * Math.PI * 2 / sides);
            float x0 = (float) Math.cos(a0) * ringR;
            float z0 = (float) Math.sin(a0) * ringR;
            float x1 = (float) Math.cos(a1) * ringR;
            float z1 = (float) Math.sin(a1) * ringR;

            // Upper face, apex at top.
            float[] nUp = faceNormal(x0, ringY, z0, x1, ringY, z1, 0f, topY, 0f);
            b.tri(
                    b.vert(x0, ringY, z0, nUp[0], nUp[1], nUp[2], 1.0f),
                    b.vert(0f, topY, 0f, nUp[0], nUp[1], nUp[2], 1.0f),
                    b.vert(x1, ringY, z1, nUp[0], nUp[1], nUp[2], 1.0f));

            // Lower face, apex at bottom.
            float[] nDn = faceNormal(x1, ringY, z1, x0, ringY, z0, 0f, botY, 0f);
            b.tri(
                    b.vert(x1, ringY, z1, nDn[0], nDn[1], nDn[2], 0.55f),
                    b.vert(0f, botY, 0f, nDn[0], nDn[1], nDn[2], 0.55f),
                    b.vert(x0, ringY, z0, nDn[0], nDn[1], nDn[2], 0.55f));
        }
        return b.build();
    }

    /** Icosphere. subdiv 0 = 20 tris, 1 = 80, 2 = 320. */
    public static Mesh sphere(int subdiv) {
        float t = (1f + (float) Math.sqrt(5f)) / 2f;
        float[][] base = {
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1},
        };
        int[][] faces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1},
        };

        int n = 12;
        float[] px = new float[4096];
        float[] py = new float[4096];
        float[] pz = new float[4096];
        for (int i = 0; i < 12; i++) {
            float l = (float) Math.sqrt(base[i][0] * base[i][0] + base[i][1] * base[i][1] + base[i][2] * base[i][2]);
            px[i] = base[i][0] / l;
            py[i] = base[i][1] / l;
            pz[i] = base[i][2] / l;
        }
        int[] f = new int[faces.length * 3];
        for (int i = 0; i < faces.length; i++) {
            f[i * 3] = faces[i][0];
            f[i * 3 + 1] = faces[i][1];
            f[i * 3 + 2] = faces[i][2];
        }
        int fn = faces.length;

        for (int s = 0; s < subdiv; s++) {
            int[] nf = new int[fn * 12];
            int nfn = 0;
            java.util.HashMap<Long, Integer> mid = new java.util.HashMap<>();
            // A mutable count: midpoints are appended while a face is being
            // processed, so the write index has to advance across calls.
            int[] count = {n};
            for (int k = 0; k < fn; k++) {
                int a = f[k * 3], bb = f[k * 3 + 1], c = f[k * 3 + 2];
                int ab = midpoint(mid, a, bb, px, py, pz, count);
                int bc = midpoint(mid, bb, c, px, py, pz, count);
                int ca = midpoint(mid, c, a, px, py, pz, count);
                nf[nfn++] = a; nf[nfn++] = ab; nf[nfn++] = ca;
                nf[nfn++] = bb; nf[nfn++] = bc; nf[nfn++] = ab;
                nf[nfn++] = c; nf[nfn++] = ca; nf[nfn++] = bc;
                nf[nfn++] = ab; nf[nfn++] = bc; nf[nfn++] = ca;
            }
            n = count[0];
            f = nf;
            fn = nfn / 3;
        }

        Builder bld = new Builder();
        for (int i = 0; i < n; i++) {
            bld.vert(px[i], py[i], pz[i], px[i], py[i], pz[i], 0.5f + 0.5f * py[i]);
        }
        for (int k = 0; k < fn; k++) {
            bld.tri(f[k * 3], f[k * 3 + 1], f[k * 3 + 2]);
        }
        return bld.build();
    }

    private static int midpoint(
            java.util.HashMap<Long, Integer> cache, int a, int b,
            float[] px, float[] py, float[] pz, int[] count) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        long key = ((long) lo << 32) | (hi & 0xffffffffL);
        Integer hit = cache.get(key);
        if (hit != null) return hit;
        int n = count[0];
        float x = (px[lo] + px[hi]) * 0.5f;
        float y = (py[lo] + py[hi]) * 0.5f;
        float z = (pz[lo] + pz[hi]) * 0.5f;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        px[n] = x / l;
        py[n] = y / l;
        pz[n] = z / l;
        cache.put(key, n);
        count[0] = n + 1;
        return n;
    }

    /** A hex column, used for the tall silhouettes on the plain. */
    public static Mesh prism(int sides) {
        Builder b = new Builder();
        float half = 0.5f;
        for (int s = 0; s < sides; s++) {
            float a0 = (float) (s * Math.PI * 2 / sides);
            float a1 = (float) ((s + 1) * Math.PI * 2 / sides);
            float x0 = (float) Math.cos(a0);
            float z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1);
            float z1 = (float) Math.sin(a1);

            float nx = (float) Math.cos((a0 + a1) * 0.5f);
            float nz = (float) Math.sin((a0 + a1) * 0.5f);

            // Counter-clockwise seen from outside: bottom-left, top-left,
            // top-right, bottom-right.
            b.quad(
                    b.vert(x0, -half, z0, nx, 0f, nz, 0.5f),
                    b.vert(x0, half, z0, nx, 0f, nz, 1.0f),
                    b.vert(x1, half, z1, nx, 0f, nz, 1.0f),
                    b.vert(x1, -half, z1, nx, 0f, nz, 0.5f));
        }
        // Single top fan, wound counter-clockwise seen from above.
        int center = b.vert(0f, half, 0f, 0f, 1f, 0f, 1.0f);
        int first = b.nv;
        for (int s = 0; s < sides; s++) {
            float a = (float) (s * Math.PI * 2 / sides);
            b.vert((float) Math.cos(a), half, (float) Math.sin(a), 0f, 1f, 0f, 1.0f);
        }
        for (int s = 0; s < sides; s++) {
            b.tri(center, first + (s + 1) % sides, first + s);
        }
        return b.build();
    }

    /** Unit cube with per-face normals. */
    public static Mesh cube() {
        Builder b = new Builder();
        // +X, -X, +Y, -Y, +Z, -Z
        float[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        float[][][] q = {
                {{0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {0.5f, 0.5f, 0.5f}},
                {{-0.5f, -0.5f, -0.5f}, {-0.5f, -0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, -0.5f}},
                {{-0.5f, 0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}},
                {{-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, 0.5f}},
                {{-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}},
                {{0.5f, -0.5f, -0.5f}, {-0.5f, -0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}},
        };
        for (int f = 0; f < 6; f++) {
            float shade = f == 2 ? 1.0f : f == 3 ? 0.45f : 0.75f;
            int a = b.vert(q[f][0][0], q[f][0][1], q[f][0][2], n[f][0], n[f][1], n[f][2], shade);
            int bb = b.vert(q[f][1][0], q[f][1][1], q[f][1][2], n[f][0], n[f][1], n[f][2], shade);
            int c = b.vert(q[f][2][0], q[f][2][1], q[f][2][2], n[f][0], n[f][1], n[f][2], shade);
            int d = b.vert(q[f][3][0], q[f][3][1], q[f][3][2], n[f][0], n[f][1], n[f][2], shade);
            b.quad(a, bb, c, d);
        }
        return b.build();
    }

    /** Normal of a triangle, returned as a 3-float array (build-time only). */
    private static float[] faceNormal(
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az;
        float vx = cx - ax, vy = cy - ay, vz = cz - az;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) {
            return new float[]{0f, 1f, 0f};
        }
        return new float[]{nx / l, ny / l, nz / l};
    }
}
