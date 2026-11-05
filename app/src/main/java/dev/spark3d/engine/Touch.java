package dev.spark3d.engine;

/**
 * Two-thumb touch input, with no Android dependency in the maths.
 *
 * <p>{@code GameView} forwards raw pointers; this class decides what they mean.
 * Keeping it separate is what makes the deadzone, the stick clamp and the
 * tap-vs-drag rule testable without a device.
 *
 * <p>Layout, held landscape:
 * <pre>
 *   left half  -> movement stick, floats to wherever the thumb lands
 *   right half -> look stick (camera yaw/pitch)
 *   quick tap  -> {@link #consumeTap()}
 * </pre>
 *
 * <p>Values are written on the UI thread and read on the GL thread. Every field
 * read by the renderer is a volatile float or a single volatile boolean, so a
 * torn read is impossible without any locking in the hot loop.
 */
public final class Touch {

    /** Radius in px where the stick reads full deflection. */
    public static final float STICK_RADIUS_DP = 56f;
    /** Below this fraction of the radius the stick reads as centred. */
    public static final float DEADZONE = 0.14f;
    /** A thumb that moves less than this many dp was a tap, not a drag. */
    private static final float TAP_SLOP_DP = 12f;

    private static float density = 2.5f;
    private static float stickRadius = STICK_RADIUS_DP * density;
    private static float tapSlop = TAP_SLOP_DP * density;

    public static void setDensity(float d) {
        density = d <= 0f ? 1f : d;
        stickRadius = STICK_RADIUS_DP * density;
        tapSlop = TAP_SLOP_DP * density;
    }

    public static float density() {
        return density;
    }

    public static float stickRadius() {
        return stickRadius;
    }

    // --- movement stick (read by the sim)
    public volatile float moveX;
    public volatile float moveY;
    public volatile boolean moving;

    // --- look stick (read by the camera)
    public volatile float lookX;
    public volatile float lookY;
    public volatile boolean looking;

    // --- rendered by the HUD
    public volatile float moveOriginX, moveOriginY, moveKnobX, moveKnobY;
    public volatile float lookOriginX, lookOriginY, lookKnobX, lookKnobY;

    private volatile boolean tapPending;
    private volatile float tapX, tapY;

    private int moveId = -1;
    private int lookId = -1;
    private float moveDownX, moveDownY, lookDownX, lookDownY;
    private float moveTravel, lookTravel;
    private int width = 1;

    public void setViewport(int w) {
        width = Math.max(1, w);
    }

    public int getViewportWidth() {
        return width;
    }

    public boolean consumeTap() {
        if (!tapPending) return false;
        tapPending = false;
        return true;
    }

    public float tapX() {
        return tapX;
    }

    public float tapY() {
        return tapY;
    }

    /** ACTION_DOWN / ACTION_POINTER_DOWN. */
    public void onDown(int id, float x, float y) {
        boolean left = x < width * 0.5f;
        if (left && moveId < 0) {
            moveId = id;
            moveDownX = x;
            moveDownY = y;
            moveTravel = 0f;
            moveOriginX = x;
            moveOriginY = y;
            moveKnobX = x;
            moveKnobY = y;
        } else if (!left && lookId < 0) {
            lookId = id;
            lookDownX = x;
            lookDownY = y;
            lookTravel = 0f;
            lookOriginX = x;
            lookOriginY = y;
            lookKnobX = x;
            lookKnobY = y;
        }
    }

    /** ACTION_MOVE, for one pointer. */
    public void onMove(int id, float x, float y) {
        if (id == moveId) {
            applyStick(x, y, true);
        } else if (id == lookId) {
            applyStick(x, y, false);
        }
    }

    /** ACTION_UP / ACTION_POINTER_UP / ACTION_CANCEL. */
    public void onUp(int id) {
        if (id == moveId) {
            boolean tap = moveTravel < tapSlop;
            moveId = -1;
            moveX = 0f;
            moveY = 0f;
            moving = false;
            if (tap) emitTap(moveDownX, moveDownY);
        } else if (id == lookId) {
            boolean tap = lookTravel < tapSlop;
            lookId = -1;
            lookX = 0f;
            lookY = 0f;
            looking = false;
            if (tap) emitTap(lookDownX, lookDownY);
        }
    }

    /** Called when the window loses focus: drop everything, never stick on. */
    public void cancelAll() {
        moveId = -1;
        lookId = -1;
        moveX = 0f;
        moveY = 0f;
        lookX = 0f;
        lookY = 0f;
        moving = false;
        looking = false;
    }

    private void emitTap(float x, float y) {
        tapX = x;
        tapY = y;
        tapPending = true;
    }

    private void applyStick(float x, float y, boolean isMove) {
        float ox = isMove ? moveOriginX : lookOriginX;
        float oy = isMove ? moveOriginY : lookOriginY;
        float dx = x - ox;
        float dy = y - oy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float travel = d;

        // Clamp to the ring but keep the knob on the rim: that is what makes the
        // stick feel like it has a limit instead of an invisible wall.
        if (d > stickRadius) {
            dx = dx / d * stickRadius;
            dy = dy / d * stickRadius;
            d = stickRadius;
        }

        float nx = d > 1e-4f ? dx / stickRadius : 0f;
        float ny = d > 1e-4f ? dy / stickRadius : 0f;
        if (d / stickRadius < DEADZONE) {
            nx = 0f;
            ny = 0f;
        }

        if (isMove) {
            moveTravel = Math.max(moveTravel, travel);
            moveX = nx;
            moveY = ny;
            moving = nx != 0f || ny != 0f;
            moveKnobX = ox + dx;
            moveKnobY = oy + dy;
        } else {
            lookTravel = Math.max(lookTravel, travel);
            lookX = nx;
            lookY = ny;
            looking = nx != 0f || ny != 0f;
            lookKnobX = ox + dx;
            lookKnobY = oy + dy;
        }
    }
}
