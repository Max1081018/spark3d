package dev.spark3d.gfx;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * GPU instancing: one mesh, N transforms, one draw call.
 *
 * <p>This is the thing that makes "thousands of models" a real claim instead of
 * a marketing one. Twelve thousand crystals are twelve thousand entries in one
 * float array uploaded <em>once</em> at load, then issued every frame as a single
 * {@code glDrawElementsInstanced}. No per-object draw call, no matrix stack, no
 * CPU list rebuilt per frame — the GPU walks the instance buffer itself.
 *
 * <p>Per-instance data is 18 floats (72 bytes):
 * <pre>
 *   offset   vec3    //  0
 *   rotation vec4    // 12  quaternion
 *   scale    vec3    // 28  arbitrary and non-uniform, no LOD buckets
 *   colour   vec4    // 40  rgb + emissive
 *   anim     vec4    // 56  phase, bob speed, bob amplitude, spin speed
 * </pre>
 *
 * <p>{@link #count} is the number of instances <em>drawn</em>, and it can be
 * lowered at any time without touching the GPU buffer. That is what the adaptive
 * quality scaler uses: dropping from 12,000 to 6,000 instances costs one integer
 * assignment, not a re-upload.
 *
 * <p>Two modes matter in practice:
 * <ul>
 *   <li><b>static</b> — upload once, animate in the vertex shader. Used for the
 *       world's crystal field and rock spires.</li>
 *   <li><b>dynamic</b> — {@link #clear()} and refill each frame. Used for the
 *       few hundred things that genuinely move under CPU control.</li>
 * </ul>
 */
public final class InstanceBatch {

    public static final int FLOATS = 18;

    private final int capacity;
    private final float[] data;
    private final FloatBuffer staging;

    private final int vao;
    private final int instanceVbo;
    private final int indexCount;
    private final Mesh mesh;

    private int filled;
    public int count;

    public InstanceBatch(Mesh mesh, int capacity) {
        this.mesh = mesh;
        this.capacity = capacity;
        this.indexCount = mesh.indexCount();
        this.data = new float[capacity * FLOATS];
        this.staging = ByteBuffer
                .allocateDirect(capacity * FLOATS * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        vao = Gl.genVertexArray();
        GLES30.glBindVertexArray(vao);
        mesh.bindAttributes();

        instanceVbo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo);
        GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                capacity * FLOATS * 4,
                null,
                GLES30.GL_STREAM_DRAW);

        int stride = FLOATS * 4;
        attrib(3, 3, stride, 0);    // offset
        attrib(4, 4, stride, 12);   // quaternion
        attrib(5, 3, stride, 28);   // scale
        attrib(6, 4, stride, 40);   // colour + emissive
        attrib(7, 4, stride, 56);   // animation

        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void attrib(int location, int size, int stride, int offset) {
        GLES30.glEnableVertexAttribArray(location);
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, stride, offset);
        GLES30.glVertexAttribDivisor(location, 1);
    }

    public void clear() {
        count = 0;
    }

    public int capacity() {
        return capacity;
    }

    public int indexCount() {
        return indexCount;
    }

    /** Number of instances ever written since the last {@link #clear()}. */
    public int filled() {
        return filled;
    }

    public void add(
            float x, float y, float z,
            float qx, float qy, float qz, float qw,
            float sx, float sy, float sz,
            float r, float g, float b, float emissive) {
        add(x, y, z, qx, qy, qz, qw, sx, sy, sz, r, g, b, emissive, 0f, 0f, 0f, 0f);
    }

    /**
     * Append one instance. Silently drops when full — a batch that resizes at
     * runtime is a batch that stalls the frame, and a hard cap is a much better
     * failure mode than a 40 ms hitch.
     */
    public void add(
            float x, float y, float z,
            float qx, float qy, float qz, float qw,
            float sx, float sy, float sz,
            float r, float g, float b, float emissive,
            float phase, float bobSpeed, float bobAmp, float spin) {
        if (count >= capacity) return;
        int o = count * FLOATS;
        float[] d = data;
        d[o] = x;      d[o + 1] = y;  d[o + 2] = z;
        d[o + 3] = qx; d[o + 4] = qy; d[o + 5] = qz; d[o + 6] = qw;
        d[o + 7] = sx; d[o + 8] = sy; d[o + 9] = sz;
        d[o + 10] = r; d[o + 11] = g; d[o + 12] = b; d[o + 13] = emissive;
        d[o + 14] = phase; d[o + 15] = bobSpeed; d[o + 16] = bobAmp; d[o + 17] = spin;
        count++;
        if (count > filled) filled = count;
    }

    /** Convenience for objects that only spin around Y. */
    public void addYaw(
            float x, float y, float z,
            float yaw, float scale,
            float r, float g, float b, float emissive) {
        addYaw(x, y, z, yaw, scale, scale, scale, r, g, b, emissive, 0f, 0f, 0f, 0f);
    }

    public void addYaw(
            float x, float y, float z,
            float yaw, float sx, float sy, float sz,
            float r, float g, float b, float emissive,
            float phase, float bobSpeed, float bobAmp, float spin) {
        float h = yaw * 0.5f;
        float s = (float) Math.sin(h);
        add(x, y, z, 0f, s, 0f, (float) Math.cos(h), sx, sy, sz,
                r, g, b, emissive, phase, bobSpeed, bobAmp, spin);
    }

    /** Convenience for objects with a full Euler rotation. */
    public void addEuler(
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float sx, float sy, float sz,
            float r, float g, float b, float emissive) {
        float cy = (float) Math.cos(yaw * 0.5f), sy2 = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f), sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f), sr = (float) Math.sin(roll * 0.5f);
        float qx = sr * cp * cy - cr * sp * sy2;
        float qy = cr * sp * cy + sr * cp * sy2;
        float qz = cr * cp * sy2 - sr * sp * cy;
        float qw = cr * cp * cy + sr * sp * sy2;
        add(x, y, z, qx, qy, qz, qw, sx, sy, sz, r, g, b, emissive);
    }

    /**
     * Upload the live prefix. Call once, after filling a static batch, or once
     * per frame for a dynamic one.
     */
    public void flush() {
        if (count == 0) return;
        upload(count);
    }

    /** Upload {@code count} instances even if more were written (static prep). */
    public void upload(int instances) {
        int n = Math.min(instances, count);
        if (n <= 0) return;
        int floats = n * FLOATS;
        staging.clear();
        staging.put(data, 0, floats);
        staging.position(0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo);
        // Orphan: tell the driver the old contents are dead so it hands us a new
        // block instead of waiting for the GPU to finish with the old one.
        GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                capacity * FLOATS * 4,
                null,
                GLES30.GL_STREAM_DRAW);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, floats * 4, staging);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public void draw() {
        draw(count);
    }

    /** Draw only the first {@code instances} of the resident buffer. */
    public void draw(int instances) {
        int n = Math.min(instances, capacity);
        if (n <= 0) return;
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawElementsInstanced(
                GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0, n);
        GLES30.glBindVertexArray(0);
    }

    public void dispose() {
        GLES30.glDeleteVertexArrays(1, new int[]{vao}, 0);
        GLES30.glDeleteBuffers(1, new int[]{instanceVbo}, 0);
        mesh.dispose();
    }
}
