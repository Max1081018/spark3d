package dev.spark3d.gfx;

import android.opengl.GLES30;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A compiled GLSL program plus a uniform-location cache.
 *
 * <p>{@code glGetUniformLocation} is a string lookup into the driver and shows up
 * in traces when called every frame. Resolving once at build time turns the hot
 * path into an int array index.
 */
public final class Shader {

    private final int program;
    private final String name;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public Shader(String name, String vertexSrc, String fragmentSrc) {
        this.name = name;
        int vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc, name + ".vert");
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc, name + ".frag");

        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, vs);
        GLES30.glAttachShader(p, fs);
        GLES30.glLinkProgram(p);

        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(p);
            GLES30.glDeleteProgram(p);
            throw new IllegalStateException("link failed for " + name + ":\n" + log);
        }
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        this.program = p;
        Log.i(Gl.TAG, "shader ready: " + name);
    }

    private static int compile(int type, String src, String label) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new IllegalStateException("compile failed for " + label + ":\n" + log);
        }
        return s;
    }

    public void use() {
        GLES30.glUseProgram(program);
    }

    public int loc(String uniform) {
        Integer cached = uniforms.get(uniform);
        if (cached != null) return cached;
        int l = GLES30.glGetUniformLocation(program, uniform);
        uniforms.put(uniform, l);
        return l;
    }

    public void set1f(String u, float v) {
        GLES30.glUniform1f(loc(u), v);
    }

    public void set2f(String u, float a, float b) {
        GLES30.glUniform2f(loc(u), a, b);
    }

    public void set3f(String u, float a, float b, float c) {
        GLES30.glUniform3f(loc(u), a, b, c);
    }

    public void set4f(String u, float a, float b, float c, float d) {
        GLES30.glUniform4f(loc(u), a, b, c, d);
    }

    public void setMat4(String u, float[] m) {
        GLES30.glUniformMatrix4fv(loc(u), 1, false, m, 0);
    }

    /** Upload a uniform array of matrices, column-major, count entries. */
    public void setMat4Array(String u, int count, float[] mats) {
        GLES30.glUniformMatrix4fv(loc(u), count, false, mats, 64);
    }

    public void set1i(String u, int v) {
        GLES30.glUniform1i(loc(u), v);
    }

    public void dispose() {
        GLES30.glDeleteProgram(program);
    }

    @Override
    public String toString() {
        return "Shader(" + name + ")";
    }
}
