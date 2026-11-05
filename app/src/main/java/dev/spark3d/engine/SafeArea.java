package dev.spark3d.engine;

/**
 * Display cutout / system bar insets, in pixels.
 *
 * <p>The game draws edge to edge, which is the right choice for a 3D scene, but
 * the HUD must not end up under a camera notch or a gesture bar. The view pushes
 * the current insets here and the HUD adds them to its margins.
 *
 * <p>Static and volatile: written on the UI thread, read on the GL thread. Four
 * independent ints can each be read safely; a torn *set* would only cause one
 * frame with a slightly wrong margin, which nobody can see.
 */
public final class SafeArea {

    private SafeArea() {
    }

    private static volatile int left, top, right, bottom;

    public static void set(int l, int t, int r, int b) {
        left = Math.max(0, l);
        top = Math.max(0, t);
        right = Math.max(0, r);
        bottom = Math.max(0, b);
    }

    public static void clear() {
        left = 0;
        top = 0;
        right = 0;
        bottom = 0;
    }

    public static int left() {
        return left;
    }

    public static int top() {
        return top;
    }

    public static int right() {
        return right;
    }

    public static int bottom() {
        return bottom;
    }
}
