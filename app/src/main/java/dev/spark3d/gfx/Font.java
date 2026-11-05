package dev.spark3d.gfx;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLUtils;

/**
 * Bitmap glyph atlas, generated at startup with the platform's own font
 * rasteriser.
 *
 * <p>This is the alternative to putting a {@code TextView} on top of the
 * {@code GLSurfaceView}: no second window layer, no view invalidation on the UI
 * thread, and the HUD ends up in the same draw call as the touch sticks. Cost is
 * one 512x240 texture and about 5 ms of startup.
 *
 * <p>ASCII 32..126 occupy 95 cells; cell 95 is a solid white block used for every
 * untextured quad, so outlines, bars and text all share one batch and one draw.
 */
public final class Font {

    public static final int COLS = 16;
    public static final int ROWS = 6;
    public static final int CELL_W = 32;
    public static final int CELL_H = 40;
    /** Size the glyphs were rasterised at; all layout scales from this. */
    public static final int TEXT_SIZE = 26;

    public final int texture;
    private final float[] advance = new float[128];
    private final float[] u0 = new float[128];
    private final float[] u1 = new float[128];
    private final float[] v0 = new float[128];
    private final float[] v1 = new float[128];

    /** UV of the reserved white cell. */
    public final float whiteU;
    public final float whiteV;

    public Font() {
        int w = COLS * CELL_W;
        int h = ROWS * CELL_H;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0x00000000);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(TEXT_SIZE);
        paint.setColor(0xffffffff);
        paint.setTextAlign(Paint.Align.LEFT);

        for (int i = 0; i < 95; i++) {
            char c = (char) (32 + i);
            int col = i % COLS;
            int row = i / COLS;
            float x = col * CELL_W + 3f;
            float y = row * CELL_H + CELL_H * 0.76f;
            canvas.drawText(String.valueOf(c), x, y, paint);

            advance[c] = paint.measureText(String.valueOf(c));
            u0[c] = (float) (col * CELL_W) / w;
            u1[c] = (float) ((col + 1) * CELL_W) / w;
            v0[c] = (float) (row * CELL_H) / h;
            v1[c] = (float) ((row + 1) * CELL_H) / h;
        }

        // White block in the last cell.
        int col = 95 % COLS;
        int row = 95 / COLS;
        paint.setColor(0xffffffff);
        canvas.drawRect(
                col * CELL_W + 1f, row * CELL_H + 1f,
                (col + 1) * CELL_W - 1f, (row + 1) * CELL_H - 1f, paint);
        whiteU = (col * CELL_W + CELL_W * 0.5f) / w;
        whiteV = (row * CELL_H + CELL_H * 0.5f) / h;

        texture = Gl.genTexture();
        Gl.bindTexture(0, texture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        Gl.check("Font upload");
    }

    public float advance(char c) {
        if (c >= 128) return advance['?'];
        return advance[c];
    }

    public float u0(char c) {
        return c >= 128 ? u0['?'] : u0[c];
    }

    public float u1(char c) {
        return c >= 128 ? u1['?'] : u1[c];
    }

    public float v0(char c) {
        return c >= 128 ? v0['?'] : v0[c];
    }

    public float v1(char c) {
        return c >= 128 ? v1['?'] : v1[c];
    }

    public void dispose() {
        GLES30.glDeleteTextures(1, new int[]{texture}, 0);
    }
}
