package dev.spark3d.gfx;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dev.spark3d.gfx.Gltf.Image;
import dev.spark3d.gfx.Gltf.MeshSkin;
import dev.spark3d.gfx.Gltf.Primitive;
import dev.spark3d.gfx.Gltf.Skin;

/**
 * One GPU mesh that came out of a glTF primitive.
 *
 * <p>Two vertex layouts, chosen by {@code skinned}:
 * <ul>
 *   <li><b>static</b> — position, normal, uv, baked shade. Stride 32, instance
 *       attributes layered at locations 4..8 by the owning batch.</li>
 *   <li><b>skinned</b> — position, normal, joints, weights, uv. Stride 64, joint
 *       palette supplied per draw.</li>
 * </ul>
 */
public final class GltfMesh {

    public static final int MAX_JOINTS = 64;

    public final int vao;
    public final int indexCount;
    public final float[] bounds;   // minX, minY, minZ, maxX, maxY, maxZ
    public final int material;
    public final int texture;      // -1 when the material has no texture
    public final boolean skinned;
    public final int jointCount;

    private final int vbo;
    private final int ebo;
    private final int stride;

    private float[] palette = new float[MAX_JOINTS * 16];

    private GltfMesh(
            int vao, int vbo, int ebo, int indexCount, int stride,
            float[] bounds, int material, int texture, boolean skinned, int jointCount) {
        this.vao = vao;
        this.vbo = vbo;
        this.ebo = ebo;
        this.indexCount = indexCount;
        this.stride = stride;
        this.bounds = bounds;
        this.material = material;
        this.texture = texture;
        this.skinned = skinned;
        this.jointCount = jointCount;
    }

    // ------------------------------------------------------------------ build

    public static GltfMesh skinned(Gltf gltf, MeshSkin msk, Skin skin, int skinIndex) {
        Primitive src = gltf.skinnedPrimitive(msk, skin);
        int n = src.positions.length / 3;
        float[] verts = new float[n * 16];
        for (int v = 0; v < n; v++) {
            int o = v * 16;
            verts[o] = src.positions[v * 3];
            verts[o + 1] = src.positions[v * 3 + 1];
            verts[o + 2] = src.positions[v * 3 + 2];
            verts[o + 3] = src.normals[v * 3];
            verts[o + 4] = src.normals[v * 3 + 1];
            verts[o + 5] = src.normals[v * 3 + 2];
            verts[o + 6] = src.joints[v * 4];
            verts[o + 7] = src.joints[v * 4 + 1];
            verts[o + 8] = src.joints[v * 4 + 2];
            verts[o + 9] = src.joints[v * 4 + 3];
            verts[o + 10] = src.weights[v * 4];
            verts[o + 11] = src.weights[v * 4 + 1];
            verts[o + 12] = src.weights[v * 4 + 2];
            verts[o + 13] = src.weights[v * 4 + 3];
            verts[o + 14] = src.uvs[v * 2];
            verts[o + 15] = src.uvs[v * 2 + 1];
        }

        int vao = Gl.genVertexArray();
        GLES30.glBindVertexArray(vao);
        int vbo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.length * 4, toBuffer(verts), GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 64, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 64, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, 64, 24);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, 64, 40);
        GLES30.glEnableVertexAttribArray(4);
        GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, 64, 56);
        int ebo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo);
        short[] idx = toIndices(src.indices);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.length * 2, toShortBuffer(idx), GLES30.GL_STATIC_DRAW);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);

        Gltf.Material mat = gltf.materials.get(src.material);
        int tex = -1;
        if (mat != null && mat.baseColorImage >= 0 && mat.baseColorImage < gltf.images.size()) {
            tex = texture(gltf.images.get(mat.baseColorImage));
        }

        return new GltfMesh(vao, vbo, ebo, idx.length, 64,
                boundsOf(src), src.material, tex, true, skin.joints.length);
    }

    public static GltfMesh staticMesh(Gltf gltf, Primitive prim, int material, int texture) {
        int n = prim.positions.length / 3;
        float[] verts = new float[n * 8];
        for (int v = 0; v < n; v++) {
            int o = v * 8;
            verts[o] = prim.positions[v * 3];
            verts[o + 1] = prim.positions[v * 3 + 1];
            verts[o + 2] = prim.positions[v * 3 + 2];
            verts[o + 3] = prim.normals[v * 3];
            verts[o + 4] = prim.normals[v * 3 + 1];
            verts[o + 5] = prim.normals[v * 3 + 2];
            if (prim.uvs != null) {
                verts[o + 6] = prim.uvs[v * 2];
                verts[o + 7] = prim.uvs[v * 2 + 1];
            } else {
                verts[o + 6] = 0f;
                verts[o + 7] = 0f;
            }
        }

        int vao = Gl.genVertexArray();
        GLES30.glBindVertexArray(vao);
        int vbo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.length * 4, toBuffer(verts), GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 32, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 32, 24);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, 32, 28);
        int ebo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo);
        short[] idx = toIndices(prim.indices);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.length * 2, toShortBuffer(idx), GLES30.GL_STATIC_DRAW);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);

        return new GltfMesh(vao, vbo, ebo, idx.length, 32, boundsOf(prim), material, texture, false, 0);
    }

    // -------------------------------------------------------------------- io

    private static float[] boundsOf(Primitive p) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        int n = p.positions.length / 3;
        for (int v = 0; v < n; v++) {
            float x = p.positions[v * 3], y = p.positions[v * 3 + 1], z = p.positions[v * 3 + 2];
            if (x < b[0]) b[0] = x;
            if (y < b[1]) b[1] = y;
            if (z < b[2]) b[2] = z;
            if (x > b[3]) b[3] = x;
            if (y > b[4]) b[4] = y;
            if (z > b[5]) b[5] = z;
        }
        if (b[0] > b[3]) return new float[6];
        return b;
    }

    private static ByteBuffer toBuffer(float[] verts) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(verts.length * 4);
        bb.order(java.nio.ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(verts);
        bb.position(0);
        return bb;
    }

    private static ByteBuffer toShortBuffer(short[] idx) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(idx.length * 2);
        bb.order(java.nio.ByteOrder.nativeOrder());
        bb.asShortBuffer().put(idx);
        bb.position(0);
        return bb;
    }

    private static short[] toIndices(int[] indices) {
        short[] out = new short[indices.length];
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] > 65535) {
                throw new IllegalArgumentException("glTF index > 65535 needs a 32-bit element buffer, unsupported");
            }
            out[i] = (short) indices[i];
        }
        return out;
    }

    public static int texture(Image image) {
        if (image == null || image.width <= 0 || image.height <= 0) return -1;
        int tex = Gl.genTexture();
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        int internal = "image/jpeg".equals(image.mimeType) ? GLES30.GL_RGB : GLES30.GL_RGBA;
        ByteBuffer bb = ByteBuffer.allocateDirect(image.data.length);
        bb.order(ByteOrder.nativeOrder());
        for (int i = 0; i < image.data.length; i++) bb.put(image.data[i]);
        bb.position(0);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internal, image.width, image.height, 0,
                internal, GLES30.GL_UNSIGNED_BYTE, bb);
        return tex;
    }

    // ------------------------------------------------------------------- gl

    public void bind() {
        GLES30.glBindVertexArray(vao);
    }

    /**
     * Draw this mesh. Skinned meshes take a joint palette plus a model matrix
     * and draw one character; static meshes take an instanced instance buffer
     * (the owning batch has bound the instance attribs at locations 4..8).
     */
    public void draw(Shader shader, float[] modelMatrix, float[] joints, int jointCount,
                     boolean instanced, float[] instances, int instanceCount) {
        shader.use();
        shader.setMat4("uModel", modelMatrix);
        if (skinned) {
            shader.setMat4Array("uJoints", jointCount, joints);
            shader.set1i("uJointCount", jointCount);
        }
        shader.setMat4("uViewProj", shaderViewProj);
        shader.set3f("uCamPos", shaderCamX, shaderCamY, shaderCamZ);
        shader.set3f("uSunDir", shaderSunX, shaderSunY, shaderSunZ);
        shader.set3f("uFogColor", shaderFogR, shaderFogG, shaderFogB);
        shader.set1f("uFogStart", shaderFogStart);
        shader.set1f("uFogEnd", shaderFogEnd);
        shader.set1f("uTime", shaderTime);
        shader.set4f("uBaseColor", shaderBaseR, shaderBaseG, shaderBaseB, shaderBaseA);
        if (texture >= 0) {
            Gl.bindTexture(0, texture);
        } else {
            Gl.bindTexture(0, Gl.whiteTexture());
        }
        shader.set1i("uTex", 0);

        GLES30.glBindVertexArray(vao);
        if (instanced) {
            GLES30.glDrawElementsInstanced(
                    GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0, instanceCount);
        } else {
            GLES30.glDrawElements(
                    GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0);
        }
        GLES30.glBindVertexArray(0);
    }

    private float[] shaderViewProj = new float[16];
    private float shaderCamX, shaderCamY, shaderCamZ;
    private float shaderSunX, shaderSunY, shaderSunZ;
    private float shaderFogR, shaderFogG, shaderFogB;
    private float shaderFogStart, shaderFogEnd;
    private float shaderTime;
    private float shaderBaseR, shaderBaseG, shaderBaseB, shaderBaseA;

    public void setFrame(float[] viewProj, float camX, float camY, float camZ, float sunX, float sunY,
                        float sunZ, float fogR, float fogG, float fogB, float fogStart, float fogEnd, float time,
                        float baseR, float baseG, float baseB, float baseA) {
        shaderViewProj = viewProj;
        shaderCamX = camX; shaderCamY = camY; shaderCamZ = camZ;
        shaderSunX = sunX; shaderSunY = sunY; shaderSunZ = sunZ;
        shaderFogR = fogR; shaderFogG = fogG; shaderFogB = fogB;
        shaderFogStart = fogStart; shaderFogEnd = fogEnd;
        shaderTime = time;
        shaderBaseR = baseR; shaderBaseG = baseG; shaderBaseB = baseB; shaderBaseA = baseA;
    }

    public void dispose() {
        GLES30.glDeleteVertexArrays(1, new int[]{vao}, 0);
        GLES30.glDeleteBuffers(1, new int[]{vbo}, 0);
        GLES30.glDeleteBuffers(1, new int[]{ebo}, 0);
    }
}
