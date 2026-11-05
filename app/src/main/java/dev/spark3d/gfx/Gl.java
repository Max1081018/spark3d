package dev.spark3d.gfx;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Small GL helpers. Everything here fails loudly instead of drawing nothing. */
public final class Gl {

    public static final String TAG = "Spark3D";

    private Gl() {
    }

    public static String readRaw(Context context, int resId) {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read shader resource " + resId, e);
        }
    }

    /** Drain the GL error queue. Called after setup, never in the draw loop. */
    public static void check(String where) {
        int err;
        int guard = 0;
        while ((err = GLES30.glGetError()) != GLES30.GL_NO_ERROR && guard++ < 16) {
            Log.e(TAG, "GL error 0x" + Integer.toHexString(err) + " at " + where);
        }
    }

    public static int genBuffer() {
        int[] b = new int[1];
        GLES30.glGenBuffers(1, b, 0);
        return b[0];
    }

    public static int genVertexArray() {
        int[] v = new int[1];
        GLES30.glGenVertexArrays(1, v, 0);
        return v[0];
    }

    public static int genTexture() {
        int[] t = new int[1];
        GLES30.glGenTextures(1, t, 0);
        return t[0];
    }

    /** A 1x1 white texture used when a material has no texture of its own. */
    private static int whiteTex = -1;

    public static int whiteTexture() {
        if (whiteTex >= 0) return whiteTex;
        int t = genTexture();
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        byte[] px = new byte[4];
        px[0] = (byte) 255; px[1] = (byte) 255; px[2] = (byte) 255; px[3] = (byte) 255;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(4);
        bb.order(java.nio.ByteOrder.nativeOrder());
        bb.put(px);
        bb.position(0);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb);
        whiteTex = t;
        return t;
    }

    public static void bindTexture(int unit, int texture) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
    }
}
