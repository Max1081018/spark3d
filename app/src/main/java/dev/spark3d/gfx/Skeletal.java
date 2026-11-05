package dev.spark3d.gfx;

import java.util.ArrayList;
import java.util.List;

import dev.spark3d.engine.Mat4;
import dev.spark3d.gfx.Gltf;
import dev.spark3d.gfx.Gltf.MeshSkin;
import dev.spark3d.gfx.Gltf.Skin;

/**
 * An animated character assembled from a skinned glTF model.
 *
 * <p>Per frame the skeleton is advanced and a joint palette is produced. Slot j
 * of the palette holds {@code J_global(j, t) * inverse(J_global(j, bind))} in its
 * upper 3x3 with translation applied — the per-joint matrix that turns a
 * bind-pose joint offset into animated world space. The vertex shader blends
 * four joints per vertex through the palette, then applies the model matrix.
 */
public final class Skeletal {

    public final Gltf gltf;
    public final int skinIndex;
    public final Skin skin;
    public final int jointCount;

    public final List<GltfMesh> meshes = new ArrayList<>();

    private final float[] palette = new float[GltfMesh.MAX_JOINTS * 16];
    private int paletteCount;

    public Skeletal(Gltf gltf, int skinIndex) {
        this.gltf = gltf;
        this.skinIndex = skinIndex;
        this.skin = gltf.skins.get(skinIndex);
        this.jointCount = skin.joints.length;
        List<MeshSkin> bindings = gltf.skinnedBindingsFor(skinIndex);
        for (MeshSkin msk : bindings) {
            GltfMesh m = GltfMesh.skinned(gltf, msk, skin, skinIndex);
            if (m != null) meshes.add(m);
        }
    }

    public int meshCount() {
        return meshes.size();
    }

    public int jointCount() {
        return jointCount;
    }

    /**
     * Advance the skeleton and write the joint palette the skin shader reads.
     * Call this after {@link Gltf#play} / {@link Gltf#advance}.
     */
    public void pose() {
        float[][] world = gltf.animatedWorldMatrices();
        float[][] bind = gltf.worldMatrices();
        int[] parents = gltf.parents();

        float[] out = new float[jointCount * 16];
        for (int j = 0; j < jointCount; j++) {
            int nodeIdx = skin.joints[j];
            float[] ja = world[nodeIdx];
            int p = parents[nodeIdx];
            float[] pa = p >= 0 ? world[p] : Mat4.create();
            float[] jb = bind[nodeIdx];
            float[] pab = p >= 0 ? bind[p] : Mat4.create();

            // Jrel(t) = J_global(j, t) * inverse(J_global(parent, t))
            // P        = Jrel(t) * inverse(Jrel(bind))
            //        = J_g(j,t) * inv(J_g(p,t)) * J_g(p,bind) * inv(J_g(j,bind))
            float[] invPa = new float[16];
            Mat4.invert(invPa, pa);
            float[] invJb = new float[16];
            Mat4.invert(invJb, jb);
            float[] t1 = new float[16];
            Mat4.multiply(t1, ja, invPa);
            float[] t2 = new float[16];
            Mat4.multiply(t2, t1, pa);
            float[] pal = new float[16];
            Mat4.multiply(pal, t2, invJb);
            System.arraycopy(pal, 0, out, j * 16, 16);
        }
        System.arraycopy(out, 0, palette, 0, out.length);
        paletteCount = jointCount;
    }

    /** Current joint palette in the flat column-major form the shader uploads. */
    public float[] paletteFlat() {
        return palette;
    }

    public int paletteCount() {
        return paletteCount;
    }

    public void draw(Shader shader, dev.spark3d.gfx.Camera camera, float[] modelMatrix, float time,
                     float sunX, float sunY, float sunZ, float fogR, float fogG, float fogB,
                     float fogStart, float fogEnd, float baseR, float baseG, float baseB, float baseA) {
        for (GltfMesh m : meshes) {
            m.setFrame(
                    camera.viewProj, camera.ex, camera.ey, camera.ez,
                    sunX, sunY, sunZ, fogR, fogG, fogB, fogStart, fogEnd, time,
                    baseR, baseG, baseB, baseA);
            m.draw(shader, modelMatrix, palette, jointCount, false, null, 0);
        }
    }
}
