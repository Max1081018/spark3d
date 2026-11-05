package dev.spark3d.engine;

/**
 * 4x4 matrix, column-major, exactly the layout OpenGL expects.
 *
 * <p>Deliberately not a general matrix library. A renderer needs six operations
 * and every extra one is code that can be wrong. Everything is written into a
 * caller-supplied array so the render loop never allocates.
 */
public final class Mat4 {

    private Mat4() {
    }

    public static void identity(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
    }

    public static float[] create() {
        float[] m = new float[16];
        identity(m);
        return m;
    }

    /**
     * Perspective projection. Right-handed, looking down -Z, which is what the
     * rest of the engine assumes.
     */
    public static void perspective(float[] m, float fovYRad, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(fovYRad * 0.5));
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
    }

    /** View matrix from an eye point and a look-at target. */
    public static void lookAt(
            float[] m,
            float ex, float ey, float ez,
            float cx, float cy, float cz,
            float ux, float uy, float uz) {

        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-6f) {
            identity(m);
            return;
        }
        fx /= fl; fy /= fl; fz /= fl;

        // s = f x up
        float sx = fy * uz - fz * uy;
        float sy = fz * ux - fx * uz;
        float sz = fx * uy - fy * ux;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6f) {
            // Camera looking straight up or down: pick any perpendicular axis.
            sx = 1f; sy = 0f; sz = 0f;
        } else {
            sx /= sl; sy /= sl; sz /= sl;
        }

        // u = s x f
        float ux2 = sy * fz - sz * fy;
        float uy2 = sz * fx - sx * fz;
        float uz2 = sx * fy - sy * fx;

        m[0] = sx;  m[4] = sy;  m[8]  = sz;  m[12] = -(sx * ex + sy * ey + sz * ez);
        m[1] = ux2; m[5] = uy2; m[9]  = uz2; m[13] = -(ux2 * ex + uy2 * ey + uz2 * ez);
        m[2] = -fx; m[6] = -fy; m[10] = -fz; m[14] = (fx * ex + fy * ey + fz * ez);
        m[3] = 0f;  m[7] = 0f;  m[11] = 0f;  m[15] = 1f;
    }

    /** out = a * b (apply b first, then a). */
    public static void multiply(float[] out, float[] a, float[] b) {
        for (int c = 0; c < 4; c++) {
            int cb = c * 4;
            float b0 = b[cb], b1 = b[cb + 1], b2 = b[cb + 2], b3 = b[cb + 3];
            out[cb]     = a[0] * b0 + a[4] * b1 + a[8]  * b2 + a[12] * b3;
            out[cb + 1] = a[1] * b0 + a[5] * b1 + a[9]  * b2 + a[13] * b3;
            out[cb + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3;
            out[cb + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
        }
    }

    /** Transform a point (w = 1) and write xyz into out. */
    public static void transformPoint(float[] out, float[] m, float x, float y, float z) {
        out[0] = m[0] * x + m[4] * y + m[8]  * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9]  * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    /** Quaternion (x, y, z, w) for a rotation of {@code angle} radians about an axis. */
    public static void quatAxisAngle(float[] out, float ax, float ay, float az, float angle) {
        float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-6f) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 1f;
            return;
        }
        float h = angle * 0.5f;
        float s = (float) Math.sin(h) / l;
        out[0] = ax * s;
        out[1] = ay * s;
        out[2] = az * s;
        out[3] = (float) Math.cos(h);
    }

    /**
     * Pure rotation matrix from a quaternion (x, y, z, w). Scale is 1 and the
     * translation is zero, so the upper 3x3 is exactly the rotation.
     */
    public static void fromQuat(float[] m, float qx, float qy, float qz, float qw) {
        float x2 = qx + qx, y2 = qy + qy, z2 = qz + qz;
        float xx = qx * x2, xy = qx * y2, xz = qx * z2;
        float yy = qy * y2, yz = qy * z2, zz = qz * z2;
        float wx = qw * x2, wy = qw * y2, wz = qw * z2;

        m[0] = (1f - (yy + zz));
        m[1] = (xy + wz);
        m[2] = (xz - wy);
        m[3] = 0f;
        m[4] = (xy - wz);
        m[5] = (1f - (xx + zz));
        m[6] = (yz + wx);
        m[7] = 0f;
        m[8] = (xz + wy);
        m[9] = (yz - wx);
        m[10] = (1f - (xx + yy));
        m[11] = 0f;
        m[12] = 0f;
        m[13] = 0f;
        m[14] = 0f;
        m[15] = 1f;
    }

    /** A yaw-then-pitch quaternion, the two angles a 3D object mostly needs. */
    public static void quatEuler(float[] out, float yaw, float pitch, float roll) {
        float cy = (float) Math.cos(yaw * 0.5f), sy = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f), sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f), sr = (float) Math.sin(roll * 0.5f);
        // Y * X * Z. Deriving this by hand is easy to get wrong: the first
        // version of this method produced a roll about Z for the yaw argument,
        // which a unit test caught. Verified against the rotation matrix for
        // yaw = 90 degrees.
        out[0] = cy * sp * cr + sy * cp * sr;
        out[1] = sy * cp * cr - cy * sp * sr;
        out[2] = cy * cp * sr - sy * sp * cr;
        out[3] = cy * cp * cr + sy * sp * sr;
    }

    /** Compose a transform from translation, rotation quaternion and scale. */
    public static void fromTRS(
            float[] m,
            float tx, float ty, float tz,
            float qx, float qy, float qz, float qw,
            float sx, float sy, float sz) {

        float x2 = qx + qx, y2 = qy + qy, z2 = qz + qz;
        float xx = qx * x2, xy = qx * y2, xz = qx * z2;
        float yy = qy * y2, yz = qy * z2, zz = qz * z2;
        float wx = qw * x2, wy = qw * y2, wz = qw * z2;

        m[0] = (1f - (yy + zz)) * sx;
        m[1] = (xy + wz) * sx;
        m[2] = (xz - wy) * sx;
        m[3] = 0f;

        m[4] = (xy - wz) * sy;
        m[5] = (1f - (xx + zz)) * sy;
        m[6] = (yz + wx) * sy;
        m[7] = 0f;

        m[8] = (xz + wy) * sz;
        m[9] = (yz - wx) * sz;
        m[10] = (1f - (xx + yy)) * sz;
        m[11] = 0f;

        m[12] = tx;
        m[13] = ty;
        m[14] = tz;
        m[15] = 1f;
    }

    /**
     * Full 4x4 inverse. Returns false and writes the identity for a singular
     * matrix instead of producing NaNs that would poison a vertex buffer.
     */
    public static boolean invert(float[] out, float[] m) {
        float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];

        float b00 = a00 * a11 - a01 * a10;
        float b01 = a00 * a12 - a02 * a10;
        float b02 = a00 * a13 - a03 * a10;
        float b03 = a01 * a12 - a02 * a11;
        float b04 = a01 * a13 - a03 * a11;
        float b05 = a02 * a13 - a03 * a12;
        float b06 = a20 * a31 - a21 * a30;
        float b07 = a20 * a32 - a22 * a30;
        float b08 = a20 * a33 - a23 * a30;
        float b09 = a21 * a32 - a22 * a31;
        float b10 = a21 * a33 - a23 * a31;
        float b11 = a22 * a33 - a23 * a32;

        float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (Math.abs(det) < 1e-12f) {
            identity(out);
            return false;
        }
        det = 1f / det;

        out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * det;
        out[1] = (a02 * b10 - a01 * b11 - a03 * b09) * det;
        out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * det;
        out[3] = (a22 * b04 - a21 * b05 - a23 * b03) * det;
        out[4] = (a12 * b08 - a10 * b11 - a13 * b07) * det;
        out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * det;
        out[6] = (a32 * b02 - a30 * b05 - a33 * b01) * det;
        out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * det;
        out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * det;
        out[9] = (a01 * b08 - a00 * b10 - a03 * b06) * det;
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * det;
        out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * det;
        out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * det;
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * det;
        out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * det;
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * det;
        return true;
    }

    /** Transform a direction with the upper 3x3 block, ignoring translation. */
    public static void transformDir(float[] out, float[] m, float x, float y, float z) {
        out[0] = m[0] * x + m[4] * y + m[8] * z;
        out[1] = m[1] * x + m[5] * y + m[9] * z;
        out[2] = m[2] * x + m[6] * y + m[10] * z;
    }
}
