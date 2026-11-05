package dev.spark3d.gfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.spark3d.engine.Json;
import dev.spark3d.engine.Mat4;

/**
 * glTF 2.0 / GLB loader.
 *
 * <p>Deliberately narrow and strict: binary GLB only, triangle primitives only,
 * no Draco, no sparse accessors, no morph targets. Everything else — node
 * hierarchy, TRS and matrix nodes, embedded PNG/JPEG textures, skins with
 * inverse bind matrices, and translation/rotation/scale animation samplers with
 * STEP and LINEAR interpolation — is supported, because that is what the models
 * in {@code assets/models} actually use.
 *
 * <p>Parsing happens once at load. Accessors are decoded into plain
 * {@code float[]} / {@code int[]} so the render path never touches the file
 * again. A malformed file throws with the accessor or node index in the message
 * instead of drawing garbage.
 */
public final class Gltf {

    public static final int PATH_TRANSLATION = 0;
    public static final int PATH_ROTATION = 1;
    public static final int PATH_SCALE = 2;

    public final List<Node> nodes;
    public final List<Mesh> meshes;
    public final List<Material> materials;
    public final List<Image> images;
    public final List<Skin> skins;
    public final List<Animation> animations;

    private final Map<String, Object> root;
    private final byte[] bin;

    /** The clip currently being played, and where in it we are. */
    private Animation active;
    private float activeTime;

    private Gltf(Map<String, Object> root, byte[] bin) {
        this.root = root;
        this.bin = bin;
        this.nodes = new ArrayList<>();
        this.meshes = new ArrayList<>();
        this.materials = new ArrayList<>();
        this.images = new ArrayList<>();
        this.skins = new ArrayList<>();
        this.animations = new ArrayList<>();
        parseAll();
    }

    // ------------------------------------------------------------------- parse

    public static Gltf parse(byte[] glb) {
        if (glb == null || glb.length < 20) throw new IllegalArgumentException("not a GLB file");
        ByteBuffer bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt();
        int version = bb.getInt();
        int length = bb.getInt();
        if (magic != 0x46546C67) throw new IllegalArgumentException("bad GLB magic");
        if (version != 2) throw new IllegalArgumentException("unsupported GLB version " + version);
        if (length > glb.length) throw new IllegalArgumentException("truncated GLB");

        byte[] jsonBytes = null;
        byte[] binBytes = null;
        int pos = 12;
        while (pos + 8 <= length) {
            int chunkLength = bb.getInt(pos);
            int chunkType = bb.getInt(pos + 4);
            pos += 8;
            if (pos + chunkLength > length) throw new IllegalArgumentException("truncated GLB chunk");
            if (chunkType == 0x4E4F534A) {
                jsonBytes = new byte[chunkLength];
                System.arraycopy(glb, pos, jsonBytes, 0, chunkLength);
            } else if (chunkType == 0x004E4942) {
                binBytes = new byte[chunkLength];
                System.arraycopy(glb, pos, binBytes, 0, chunkLength);
            }
            pos += chunkLength;
        }
        if (jsonBytes == null) throw new IllegalArgumentException("GLB has no JSON chunk");

        Object parsed = Json.parse(new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> root = Json.obj(parsed);
        return new Gltf(root, binBytes == null ? new byte[0] : binBytes);
    }

    private void parseAll() {
        List<Object> nodeList = Json.arr(root.get("nodes"));
        List<Object> meshList = Json.arr(root.get("meshes"));
        List<Object> matList = Json.arr(root.get("materials"));
        List<Object> imageList = Json.arr(root.get("images"));
        List<Object> skinList = Json.arr(root.get("skins"));
        List<Object> animList = Json.arr(root.get("animations"));
        List<Object> texList = Json.arr(root.get("textures"));
        List<Object> accList = Json.arr(root.get("accessors"));

        for (Object o : imageList) images.add(parseImage(Json.obj(o)));
        for (Object o : texList) textureSources.add(Json.i(Json.obj(o).get("source"), -1));
        for (Object o : matList) materials.add(parseMaterial(Json.obj(o)));
        for (Object o : meshList) meshes.add(parseMesh(Json.obj(o), accList));
        for (Object o : nodeList) nodes.add(parseNode(Json.obj(o)));
        for (Object o : skinList) skins.add(parseSkin(Json.obj(o), accList));
        for (Object o : animList) animations.add(parseAnimation(Json.obj(o), accList));

        if (nodes.isEmpty()) throw new IllegalArgumentException("glTF has no nodes");
    }

    private final List<Integer> textureSources = new ArrayList<>();

    private Image parseImage(Map<String, Object> m) {
        String mime = Json.str(m.get("mimeType"), "image/png");
        String uri = Json.str(m.get("uri"), null);
        byte[] data;
        if (uri != null && uri.startsWith("data:")) {
            int comma = uri.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("malformed data uri image");
            data = base64(uri.substring(comma + 1));
        } else if (m.containsKey("bufferView")) {
            data = bufferViewBytes(Json.i(m.get("bufferView"), -1));
        } else {
            throw new IllegalArgumentException("external image uri unsupported: " + uri);
        }
        return new Image(mime, data, imageWidth(mime, data), imageHeight(mime, data));
    }

    /** PNG IHDR and JPEG SOF give the pixel dimensions directly from the header. */
    private static int imageWidth(String mime, byte[] d) {
        if (mime != null && mime.startsWith("image/png")) {
            if (d.length < 24) return 0;
            return ((d[16] & 0xff) << 8) | (d[17] & 0xff);
        }
        if (mime != null && mime.startsWith("image/jpeg")) {
            int i = 2;
            while (i + 9 < d.length) {
                int marker = d[i] & 0xff;
                if (marker == 0xFF && (d[i + 1] & 0xff) == 0xD8) { i += 2; continue; }
                int len = ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
                int sof = marker & 0xFF;
                if ((sof & 0xF0) == 0xC0 && sof != 0xC4 && sof != 0xC8) {
                    return ((d[i + 7] & 0xff) << 8) | (d[i + 8] & 0xff);
                }
                i += 2 + len;
            }
        }
        return 0;
    }

    private static int imageHeight(String mime, byte[] d) {
        if (mime != null && mime.startsWith("image/png")) {
            if (d.length < 24) return 0;
            return ((d[20] & 0xff) << 8) | (d[21] & 0xff);
        }
        if (mime != null && mime.startsWith("image/jpeg")) {
            int i = 2;
            while (i + 9 < d.length) {
                int marker = d[i] & 0xff;
                if (marker == 0xFF && (d[i + 1] & 0xff) == 0xD8) { i += 2; continue; }
                int len = ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
                int sof = marker & 0xFF;
                if ((sof & 0xF0) == 0xC0 && sof != 0xC4 && sof != 0xC8) {
                    return ((d[i + 5] & 0xff) << 8) | (d[i + 6] & 0xff);
                }
                i += 2 + len;
            }
        }
        return 0;
    }

    /**
     * Base64 decoder written by hand: {@code java.util.Base64} only exists on
     * Android from API 26 and this app supports API 24. Accepts the standard
     * and URL-safe alphabets and ignores whitespace, because exported data URIs
     * are often line-wrapped.
     */
    private static byte[] base64(String s) {
        byte[] out = new byte[s.length() * 3 / 4 + 3];
        int o = 0;
        int buf = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+' || c == '-') v = 62;
            else if (c == '/' || c == '_') v = 63;
            else if (c == '=') break;
            else continue;
            buf = ((buf << 6) | v) & 0xFFFFFF;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) (buf >> bits);
            }
        }
        return Arrays.copyOf(out, o);
    }

    private Material parseMaterial(Map<String, Object> m) {
        Material mat = new Material();
        mat.name = Json.str(m.get("name"), "");
        boolean ds = Json.b(m.get("doubleSided"), false);
        mat.doubleSided = ds;
        mat.alphaMode = Json.str(m.get("alphaMode"), "OPAQUE");
        mat.alphaCutoff = Json.f(m.get("alphaCutoff"), 0.5f);

        Map<String, Object> pbr = Json.obj(m.get("pbrMetallicRoughness"));
        float[] base = Json.floats(pbr.get("baseColorFactor"), 4);
        if (base.length != 4) {
            base = new float[]{1f, 1f, 1f, 1f};
        }
        if (pbr.get("baseColorFactor") == null) {
            base = new float[]{1f, 1f, 1f, 1f};
        }
        mat.baseColor = base;

        Map<String, Object> tex = Json.obj(pbr.get("baseColorTexture"));
        int texIndex = Json.i(tex.get("index"), -1);
        mat.baseColorImage = texIndex >= 0 && texIndex < textureSources.size()
                ? textureSources.get(texIndex) : -1;

        mat.metallic = Json.f(pbr.get("metallicFactor"), 1f);
        mat.roughness = Json.f(pbr.get("roughnessFactor"), 1f);
        mat.emissive = Json.floats(m.get("emissiveFactor"), 3);
        if (m.get("emissiveFactor") == null) mat.emissive = new float[]{0f, 0f, 0f};
        return mat;
    }

    private Mesh parseMesh(Map<String, Object> m, List<Object> accessors) {
        Mesh mesh = new Mesh();
        mesh.name = Json.str(m.get("name"), "");
        for (Object po : Json.arr(m.get("primitives"))) {
            Map<String, Object> p = Json.obj(po);
            int mode = Json.i(p.get("mode"), 4);
            if (mode != 4) {
                throw new IllegalArgumentException("only triangle primitives are supported (mode " + mode + ")");
            }
            Map<String, Object> attrs = Json.obj(p.get("attributes"));
            Primitive prim = new Primitive();
            prim.positions = accessorFloats(Json.i(attrs.get("POSITION"), -1));
            int vertexCount = prim.positions.length / 3;

            int normalAcc = Json.i(attrs.get("NORMAL"), -1);
            prim.normals = normalAcc >= 0 ? accessorFloats(normalAcc) : fallbackNormals(vertexCount);

            int uvAcc = Json.i(attrs.get("TEXCOORD_0"), -1);
            prim.uvs = uvAcc >= 0 ? accessorFloats(uvAcc) : new float[vertexCount * 2];

            int jointAcc = Json.i(attrs.get("JOINTS_0"), -1);
            int weightAcc = Json.i(attrs.get("WEIGHTS_0"), -1);
            if (jointAcc >= 0 && weightAcc >= 0) {
                prim.joints = accessorFloats(jointAcc);
                prim.weights = accessorFloats(weightAcc);
            } else {
                prim.joints = new float[vertexCount * 4];
                prim.weights = new float[vertexCount * 4];
                for (int v = 0; v < vertexCount; v++) prim.weights[v * 4] = 1f;
            }

            int idxAcc = Json.i(p.get("indices"), -1);
            // Morph targets live in the "targets" array; they are not what we
            // animate, so a primitive that has them is skipped rather than
            // half-supported.
            if (p.get("targets") != null) continue;
            prim.indices = idxAcc >= 0 ? accessorInts(idxAcc) : sequential(vertexCount);
            prim.material = Json.i(p.get("material"), -1);

            if (prim.positions.length == 0) {
                throw new IllegalArgumentException("mesh " + mesh.name + " has an empty POSITION accessor");
            }
            mesh.primitives.add(prim);
        }
        return mesh;
    }

    private Node parseNode(Map<String, Object> m) {
        Node n = new Node();
        n.name = Json.str(m.get("name"), "");
        n.mesh = Json.i(m.get("mesh"), -1);
        n.skin = Json.i(m.get("skin"), -1);
        n.children = ints(Json.arr(m.get("children")));

        float[] matrix = m.get("matrix") != null ? Json.floats(m.get("matrix"), 16) : null;
        n.matrix = matrix;
        if (matrix == null) {
            n.translation = m.get("translation") != null
                    ? Json.floats(m.get("translation"), 3) : new float[]{0f, 0f, 0f};
            n.rotation = m.get("rotation") != null
                    ? Json.floats(m.get("rotation"), 4) : new float[]{0f, 0f, 0f, 1f};
            n.scale = m.get("scale") != null
                    ? Json.floats(m.get("scale"), 3) : new float[]{1f, 1f, 1f};
        } else {
            // Decompose is not needed: animation channels only target TRS nodes in
            // the shipped models, and static nodes keep their matrix as-is.
            n.translation = new float[]{0f, 0f, 0f};
            n.rotation = new float[]{0f, 0f, 0f, 1f};
            n.scale = new float[]{1f, 1f, 1f};
        }
        return n;
    }

    private Skin parseSkin(Map<String, Object> m, List<Object> accessors) {
        Skin s = new Skin();
        s.joints = ints(Json.arr(m.get("joints")));
        int ibm = Json.i(m.get("inverseBindMatrices"), -1);
        if (ibm >= 0) {
            s.inverseBind = accessorFloats(ibm);
        } else {
            s.inverseBind = new float[s.joints.length * 16];
            for (int j = 0; j < s.joints.length; j++) {
                s.inverseBind[j * 16] = 1f;
                s.inverseBind[j * 16 + 5] = 1f;
                s.inverseBind[j * 16 + 10] = 1f;
                s.inverseBind[j * 16 + 15] = 1f;
            }
        }
        if (s.inverseBind.length < s.joints.length * 16) {
            throw new IllegalArgumentException("skin inverse bind matrix count mismatch");
        }
        return s;
    }

    private Animation parseAnimation(Map<String, Object> m, List<Object> accessors) {
        Animation a = new Animation();
        a.name = Json.str(m.get("name"), "clip" + animations.size());
        List<Object> samplers = Json.arr(m.get("samplers"));

        int samplerCount = samplers.size();
        a.samplers = new Animation.Sampler[samplerCount];
        for (int s = 0; s < samplerCount; s++) {
            Map<String, Object> sm = Json.obj(samplers.get(s));
            Animation.Sampler sampler = new Animation.Sampler();
            sampler.times = accessorFloats(Json.i(sm.get("input"), -1));

            int outputAcc = Json.i(sm.get("output"), -1);
            sampler.values = accessorFloats(outputAcc);

            String interp = Json.str(sm.get("interpolation"), "LINEAR");
            sampler.step = "STEP".equals(interp);
            if ("CUBICSPLINE".equals(interp)) {
                throw new IllegalArgumentException(
                        "CUBICSPLINE animation is not supported (clip " + a.name + ")");
            }

            // Components come from the channel path, filled in below.
            sampler.components = sampler.times.length > 0
                    ? sampler.values.length / sampler.times.length : 3;
            a.samplers[s] = sampler;
        }

        List<Animation.Channel> channels = new ArrayList<>();
        for (Object co : Json.arr(m.get("channels"))) {
            Map<String, Object> cm = Json.obj(co);
            Map<String, Object> target = Json.obj(cm.get("target"));
            String path = Json.str(target.get("path"), "");
            int node = Json.i(target.get("node"), -1);
            if (node < 0) continue;
            int pathId;
            switch (path) {
                case "translation": pathId = PATH_TRANSLATION; break;
                case "rotation": pathId = PATH_ROTATION; break;
                case "scale": pathId = PATH_SCALE; break;
                default: continue;   // morph target weights: not supported, skip
            }
            int si = Json.i(cm.get("sampler"), -1);
            if (si < 0 || si >= samplerCount) continue;
            Animation.Sampler s = a.samplers[si];
            if (s.times.length == 0) continue;
            s.components = pathId == PATH_ROTATION ? 4 : 3;
            if (s.values.length < s.times.length * s.components) {
                throw new IllegalArgumentException("animation sampler output is too short");
            }
            channels.add(new Animation.Channel(node, pathId, s));
            a.duration = Math.max(a.duration, s.times[s.times.length - 1]);
        }
        a.channels = channels.toArray(new Animation.Channel[0]);
        return a;
    }

    // --------------------------------------------------------------- accessors

    private int[] sequential(int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = i;
        return out;
    }

    /** Placeholder normals for primitives that shipped without a NORMAL accessor. */
    private static float[] fallbackNormals(int vertexCount) {
        float[] out = new float[vertexCount * 3];
        for (int v = 0; v < vertexCount; v++) {
            out[v * 3] = 0f;
            out[v * 3 + 1] = 1f;
            out[v * 3 + 2] = 0f;
        }
        return out;
    }

    private static int[] ints(List<Object> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = Json.i(list.get(i), -1);
        return out;
    }

    /** Read an accessor as floats. JOINTS_0 integer data is widened, which is fine in a float attribute. */
    public float[] accessorFloats(int index) {
        Map<String, Object> a = accessor(index);
        int count = Json.i(a.get("count"), 0);
        int components = components(Json.str(a.get("type"), "SCALAR"));
        int componentType = Json.i(a.get("componentType"), 5126);
        if (a.containsKey("sparse")) {
            throw new IllegalArgumentException("sparse accessors are not supported");
        }
        int viewIndex = Json.i(a.get("bufferView"), -1);
        float[] out = new float[count * components];
        if (viewIndex < 0) return out;

        Map<String, Object> view = view(viewIndex);
        int base = Json.i(view.get("byteOffset"), 0) + Json.i(a.get("byteOffset"), 0);
        int elementBytes = componentSize(componentType) * components;
        int stride = Json.i(view.get("byteStride"), 0);
        if (stride <= 0) stride = elementBytes;

        boolean normalized = Json.b(a.get("normalized"), false);
        ByteBuffer bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        for (int e = 0; e < count; e++) {
            int row = base + e * stride;
            int outBase = e * components;
            for (int c = 0; c < components; c++) {
                out[outBase + c] = readComponent(bb, row + c * componentSize(componentType),
                        componentType, normalized);
            }
        }
        return out;
    }

    public int[] accessorInts(int index) {
        Map<String, Object> a = accessor(index);
        int count = Json.i(a.get("count"), 0);
        int componentType = Json.i(a.get("componentType"), 5123);
        int components = components(Json.str(a.get("type"), "SCALAR"));
        if (components != 1) throw new IllegalArgumentException("index accessor is not SCALAR");
        int viewIndex = Json.i(a.get("bufferView"), -1);
        int[] out = new int[count];
        if (viewIndex < 0) return out;

        Map<String, Object> view = view(viewIndex);
        int base = Json.i(view.get("byteOffset"), 0) + Json.i(a.get("byteOffset"), 0);
        int size = componentSize(componentType);
        int stride = Json.i(view.get("byteStride"), 0);
        if (stride <= 0) stride = size;

        ByteBuffer bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        for (int e = 0; e < count; e++) {
            int o = base + e * stride;
            switch (componentType) {
                case 5121: out[e] = bb.get(o) & 0xff; break;
                case 5123: out[e] = bb.getShort(o) & 0xffff; break;
                case 5125: out[e] = bb.getInt(o); break;
                default: throw new IllegalArgumentException("unsupported index type " + componentType);
            }
        }
        return out;
    }

    private byte[] bufferViewBytes(int viewIndex) {
        Map<String, Object> view = view(viewIndex);
        int offset = Json.i(view.get("byteOffset"), 0);
        int length = Json.i(view.get("byteLength"), 0);
        if (offset + length > bin.length) throw new IllegalArgumentException("bufferView out of range");
        byte[] out = new byte[length];
        System.arraycopy(bin, offset, out, 0, length);
        return out;
    }

    private float readComponent(ByteBuffer bb, int offset, int type, boolean normalized) {
        switch (type) {
            case 5126: return bb.getFloat(offset);
            case 5121: {
                int v = bb.get(offset) & 0xff;
                return normalized ? v / 255f : v;
            }
            case 5120: {
                int v = bb.get(offset);
                return normalized ? Math.max(v / 127f, -1f) : v;
            }
            case 5123: {
                int v = bb.getShort(offset) & 0xffff;
                return normalized ? v / 65535f : v;
            }
            case 5122: {
                int v = bb.getShort(offset);
                return normalized ? Math.max(v / 32767f, -1f) : v;
            }
            case 5125: {
                long v = bb.getInt(offset) & 0xffffffffL;
                return v;
            }
            default:
                throw new IllegalArgumentException("unsupported component type " + type);
        }
    }

    private static int componentSize(int type) {
        switch (type) {
            case 5120:
            case 5121: return 1;
            case 5122:
            case 5123: return 2;
            case 5125:
            case 5126: return 4;
            default: throw new IllegalArgumentException("unknown component type " + type);
        }
    }

    private static int components(String type) {
        switch (type) {
            case "SCALAR": return 1;
            case "VEC2": return 2;
            case "VEC3": return 3;
            case "VEC4": return 4;
            case "MAT2": return 4;
            case "MAT3": return 9;
            case "MAT4": return 16;
            default: throw new IllegalArgumentException("unknown accessor type " + type);
        }
    }

    private Map<String, Object> accessor(int index) {
        List<Object> list = Json.arr(root.get("accessors"));
        if (index < 0 || index >= list.size()) {
            throw new IllegalArgumentException("accessor " + index + " does not exist");
        }
        return Json.obj(list.get(index));
    }

    private Map<String, Object> view(int index) {
        List<Object> list = Json.arr(root.get("bufferViews"));
        if (index < 0 || index >= list.size()) {
            throw new IllegalArgumentException("bufferView " + index + " does not exist");
        }
        return Json.obj(list.get(index));
    }

    // -------------------------------------------------------------- world math

    /** Parent index for every node, or -1 for a root. */
    public int[] parents() {
        int[] parents = new int[nodes.size()];
        java.util.Arrays.fill(parents, -1);
        for (int i = 0; i < nodes.size(); i++) {
            for (int child : nodes.get(i).children) {
                if (child >= 0 && child < parents.length) parents[child] = i;
            }
        }
        return parents;
    }

    /** World matrix of every node in the default (bind) pose, column-major. */
    public float[][] worldMatrices() {
        int n = nodes.size();
        int[] parents = parents();
        float[][] world = new float[n][];
        for (int i = 0; i < n; i++) world[i] = new float[16];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) resolveWorld(i, parents, world, done, 0, false);
        return world;
    }

    /** World matrices after applying the currently active animation. */
    public float[][] animatedWorldMatrices() {
        int n = nodes.size();
        int[] parents = parents();
        float[][] world = new float[n][];
        for (int i = 0; i < n; i++) world[i] = new float[16];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) resolveWorld(i, parents, world, done, 0, true);
        return world;
    }

    private void resolveWorld(int i, int[] parents, float[][] world, boolean[] done, int depth, boolean animated) {
        if (done[i]) return;
        if (depth > 512) throw new IllegalArgumentException("node hierarchy is cyclic");
        Node node = nodes.get(i);
        float[] local = new float[16];
        if (node.matrix != null) {
            System.arraycopy(node.matrix, 0, local, 0, 16);
        } else {
            float[] t = node.translation != null ? node.translation : new float[]{0f, 0f, 0f};
            float[] q = node.rotation != null ? node.rotation : new float[]{0f, 0f, 0f, 1f};
            float[] s = node.scale != null ? node.scale : new float[]{1f, 1f, 1f};
            Mat4.fromTRS(local, t[0], t[1], t[2], q[0], q[1], q[2], q[3], s[0], s[1], s[2]);
        }
        if (animated) {
            float[] t = node.translation != null ? node.translation : new float[]{0f, 0f, 0f};
            float[] q = node.rotation != null ? node.rotation : new float[]{0f, 0f, 0f, 1f};
            float[] s = node.scale != null ? node.scale : new float[]{1f, 1f, 1f};
            for (Animation a : animations) {
                if (a != active) continue;
                for (Animation.Channel ch : a.channels) {
                    if (ch.node != i) continue;
                    Animation.Sampler sampler = ch.sampler;
                    float[] v = new float[4];
                    sampler.sample(activeTime, v);
                    if (ch.path == PATH_TRANSLATION) {
                        t = new float[]{v[0], v[1], v[2]};
                    } else if (ch.path == PATH_ROTATION) {
                        q = new float[]{v[0], v[1], v[2], v[3]};
                    } else if (ch.path == PATH_SCALE) {
                        s = new float[]{v[0], v[1], v[2]};
                    }
                }
            }
            Mat4.fromTRS(local, t[0], t[1], t[2], q[0], q[1], q[2], q[3], s[0], s[1], s[2]);
        }
        int p = parents[i];
        if (p >= 0 && p < nodes.size()) {
            resolveWorld(p, parents, world, done, depth + 1, animated);
            Mat4.multiply(world[i], world[p], local);
        } else {
            System.arraycopy(local, 0, world[i], 0, 16);
        }
        done[i] = true;
    }

    /**
     * Flatten every mesh node into model space by baking its world transform
     * into the vertex data. Only the rotation and scale are baked; translation
     * is left to the per-instance offset, which is what makes one instance buffer
     * able to place any number of copies. Static props use this so a whole model
     * hierarchy is a single instanced draw with one per-instance TRS.
     */
    public List<Primitive> bakeStatic() {
        float[][] world = worldMatrices();
        List<Primitive> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.mesh < 0) continue;
            Mesh mesh = meshes.get(node.mesh);
            float[] m = world[i];
            // Rotation + scale only, translation dropped: the caller places the
            // instance with the instance offset.
            float[] rot = new float[16];
            rot[0] = m[0]; rot[4] = m[1]; rot[8] = m[2];
            rot[1] = m[4]; rot[5] = m[5]; rot[9] = m[6];
            rot[2] = m[8]; rot[6] = m[9]; rot[10] = m[10];
            rot[3] = 0f; rot[7] = 0f; rot[11] = 0f;
            rot[12] = 0f; rot[13] = 0f; rot[14] = 0f; rot[15] = 1f;
            for (Primitive src : mesh.primitives) {
                Primitive p = src.copy();
                for (int v = 0; v < p.positions.length / 3; v++) {
                    int o = v * 3;
                    float x = p.positions[o], y = p.positions[o + 1], z = p.positions[o + 2];
                    p.positions[o] = rot[0] * x + rot[4] * y + rot[8] * z;
                    p.positions[o + 1] = rot[1] * x + rot[5] * y + rot[9] * z;
                    p.positions[o + 2] = rot[2] * x + rot[6] * y + rot[10] * z;

                    // For a pure rotation the inverse-transpose is the transpose,
                    // which for a column-major rotation equals applying it to the
                    // normal, so no inverse is needed at all. Row r of the rotation
                    // dotted with the normal gives the transformed normal.
                    float nx = p.normals[o], ny = p.normals[o + 1], nz = p.normals[o + 2];
                    float tx = rot[0] * nx + rot[4] * ny + rot[8] * nz;
                    float ty = rot[1] * nx + rot[5] * ny + rot[9] * nz;
                    float tz = rot[2] * nx + rot[6] * ny + rot[10] * nz;
                    float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                    if (len < 1e-6f) len = 1f;
                    p.normals[o] = tx / len;
                    p.normals[o + 1] = ty / len;
                    p.normals[o + 2] = tz / len;
                }
                out.add(p);
            }
        }
        return out;
    }

    /** Axis-aligned bounds of the given primitives: {minX,minY,minZ,maxX,maxY,maxZ}. */
    public static float[] bounds(List<Primitive> primitives) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Primitive p : primitives) {
            for (int v = 0; v < p.positions.length / 3; v++) {
                float x = p.positions[v * 3], y = p.positions[v * 3 + 1], z = p.positions[v * 3 + 2];
                if (x < b[0]) b[0] = x;
                if (y < b[1]) b[1] = y;
                if (z < b[2]) b[2] = z;
                if (x > b[3]) b[3] = x;
                if (y > b[4]) b[4] = y;
                if (z > b[5]) b[5] = z;
            }
        }
        if (b[0] > b[3]) return new float[6];
        return b;
    }

    public Animation animation(String name) {
        for (Animation a : animations) {
            if (a.name.equals(name)) return a;
        }
        return null;
    }

    /** Start playing {@code anim}, optionally at {@code time} into it. */
    public void play(Animation anim, float time) {
        active = anim;
        activeTime = time;
    }

    public Animation activeAnimation() {
        return active;
    }

    public float activeTime() {
        return activeTime;
    }

    /** Advance the active clip. {@code dt} is seconds, {@code speed} is a multiplier. */
    public void advance(float dt, float speed) {
        if (active == null) return;
        float dur = active.duration > 0f ? active.duration : 1f;
        activeTime += dt * speed;
        if (active.loop) {
            activeTime = activeTime - (float) (Math.floor(activeTime / dur) * dur);
        }
        if (activeTime > dur) activeTime = dur;
    }

    // ------------------------------------------------------------------ skins

    /**
     * One (mesh node, mesh, primitive) triple per mesh that uses a skin. The
     * primitive indices here are raw glTF indices; {@link #skinnedPrimitive}
     * bakes joint indices into palette ordinals.
     */
    public static final class MeshSkin {
        public final int nodeIndex;
        public final int meshIndex;
        public final int primitiveIndex;

        MeshSkin(int nodeIndex, int meshIndex, int primitiveIndex) {
            this.nodeIndex = nodeIndex;
            this.meshIndex = meshIndex;
            this.primitiveIndex = primitiveIndex;
        }
    }

    /** Every mesh node of the given skin, in node order. */
    public List<MeshSkin> skinnedBindingsFor(int skinIndex) {
        List<MeshSkin> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.skin != skinIndex || n.mesh < 0) continue;
            Mesh mesh = meshes.get(n.mesh);
            for (int p = 0; p < mesh.primitives.size(); p++) {
                out.add(new MeshSkin(i, n.mesh, p));
            }
        }
        return out;
    }

    /**
     * Returns a copy of the primitive with joint indices rewritten from glTF
     * node indices into palette ordinals (position inside the skin's joint
     * array). Weights follow their joint.
     */
    public Primitive skinnedPrimitive(MeshSkin msk, Skin skin) {
        Mesh mesh = meshes.get(msk.meshIndex);
        if (msk.meshIndex >= meshes.size() || msk.primitiveIndex >= mesh.primitives.size()) {
            throw new IllegalArgumentException("mesh binding out of range");
        }
        Primitive p = mesh.primitives.get(msk.primitiveIndex).copy();
        int[] joints = skin.joints;
        for (int v = 0; v < p.positions.length / 3; v++) {
            for (int k = 0; k < 4; k++) {
                int nodeIdx = (int) p.joints[v * 4 + k];
                int ord = -1;
                for (int j = 0; j < joints.length; j++) {
                    if (joints[j] == nodeIdx) {
                        ord = j;
                        break;
                    }
                }
                p.joints[v * 4 + k] = ord < 0 ? 0f : ord;
                if (ord < 0) p.weights[v * 4 + k] = 0f;
            }
        }
        return p;
    }

    // ------------------------------------------------------------------- types

    public static final class Primitive {
        public float[] positions;
        public float[] normals;
        public float[] uvs;
        public float[] joints;
        public float[] weights;
        public int[] indices;
        public int material = -1;

        Primitive copy() {
            Primitive p = new Primitive();
            p.positions = positions.clone();
            p.normals = normals.clone();
            p.uvs = uvs.clone();
            p.joints = joints.clone();
            p.weights = weights.clone();
            p.indices = indices.clone();
            p.material = material;
            return p;
        }
    }

    public static final class Mesh {
        public String name = "";
        public final List<Primitive> primitives = new ArrayList<>();
    }

    public static final class Material {
        public String name = "";
        public float[] baseColor = {1f, 1f, 1f, 1f};
        public float[] emissive = {0f, 0f, 0f};
        public int baseColorImage = -1;
        public float metallic = 1f;
        public float roughness = 1f;
        public String alphaMode = "OPAQUE";
        public float alphaCutoff = 0.5f;
        public boolean doubleSided;
    }

    public static final class Image {
        public final String mimeType;
        public final byte[] data;
        public final int width;
        public final int height;

        Image(String mimeType, byte[] data, int width, int height) {
            this.mimeType = mimeType;
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    public static final class Node {
        public String name = "";
        public int[] children = new int[0];
        public int mesh = -1;
        public int skin = -1;
        public float[] translation;
        public float[] rotation;
        public float[] scale;
        public float[] matrix;
    }

    public static final class Skin {
        public int[] joints = new int[0];
        public float[] inverseBind;
    }

    public static final class Animation {
        public String name = "";
        public float duration;
        public boolean loop = true;
        public Channel[] channels = new Channel[0];
        public Sampler[] samplers = new Sampler[0];

        public static final class Channel {
            public final int node;
            public final int path;
            public final Sampler sampler;

            Channel(int node, int path, Sampler sampler) {
                this.node = node;
                this.path = path;
                this.sampler = sampler;
            }
        }

        public static final class Sampler {
            public float[] times = new float[0];
            public float[] values = new float[0];
            public int components = 3;
            public boolean step;

            /**
             * Sample at {@code t} into {@code out}. Loops are handled by the
             * caller; this clamps. Rotations use a real slerp — a lerp of two
             * quaternions at 90 degrees apart visibly shrinks the limb.
             */
            public void sample(float t, float[] out) {
                int n = times.length;
                if (n == 0) return;
                if (n == 1 || t <= times[0]) {
                    System.arraycopy(values, 0, out, 0, components);
                    return;
                }
                if (t >= times[n - 1]) {
                    System.arraycopy(values, (n - 1) * components, out, 0, components);
                    return;
                }
                int lo = 0;
                int hi = n - 1;
                while (hi - lo > 1) {
                    int mid = (lo + hi) >>> 1;
                    if (times[mid] <= t) lo = mid; else hi = mid;
                }
                float t0 = times[lo];
                float t1 = times[hi];
                float u = t1 - t0 > 1e-6f ? (t - t0) / (t1 - t0) : 0f;
                if (step) u = 0f;

                int a = lo * components;
                int b = hi * components;
                if (components == 4) {
                    slerp(values, a, values, b, u, out);
                } else {
                    for (int c = 0; c < components; c++) {
                        out[c] = values[a + c] + (values[b + c] - values[a + c]) * u;
                    }
                }
            }

            private static void slerp(float[] a, int ao, float[] b, int bo, float t, float[] out) {
                float ax = a[ao], ay = a[ao + 1], az = a[ao + 2], aw = a[ao + 3];
                float bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
                float dot = ax * bx + ay * by + az * bz + aw * bw;
                if (dot < 0f) {
                    bx = -bx; by = -by; bz = -bz; bw = -bw;
                    dot = -dot;
                }
                float k0;
                float k1;
                if (dot > 0.9995f) {
                    k0 = 1f - t;
                    k1 = t;
                } else {
                    float theta = (float) Math.acos(Math.min(1f, dot));
                    float sin = (float) Math.sin(theta);
                    k0 = (float) Math.sin((1f - t) * theta) / sin;
                    k1 = (float) Math.sin(t * theta) / sin;
                }
                float x = ax * k0 + bx * k1;
                float y = ay * k0 + by * k1;
                float z = az * k0 + bz * k1;
                float w = aw * k0 + bw * k1;
                float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
                if (len < 1e-6f) len = 1f;
                out[0] = x / len;
                out[1] = y / len;
                out[2] = z / len;
                out[3] = w / len;
            }
        }
    }
}
