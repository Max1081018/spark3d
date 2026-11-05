import dev.spark3d.engine.Clock;
import dev.spark3d.engine.Mat4;
import dev.spark3d.engine.Noise;
import dev.spark3d.engine.SpatialHash3;
import dev.spark3d.engine.Touch;

/**
 * Verification for the parts of the engine that do not need Android or a GPU.
 *
 * Run with:
 *   javac -d out $(find app/src/main/java/dev/spark3d/engine -name '*.java') verify/EngineCheck.java
 *   java -ea -cp out EngineCheck
 *
 * Exits non-zero on the first failure so it can gate a build.
 */
public final class EngineCheck {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        mat4();
        quaternions();
        noise();
        touchSticks();
        touchMultiPointer();
        spatialHash();
        clock();

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ tests

    private static void mat4() {
        float[] m = Mat4.create();
        near("identity diagonal", m[0], 1f, 1e-6f);
        near("identity last", m[15], 1f, 1e-6f);
        near("identity off-diagonal", m[4], 0f, 1e-6f);

        // Perspective: transformPoint returns clip-space xyz (no divide). For a
        // point on the near plane centre, clip z equals -0.1 and w equals 0.1, so
        // the NDC z is -1.
        float[] p = Mat4.create();
        Mat4.perspective(p, (float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
        float[] out = new float[3];
        Mat4.transformPoint(out, p, 0f, 0f, -0.1f);
        near("perspective near plane x", out[0], 0f, 1e-5f);
        near("perspective near plane y", out[1], 0f, 1e-5f);
        near("perspective near plane clip z", out[2], -0.1f, 1e-4f);

        // The clip-space w of a view point at z = -10 is 10, and a point at the
        // top edge of a 60 degree FOV must divide to NDC y = 1.
        float[] top = new float[3];
        float tan30 = (float) Math.tan(Math.toRadians(30));
        Mat4.transformPoint(top, p, 0f, tan30 * 10f, -10f);
        near("perspective top edge clip y", top[1], 10f, 1e-3f);
        near("top edge ndc y", top[1] / 10f, 1f, 1e-4f);

        // Look-at: eye 10 units back on +Z looking at the origin. The origin must
        // land 10 units in front of the camera, i.e. at view-space z = -10.
        float[] v = Mat4.create();
        Mat4.lookAt(v, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f);
        Mat4.transformPoint(out, v, 0f, 0f, 0f);
        near("lookAt origin x", out[0], 0f, 1e-5f);
        near("lookAt origin y", out[1], 0f, 1e-5f);
        near("lookAt origin z", out[2], -10f, 1e-5f);

        // A point to the right of the target must stay to the right (+x).
        Mat4.transformPoint(out, v, 5f, 0f, 0f);
        near("lookAt right stays right", out[0], 5f, 1e-5f);

        // orthonormality of the rotation block
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        near("lookAt basis is unit length", len, 1f, 1e-5f);

        // multiply by identity is a no-op
        float[] a = Mat4.create();
        Mat4.perspective(a, 1.2f, 1.5f, 0.1f, 50f);
        float[] b = Mat4.create();
        Mat4.multiply(b, a, Mat4.create());
        boolean same = true;
        for (int i = 0; i < 16; i++) if (Math.abs(a[i] - b[i]) > 1e-6f) same = false;
        check("multiply by identity", same);
    }

    private static void quaternions() {
        float[] q = new float[4];
        Mat4.quatAxisAngle(q, 0f, 1f, 0f, (float) (Math.PI * 0.5));
        float[] r = rotate(q, 1f, 0f, 0f);
        // +90 degrees about +Y sends +X to -Z (right-handed).
        near("quat 90Y x", r[0], 0f, 1e-5f);
        near("quat 90Y y", r[1], 0f, 1e-5f);
        near("quat 90Y z", r[2], -1f, 1e-5f);

        float[] e = new float[4];
        Mat4.quatEuler(e, (float) (Math.PI * 0.5), 0f, 0f);
        float[] r2 = rotate(e, 1f, 2f, 3f);
        // yaw 90 about Y: (x,y,z) -> (z, y, -x)
        near("euler yaw x", r2[0], 3f, 1e-5f);
        near("euler yaw y", r2[1], 2f, 1e-5f);
        near("euler yaw z", r2[2], -1f, 1e-5f);

        // pitch 90 about X: (x,y,z) -> (x, -z, y)
        Mat4.quatEuler(e, 0f, (float) (Math.PI * 0.5), 0f);
        float[] r3 = rotate(e, 1f, 2f, 3f);
        near("euler pitch x", r3[0], 1f, 1e-5f);
        near("euler pitch y", r3[1], -3f, 1e-5f);
        near("euler pitch z", r3[2], 2f, 1e-5f);
    }

    private static void noise() {
        Noise a = new Noise(1234);
        Noise b = new Noise(1234);
        Noise c = new Noise(5678);

        boolean same = true;
        boolean differs = false;
        for (int i = 0; i < 200; i++) {
            float x = i * 0.37f - 20f;
            float y = i * -1.13f + 4f;
            if (a.value(x, y) != b.value(x, y)) same = false;
            if (Math.abs(a.value(x, y) - c.value(x, y)) > 1e-6f) differs = true;
        }
        check("noise is deterministic for a seed", same);
        check("different seeds differ", differs);

        boolean inRange = true;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < 5000; i++) {
            float v = a.fbm(i * 0.01f, i * 0.013f, 4, 2.03f, 0.5f);
            if (v < -1.0001f || v > 1.0001f) inRange = false;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        check("fbm stays in [-1, 1]", inRange);
        check("fbm actually varies", max - min > 0.3f);
    }

    private static void touchSticks() {
        Touch t = new Touch();
        Touch.setDensity(2f);           // 56 dp radius => 112 px
        t.setViewport(2400);

        t.onDown(0, 200f, 800f);
        check("move stick exists after down", t.moveOriginX == 200f && t.moveOriginY == 800f);
        check("stick starts centred", t.moveX == 0f && t.moveY == 0f);

        // Straight up by a full radius: screen-space y is negative, so moveY = -1.
        t.onMove(0, 200f, 800f - 112f);
        near("full deflection x", t.moveX, 0f, 1e-5f);
        near("full deflection y", t.moveY, -1f, 1e-5f);
        check("moving flag", t.moving);

        // Beyond the ring the knob clamps to the rim.
        t.onMove(0, 200f, 800f - 400f);
        near("clamped deflection", t.moveY, -1f, 1e-5f);
        near("knob clamped to rim", t.moveKnobY, 800f - 112f, 1e-5f);

        // Inside the deadzone the stick reads zero.
        t.onMove(0, 200f + 10f, 800f);
        near("deadzone x", t.moveX, 0f, 1e-5f);
        near("deadzone y", t.moveY, 0f, 1e-5f);
        check("not moving inside deadzone", !t.moving);

        // Releasing clears the stick and does not fire a tap after a real drag.
        t.onUp(0);
        check("stick cleared on up", t.moveX == 0f && t.moveY == 0f && !t.moving);
        check("drag is not a tap", !t.consumeTap());
    }

    private static void touchMultiPointer() {
        Touch t = new Touch();
        Touch.setDensity(2f);
        t.setViewport(2400);

        // Left thumb down, then right thumb, both must be tracked independently.
        t.onDown(0, 300f, 900f);
        t.onDown(1, 1800f, 900f);
        check("look stick separate from move", t.lookOriginX == 1800f);

        t.onMove(0, 300f, 900f - 112f);
        t.onMove(1, 1800f + 112f, 900f);
        near("move stick unaffected by look", t.moveY, -1f, 1e-5f);
        near("look stick right", t.lookX, 1f, 1e-5f);

        // Lifting the left thumb must leave the right thumb active.
        t.onUp(0);
        near("look survives other pointer up", t.lookX, 1f, 1e-5f);
        check("look still held", t.looking);

        t.onUp(1);
        check("all released", !t.looking && t.lookX == 0f);

        // A tap is a down/up with almost no travel.
        t.onDown(4, 1200f, 500f);
        t.onMove(4, 1202f, 501f);
        t.onUp(4);
        check("tap detected", t.consumeTap());
        check("tap consumed once", !t.consumeTap());

        // A cancel must drop everything, never leave a stick stuck on.
        t.onDown(0, 300f, 900f);
        t.onDown(1, 1800f, 900f);
        t.onMove(0, 300f, 780f);
        t.cancelAll();
        check("cancel clears move", t.moveX == 0f && t.moveY == 0f && !t.moving);
        check("cancel clears look", t.lookX == 0f && t.lookY == 0f && !t.looking);
    }

    private static void spatialHash() {
        SpatialHash3 h = new SpatialHash3(10f, 200f);
        h.insert(7, 0f, 0f);
        h.insert(8, 3f, 4f);
        h.insert(9, 60f, 60f);

        int[] out = new int[64];
        int n = h.query(1f, 1f, out);
        boolean found7 = false, found8 = false, found9 = false;
        for (int i = 0; i < n; i++) {
            if (out[i] == 7) found7 = true;
            if (out[i] == 8) found8 = true;
            if (out[i] == 9) found9 = true;
        }
        check("query finds nearby id", found7);
        check("query finds second nearby id", found8);
        check("query excludes far id", !found9);

        // Querying outside the world must clamp, not throw.
        n = h.query(-5000f, 5000f, out);
        check("out-of-range query is safe", n >= 0);

        // Rebucketing: after clear, nothing is returned.
        h.clear();
        n = h.query(0f, 0f, out);
        check("clear empties the grid", n == 0);
    }

    private static void clock() {
        Clock c = new Clock();
        c.reset();
        check("clock starts with no frames", c.frames == 0);
        for (int i = 0; i < 5; i++) {
            float dt = c.tick();
            check("delta is never negative", dt >= 0f);
            check("delta is clamped", dt <= 1f / 12f + 1e-6f);
        }
        check("clock counts frames", c.frames == 5);
        check("clock time advances", c.time >= 0f);
        check("fps is finite", c.fps() > 0f && c.fps() < 10000f);
    }

    // ----------------------------------------------------------------- helpers

    private static float[] rotate(float[] q, float x, float y, float z) {
        // v + 2*cross(q.xyz, cross(q.xyz, v) + q.w*v)
        float cx = q[1] * z - q[2] * y;
        float cy = q[2] * x - q[0] * z;
        float cz = q[0] * y - q[1] * x;
        float ix = cx + q[3] * x;
        float iy = cy + q[3] * y;
        float iz = cz + q[3] * z;
        float rx = q[1] * iz - q[2] * iy;
        float ry = q[2] * ix - q[0] * iz;
        float rz = q[0] * iy - q[1] * ix;
        return new float[]{x + 2f * rx, y + 2f * ry, z + 2f * rz};
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL  " + name);
        } else {
            System.out.println("ok    " + name);
        }
    }

    private static void near(String name, float actual, float expected, float tol) {
        checks++;
        if (Math.abs(actual - expected) > tol) {
            failures++;
            System.out.println("FAIL  " + name + "  expected " + expected + " got " + actual);
        } else {
            System.out.println("ok    " + name);
        }
    }
}
