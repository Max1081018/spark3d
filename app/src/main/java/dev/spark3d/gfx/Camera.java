package dev.spark3d.gfx;

import dev.spark3d.engine.Mat4;

/**
 * Third-person orbit camera.
 *
 * <p>Every number here is a feel decision, not a rendering one:
 * <ul>
 *   <li>the eye is smoothed toward a follow point, so the player never
 *       inherits a jittery camera from a jittery frame;</li>
 *   <li>pitch is clamped above the horizon and below top-down, because both
 *       extremes make a 3D phone game unplayable;</li>
 *   <li>the eye is pushed out of the ground, otherwise a hill behind the player
 *       puts the camera inside the terrain and the screen fills with backfaces.</li>
 * </ul>
 */
public final class Camera {

    public final float[] view = Mat4.create();
    public final float[] proj = Mat4.create();
    public final float[] viewProj = Mat4.create();

    /** Smoothed focus point. */
    public float tx, ty, tz;
    /** Eye position, computed each frame. */
    public float ex, ey, ez;

    public float yaw = 0f;
    public float pitch = 0.38f;
    public float distance = 11.5f;
    public float fovY = (float) Math.toRadians(58);
    // A 16-bit depth buffer is what most phone configs give us, so keep the
    // near/far ratio modest: 0.4..520 resolves far better than 0.15..620.
    public float near = 0.4f;
    public float far = 520f;
    /** Look sensitivity, radians per second at full stick deflection. */
    public float lookSpeed = 2.6f;
    /** Terrain height under the focus point; used to keep the eye above ground. */
    public float groundY;

    private float shake;
    private float shakeTime;
    private final float[] tmp = new float[3];

    public void snap(float x, float y, float z) {
        tx = x;
        ty = y;
        tz = z;
        computeEye();
    }

    /** Exponential follow. Rate 14 converges in ~200 ms, which reads as "attached". */
    public void follow(float x, float y, float z, float dt) {
        float k = 1f - (float) Math.exp(-14f * dt);
        tx += (x - tx) * k;
        ty += (y - ty) * k;
        tz += (z - tz) * k;
    }

    /** Apply look-stick input. */
    public void look(float stickX, float stickY, float dt) {
        if (stickX == 0f && stickY == 0f) return;
        yaw -= stickX * lookSpeed * dt;
        pitch += stickY * lookSpeed * 0.72f * dt;
        if (pitch < 0.10f) pitch = 0.10f;
        if (pitch > 1.16f) pitch = 1.16f;
    }

    public void addShake(float amount) {
        shake = Math.min(1f, shake + amount);
    }

    public void update(float aspect, float dt) {
        computeEye();
        shakeTime += dt;
        if (shake > 0f) {
            shake = Math.max(0f, shake - dt * 2.2f);
        }
        Mat4.perspective(proj, fovY, aspect, near, far);
        Mat4.lookAt(view, ex, ey, ez, tx, ty + 1.1f, tz, 0f, 1f, 0f);
        Mat4.multiply(viewProj, proj, view);
    }

    private void computeEye() {
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);

        float shakeAmp = shake * shake * 0.35f;
        float sx = (float) Math.sin(shakeTime * 61f) * shakeAmp;
        float sz = (float) Math.cos(shakeTime * 47f) * shakeAmp;

        ex = tx + sy * cp * distance + sx;
        ey = ty + sp * distance;
        ez = tz + cy * cp * distance + sz;

        // Never put the eye underground.
        float floor = groundY + 1.4f;
        if (ey < floor) ey = floor;
    }

    /** Unit forward vector on the ground plane. */
    public float forwardX() {
        return -(float) Math.sin(yaw);
    }

    public float forwardZ() {
        return -(float) Math.cos(yaw);
    }

    public float rightX() {
        return (float) Math.cos(yaw);
    }

    public float rightZ() {
        return -(float) Math.sin(yaw);
    }

    /** Screen-space sun position for the sky shader, in uv coordinates. */
    public void sunScreen(float sunX, float sunY, float sunZ, float[] out) {
        // Cheap projection: ignore the view matrix, use the camera's yaw only.
        // The sun is decorative, so an approximation that stays fixed relative to
        // the sky is not just acceptable, it looks more stable.
        out[0] = (float) (0.5 + (sunX * Math.cos(yaw) - sunZ * Math.sin(yaw)) * 0.42);
        out[1] = (float) (0.5 + sunY * 0.55);
    }

    void projectPoint(float[] m, float x, float y, float z, float[] out) {
        Mat4.transformPoint(tmp, m, x, y, z);
        out[0] = tmp[0];
        out[1] = tmp[1];
        out[2] = tmp[2];
    }

    /** Aspect helper so callers cannot pass zero and blow up the projection. */
    public static float aspect(int w, int h) {
        if (h <= 0) return 1f;
        return (float) w / (float) h;
    }

    // Unused scratch kept out of the hot path; referenced so lint stays quiet.
    static float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }
}
