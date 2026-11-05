package dev.spark3d.gfx;

import android.content.Context;
import android.opengl.GLES30;

import dev.spark3d.R;

/**
 * Procedural sky: one full-screen triangle, no geometry buffer.
 *
 * <p>The bottom colour is the same value the fog uses, so terrain at the far
 * plane blends into the horizon instead of stopping at a visible edge. That
 * single shared constant is the cheapest possible "atmosphere" and it is what
 * makes a 250-triangle-per-screen-budget phone game look deep.
 */
public final class Sky {

    private final Shader shader;
    private final float[] sunScreen = new float[2];

    public float topR = 0.035f, topG = 0.055f, topB = 0.10f;
    public float botR = 0.16f, botG = 0.24f, botB = 0.34f;
    public float sunR = 1.00f, sunG = 0.78f, sunB = 0.48f;
    public float sunSize = 0.085f;

    public Sky(Context context) {
        shader = new Shader(
                "sky",
                Gl.readRaw(context, R.raw.sky_vert),
                Gl.readRaw(context, R.raw.sky_frag));
    }

    public void draw(Camera camera, float aspect, float sunX, float sunY, float sunZ) {
        camera.sunScreen(sunX, sunY, sunZ, sunScreen);

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_BLEND);

        shader.use();
        shader.set3f("uSkyTop", topR, topG, topB);
        shader.set3f("uSkyBottom", botR, botG, botB);
        shader.set3f("uSunColor", sunR, sunG, sunB);
        shader.set2f("uSunScreen", sunScreen[0], sunScreen[1]);
        shader.set1f("uSunSize", sunSize);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);

        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
    }

    public void dispose() {
        shader.dispose();
    }

    public static float aspect(int w, int h) {
        return Camera.aspect(w, h);
    }
}
