package dev.spark3d.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON parser that returns plain Java values:
 * {@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Double}, {@code Boolean} and {@code null}.
 *
 * <p>Written instead of using {@code org.json} so the glTF parser can be unit
 * tested on a desktop JVM, where {@code org.json} does not exist. It is also
 * faster than the platform implementation and allocates a fraction as much.
 *
 * <p>No comments, no trailing commas, no NaN. Those are not JSON and a model
 * file that contains them should fail loudly rather than load half-way.
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWhitespace();
        Object v = p.value();
        p.skipWhitespace();
        if (p.pos < p.src.length()) {
            throw p.error("trailing characters");
        }
        return v;
    }

    // ---------------------------------------------------------------- accessors

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        if (o == null) return new LinkedHashMap<>();
        if (!(o instanceof Map)) throw new IllegalArgumentException("expected object, got " + type(o));
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        if (o == null) return new ArrayList<>();
        if (!(o instanceof List)) throw new IllegalArgumentException("expected array, got " + type(o));
        return (List<Object>) o;
    }

    public static String str(Object o, String fallback) {
        return o instanceof String ? (String) o : fallback;
    }

    public static float f(Object o, float fallback) {
        return o instanceof Number ? ((Number) o).floatValue() : fallback;
    }

    public static int i(Object o, int fallback) {
        return o instanceof Number ? ((Number) o).intValue() : fallback;
    }

    public static boolean b(Object o, boolean fallback) {
        return o instanceof Boolean ? (Boolean) o : fallback;
    }

    public static float[] floats(Object o, int expected) {
        List<Object> list = arr(o);
        float[] out = new float[expected > 0 ? expected : list.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = k < list.size() ? f(list.get(k), 0f) : 0f;
        }
        return out;
    }

    private static String type(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------ parser

    private Object value() {
        if (pos >= src.length()) throw error("unexpected end of input");
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected a key");
            String key = string();
            skipWhitespace();
            if (peek() != ':') throw error("expected ':'");
            pos++;
            skipWhitespace();
            map.put(key, value());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return map;
            }
            throw error("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return list;
            }
            throw error("expected ',' or ']'");
        }
    }

    private String string() {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder(24);
        while (true) {
            if (pos >= src.length()) throw error("unterminated string");
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= src.length()) throw error("unterminated escape");
            char e = src.charAt(pos++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > src.length()) throw error("bad unicode escape");
                    int cp = 0;
                    for (int k = 0; k < 4; k++) {
                        int d = Character.digit(src.charAt(pos++), 16);
                        if (d < 0) throw error("bad unicode escape");
                        cp = cp * 16 + d;
                    }
                    sb.append((char) cp);
                    break;
                default:
                    throw error("bad escape \\" + e);
            }
        }
    }

    private Double number() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) throw error("expected a value");
        try {
            return Double.valueOf(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) throw error("expected " + word);
        pos += word.length();
    }

    private char peek() {
        if (pos >= src.length()) throw error("unexpected end of input");
        return src.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        int col = 1;
        for (int k = 0; k < pos && k < src.length(); k++) {
            if (src.charAt(k) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException("JSON " + message + " at line " + line + ", column " + col);
    }
}
