package dev.spark3d.engine;

/**
 * Frame clock.
 *
 * <p>Two numbers matter on a phone and they are different things:
 * <ul>
 *   <li><b>delta</b> — how long the last frame took, clamped so a stalled app
 *       (GC, thermals, a phone call) never teleports the world forward.</li>
 *   <li><b>smoothed delta</b> — an exponential average, which is what the HUD
 *       and the adaptive quality scaler should look at. A single 40 ms frame
 *       should not drop quality; a sustained 40 ms should.</li>
 * </ul>
 *
 * <p>The simulation runs on real delta (with a clamp), not on a fixed step.
 * A third-person game with exponential smoothing is stable under variable dt,
 * and not owning an accumulator loop removes a whole class of catch-up
 * stutter that costs more than it buys on mobile.
 */
public final class Clock {

    private static final float MAX_DELTA = 1f / 12f;   // never simulate more than 83 ms
    private static final float SMOOTHING = 0.06f;      // per-frame weight of the new sample

    private long lastNanos;
    private boolean started;

    /** Clamped seconds since the previous frame. */
    public float delta = 1f / 60f;
    /** Exponential moving average of {@link #delta}. */
    public float smoothDelta = 1f / 60f;
    /** Total simulated seconds. */
    public float time;
    /** Frames rendered since start. */
    public long frames;

    public void reset() {
        lastNanos = System.nanoTime();
        started = true;
        delta = 1f / 60f;
        smoothDelta = 1f / 60f;
    }

    /** Call once at the top of every frame. Returns the clamped delta. */
    public float tick() {
        long now = System.nanoTime();
        if (!started) {
            reset();
            return 0f;
        }
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt < 0f) dt = 0f;
        if (dt > MAX_DELTA) dt = MAX_DELTA;
        delta = dt;
        smoothDelta += (dt - smoothDelta) * SMOOTHING;
        time += dt;
        frames++;
        return dt;
    }

    public float fps() {
        return smoothDelta > 1e-5f ? 1f / smoothDelta : 0f;
    }

    public float frameMs() {
        return smoothDelta * 1000f;
    }
}
