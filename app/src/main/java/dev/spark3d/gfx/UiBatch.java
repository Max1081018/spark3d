package dev.spark3d.gfx;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Immediate-mode 2D batch for the HUD.
 *
 * <p>Everything the overlay draws — text, touch sticks, bars, outlines — becomes
 * quads in one interleaved array and leaves as a single {@code glDrawElements}.
 * Text and solid shapes share the glyph atlas (a reserved white cell), so there
 * is exactly one texture bind and one program switch for the whole HUD.
 *
 * <p>Positions are pixels with a top-left origin; the vertex shader converts to
 * NDC. That keeps layout code readable and resolution-independent.
 */
public final class UiBatch {

    private static final int FLOATS_PER_VERTEX = 8;   // pos2 + uv2 + rgba4

    private final int maxQuads;
    private final float[] data;
    private final FloatBuffer staging;
    private final int vao;
    private final int vbo;
    private final int ibo;
    private final Shader shader;

    private int vertices;
    private int quads;

    public UiBatch(int maxQuads, Shader shader) {
        this.maxQuads = maxQuads;
        this.shader = shader;
        this.data = new float[maxQuads * 4 * FLOATS_PER_VERTEX];
        this.staging = ByteBuffer
                .allocateDirect(maxQuads * 4 * FLOATS_PER_VERTEX * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        vao = Gl.genVertexArray();
        GLES30.glBindVertexArray(vao);

        vbo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                maxQuads * 4 * FLOATS_PER_VERTEX * 4, null, GLES30.GL_STREAM_DRAW);

        int stride = FLOATS_PER_VERTEX * 4;
        attrib(0, 2, stride, 0);
        attrib(1, 2, stride, 8);
        attrib(2, 4, stride, 16);

        // Static index buffer: 0,1,2, 0,2,3 per quad.
        short[] idx = new short[maxQuads * 6];
        for (int q = 0; q < maxQuads; q++) {
            short b = (short) (q * 4);
            int o = q * 6;
            idx[o] = b;
            idx[o + 1] = (short) (b + 1);
            idx[o + 2] = (short) (b + 2);
            idx[o + 3] = b;
            idx[o + 4] = (short) (b + 2);
            idx[o + 5] = (short) (b + 3);
        }
        ShortBuffer ib = ByteBuffer
                .allocateDirect(idx.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        ib.put(idx).position(0);
        ibo = Gl.genBuffer();
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.length * 2, ib, GLES30.GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void attrib(int location, int size, int stride, int offset) {
        GLES30.glEnableVertexAttribArray(location);
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, stride, offset);
    }

    public void begin() {
        vertices = 0;
        quads = 0;
    }

    public boolean empty() {
        return quads == 0;
    }

    /** A solid rectangle. */
    public void solid(float x, float y, float w, float h,
                      float r, float g, float b, float a, Font font) {
        quad(x, y, w, h, font.whiteU, font.whiteV, font.whiteU, font.whiteV, r, g, b, a);
    }

    /** Outlined rectangle. */
    public void outline(float x, float y, float w, float h, float thickness,
                        float r, float g, float b, float a, Font font) {
        solid(x, y, w, thickness, r, g, b, a, font);
        solid(x, y + h - thickness, w, thickness, r, g, b, a, font);
        solid(x, y, thickness, h, r, g, b, a, font);
        solid(x + w - thickness, y, thickness, h, r, g, b, a, font);
    }

    public void quad(float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        if (quads >= maxQuads) return;
        int o = vertices * FLOATS_PER_VERTEX;
        float[] d = data;
        // 0: top-left, 1: bottom-left, 2: bottom-right, 3: top-right
        d[o] = x;         d[o + 1] = y;         d[o + 2] = u0; d[o + 3] = v0;
        d[o + 4] = r;     d[o + 5] = g;         d[o + 6] = b;  d[o + 7] = a;

        o += FLOATS_PER_VERTEX;
        d[o] = x;         d[o + 1] = y + h;     d[o + 2] = u0; d[o + 3] = v1;
        d[o + 4] = r;     d[o + 5] = g;         d[o + 6] = b;  d[o + 7] = a;

        o += FLOATS_PER_VERTEX;
        d[o] = x + w;     d[o + 1] = y + h;     d[o + 2] = u1; d[o + 3] = v1;
        d[o + 4] = r;     d[o + 5] = g;         d[o + 6] = b;  d[o + 7] = a;

        o += FLOATS_PER_VERTEX;
        d[o] = x + w;     d[o + 1] = y;         d[o + 2] = u1; d[o + 3] = v0;
        d[o + 4] = r;     d[o + 5] = g;         d[o + 6] = b;  d[o + 7] = a;

        vertices += 4;
        quads++;
    }

    /** Draw a string with the top-left of the line box at (x, y). */
    public void text(Font font, String s, float x, float y, float sizePx,
                     float r, float g, float b, float a) {
        if (s == null || s.isEmpty()) return;
        float scale = sizePx / Font.TEXT_SIZE;
        float cellW = Font.CELL_W * scale;
        float cellH = Font.CELL_H * scale;
        float pad = 3f * scale;
        float pen = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float adv = font.advance(c) * scale;
            if (c != ' ') {
                quad(pen - pad, y, cellW, cellH,
                        font.u0(c), font.v0(c), font.u1(c), font.v1(c), r, g, b, a);
            }
            pen += adv;
        }
    }

    public float textWidth(Font font, String s, float sizePx) {
        float scale = sizePx / Font.TEXT_SIZE;
        float w = 0f;
        for (int i = 0; i < s.length(); i++) w += font.advance(s.charAt(i)) * scale;
        return w;
    }

    public void textCentered(Font font, String s, float cx, float y, float sizePx,
                             float r, float g, float b, float a) {
        float w = textWidth(font, s, sizePx);
        text(font, s, cx - w * 0.5f, y, sizePx, r, g, b, a);
    }

    public void textRight(Font font, String s, float right, float y, float sizePx,
                          float r, float g, float b, float a) {
        float w = textWidth(font, s, sizePx);
        text(font, s, right - w, y, sizePx, r, g, b, a);
    }

    /** Upload and draw. Restores the depth state the world pass expects. */
    public void flush(Font font, int screenW, int screenH) {
        if (quads == 0) return;
        int floats = vertices * FLOATS_PER_VERTEX;
        staging.clear();
        staging.put(data, 0, floats);
        staging.position(0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, floats * 4, staging);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        shader.use();
        shader.set2f("uResolution", screenW, screenH);
        shader.set1f("uAlpha", 1f);
        shader.set1i("uAtlas", 0);
        Gl.bindTexture(0, font.texture);

        GLES30.glBindVertexArray(vao);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, quads * 6, GLES30.GL_UNSIGNED_SHORT, 0);
        GLES30.glBindVertexArray(0);

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
    }

    public void dispose() {
        GLES30.glDeleteVertexArrays(1, new int[]{vao}, 0);
        GLES30.glDeleteBuffers(1, new int[]{vbo}, 0);
        GLES30.glDeleteBuffers(1, new int[]{ibo}, 0);
    }
}
