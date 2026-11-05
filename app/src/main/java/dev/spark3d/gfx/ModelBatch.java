package dev.spark3d.gfx;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Instanced batch for a baked glTF static mesh — the "flock of real ducks" path.
 *
 * <p>One GltfMesh (already baked from a node's world rotation/scale) plus N
 * instances in an 18-float layout identical to {@link InstanceBatch}:
 * offset vec3, quaternion vec4, scale vec3, colour+emissive vec4, anim vec4.
 * One draw call, one VAO, one instance buffer — thousands of real models with
 * real textures at 60 fps.
 */
public final class ModelBatch {

    public static final int FLOATS = 18;

    private final int capacity;
    private final float[] data;
    private final FloatBuffer staging;

    private final GltfMesh mesh;
    private final int instanceVbo;

    public ModelBatch(GltfMesh mesh, int capacity) {
        this.mesh = mesh;
        this.capacity = capacity;
        this.data = new float[capacity * FLOATS];
        this.staging = ByteBuffer
                .allocateDirect(capacity * FLOATS * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        int[] vboIds = new int[1];
        GLES30.glGenBuffers(1, vboIds, 0);
        instanceVbo = vboIds[0];
        GLES30.glBindVertexArray(mesh.vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacity * FLOATS * 4, null, GLES30.GL_STREAM_DRAW);
        int stride = FLOATS * 4;
        GLES30.glEnableVertexAttribArray(4);
        GLES30.glVertexAttribPointer(4, 3, GLES30.GL_FLOAT, false, stride, 0);
        GLES30.glVertexAttribDivisor(4, 1);
        GLES30.glEnableVertexAttribArray(5);
        GLES30.glVertexAttribPointer(5, 4, GLES30.GL_FLOAT, false, stride, 12);
        GLES30.glVertexAttribDivisor(5, 1);
        GLES30.glEnableVertexAttribArray(6);
        GLES30.glVertexAttribPointer(6, 3, GLES30.GL_FLOAT, false, stride, 28);
        GLES30.glVertexAttribDivisor(6, 1);
        GLES30.glEnableVertexAttribArray(7);
        GLES30.glVertexAttribPointer(7, 4, GLES30.GL_FLOAT, false, stride, 40);
        GLES30.glVertexAttribDivisor(7, 1);
        GLES30.glEnableVertexAttribArray(8);
        GLES30.glVertexAttribPointer(8, 4, GLES30.GL_FLOAT, false, stride, 56);
        GLES30.glVertexAttribDivisor(8, 1);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public int count;

    public int capacity() {
        return capacity;
    }

    public GltfMesh mesh() {
        return mesh;
    }

    public int indexCount() {
        return mesh.indexCount;
    }

    public void clear() {
        count = 0;
    }

    public void add(
            float x, float y, float z,
            float qx, float qy, float qz, float qw,
            float sx, float sy, float sz,
            float r, float g, float b, float emissive,
            float phase, float bobSpeed, float bobAmp, float spin) {
        if (count >= capacity) return;
        int o = count * FLOATS;
        float[] d = data;
        d[o] = x; d[o + 1] = y; d[o + 2] = z;
        d[o + 3] = qx; d[o + 4] = qy; d[o + 5] = qz; d[o + 6] = qw;
        d[o + 7] = sx; d[o + 8] = sy; d[o + 9] = sz;
        d[o + 10] = r; d[o + 11] = g; d[o + 12] = b; d[o + 13] = emissive;
        d[o + 14] = phase; d[o + 15] = bobSpeed; d[o + 16] = bobAmp; d[o + 17] = spin;
        count++;
    }

    public void flush() {
        if (count == 0) return;
        int floats = count * FLOATS;
        staging.clear();
        staging.put(data, 0, floats);
        staging.position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacity * FLOATS * 4, null, GLES30.GL_STREAM_DRAW);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, floats * 4, staging);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public void draw(Shader shader, float[] viewProj, float camX, float camY, float camZ, float sunX, float sunY,
                     float sunZ, float fogR, float fogG, float fogB, float fogStart, float fogEnd, float time,
                     float baseR, float baseG, float baseB, float baseA) {
        shader.use();
        shader.setMat4("uViewProj", viewProj);
        shader.set3f("uCamPos", camX, camY, camZ);
        shader.set3f("uSunDir", sunX, sunY, sunZ);
        shader.set3f("uFogColor", fogR, fogG, fogB);
        shader.set1f("uFogStart", fogStart);
        shader.set1f("uFogEnd", fogEnd);
        shader.set1f("uTime", time);
        shader.set4f("uBaseColor", baseR, baseG, baseB, baseA);
        if (mesh.texture >= 0) {
            Gl.bindTexture(0, mesh.texture);
        } else {
            Gl.bindTexture(0, Gl.whiteTexture());
        }
        shader.set1i("uTex", 0);

        GLES30.glBindVertexArray(mesh.vao);
        GLES30.glDrawElementsInstanced(
                GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_SHORT, 0, count);
        GLES30.glBindVertexArray(0);
    }

    public void dispose() {
        GLES30.glDeleteBuffers(1, new int[]{instanceVbo}, 0);
    }
}
