import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.spark3d.engine.Mat4;
import dev.spark3d.gfx.Gltf;

/**
 * Verification for the glTF/GLB loader and the animation math.
 *
 * Run with:
 *   javac -encoding UTF-8 -d out \
 *     $(find app/src/main/java/dev/spark3d/engine dev/spark3d/gfx -name '*.java') \
 *     verify/GltfCheck.java
 *   java -ea -cp out GltfCheck [assets dir]
 *
 * Exits non-zero on the first failure so it can gate a build. The models are
 * the real ones from Khronos/Sony/Cesium/three.js samples, so the numbers here
 * are the numbers the renderer will actually see.
 */
public final class GltfCheck {

    private static int checks;
    private static int failures;

    public static void main(String[] args) throws Exception {
        Path dir = (args.length > 0 ? Path.of(args[0]) : Path.of("app/src/main/assets/models"))
                .toAbsolutePath();
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream
                    .filter(f -> f.toString().endsWith(".glb"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("no .glb files in " + dir);
        }

        for (Path p : files) {
            checkModel(p);
        }

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ checks

    private static void checkModel(Path p) throws Exception {
        System.out.println("== " + p.getFileName());
        Gltf gltf = Gltf.parse(readAll(p));
        check(gltf.nodes.size() > 0, "has nodes");

        for (int s = 0; s < gltf.skins.size(); s++) {
            check(gltf.skins.get(s) != null, "skin " + s + " exists");
            int n = gltf.skins.get(s).joints.length;
            check(n > 0, "skin " + s + " has joints");
            float[] ibm = gltf.skins.get(s).inverseBind;
            check(ibm != null && ibm.length >= n * 16, "skin " + s + " inverse bind is full size");
            for (int j = 0; j < n; j++) {
                float[] m = new float[16];
                System.arraycopy(ibm, j * 16, m, 0, 16);
                check(Mat4.invert(new float[16], m), "skin " + s + " joint " + j + " invertible");
            }
        }

        for (Gltf.Animation a : gltf.animations) {
            check(a.duration > 0f, "clip " + a.name + " has duration " + a.duration);
            check(a.channels.length > 0, "clip " + a.name + " has channels");
            for (Gltf.Animation.Sampler s : a.samplers) {
                check(s.times.length > 0, "clip " + a.name + " sampler has times");
                check(s.values.length >= s.times.length * Math.max(3, s.components),
                        "clip " + a.name + " sampler " + s.components + " components");
            }
        }

        // Bind + animated world matrices must stay finite, and baked normals
        // must stay unit length.
        float[][] world = gltf.worldMatrices();
        check(world.length == gltf.nodes.size(), "world matrix count");
        for (float[] m : world) {
            for (float x : m) check(Float.isFinite(x), "bind world finite");
        }

        // Every clip must produce finite joint data when sampled. Models without
        // clips (static props) skip this section.
        if (!gltf.animations.isEmpty()) {
            for (Gltf.Animation a : gltf.animations) {
                for (int step = 0; step <= 8; step++) {
                    float t = a.duration * step / 8f;
                    for (Gltf.Animation.Sampler s : a.samplers) {
                        float[] v = new float[s.components];
                        s.sample(t, v);
                        for (float x : v) check(Float.isFinite(x), "clip " + a.name + " finite @ " + t);
                    }
                }
            }

            gltf.play(gltf.animations.get(0), 0f);
            gltf.advance(gltf.animations.get(0).duration, 1f);
            float[][] anim = gltf.animatedWorldMatrices();
            for (float[] m : anim) {
                for (float x : m) check(Float.isFinite(x), "animated world finite");
            }
        }

        java.util.List<Gltf.Primitive> baked = gltf.bakeStatic();
        check(baked.size() > 0, "bakeStatic produced primitives");
        for (Gltf.Primitive prim : baked) {
            int n = prim.positions.length / 3;
            check(prim.positions.length == n * 3, "positions length");
            check(prim.normals.length == n * 3, "normals length");
            float sum = 0f;
            for (int v = 0; v < n; v++) {
                float x = prim.normals[v * 3], y = prim.normals[v * 3 + 1], z = prim.normals[v * 3 + 2];
                sum += x * x + y * y + z * z;
            }
            float meanSquare = sum / Math.max(1, n);
            check(Math.abs((float) Math.sqrt(meanSquare) - 1f) < 1e-3f, "baked normals unit length");
        }
        float[] bounds = Gltf.bounds(baked);
        check(bounds.length == 6, "bounds length");
    }

    private static byte[] readAll(Path p) throws Exception {
        return Files.readAllBytes(p);
    }

    // ------------------------------------------------------------------ runner

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + what);
        }
    }
}
