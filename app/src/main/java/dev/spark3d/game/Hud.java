package dev.spark3d.game;

import dev.spark3d.engine.Clock;
import dev.spark3d.engine.SafeArea;
import dev.spark3d.engine.Touch;
import dev.spark3d.gfx.Camera;
import dev.spark3d.gfx.Font;
import dev.spark3d.gfx.UiBatch;

/**
 * The overlay: performance readout, score, and the two touch sticks.
 *
 * <p>Drawn into the same GL surface as the world, in one batch and one draw
 * call. A {@code TextView} on top of a {@code GLSurfaceView} would mean a second
 * composited layer, a UI-thread invalidate every frame and a hard dependency on
 * AndroidX; none of that is worth it for fourteen short strings.
 *
 * <p>Every size is expressed in dp and multiplied by the display density once,
 * so the HUD is the same physical size on a 5" phone and a 13" tablet.
 */
public final class Hud {

    private float density = 2.5f;
    private int width = 1;
    private int height = 1;

    /** Smoothed quality readout so the instance counter does not flicker. */
    private float shownInstances;

    public void setViewport(int w, int h, float density) {
        this.width = Math.max(1, w);
        this.height = Math.max(1, h);
        this.density = density <= 0f ? 1f : density;
    }

    private float dp(float v) {
        return v * density;
    }

    public void draw(
            UiBatch ui,
            Font font,
            Clock clock,
            World world,
            Camera camera,
            Touch touch,
            int drawnCrystals,
            int drawnModels,
            int animatedCharacters,
            int drawCalls,
            int triangles) {

        ui.begin();

        // Keep everything clear of a camera cutout or a gesture bar.
        float padL = dp(14f) + SafeArea.left();
        float padT = dp(14f) + SafeArea.top();
        float padR = dp(14f) + SafeArea.right();
        float padB = dp(14f) + SafeArea.bottom();
        float small = dp(11f);
        float big = dp(22f);
        float line = dp(15f);

        // --- performance block, top-left, on a translucent plate
        float panelW = dp(186f);
        float panelH = dp(76f);
        ui.solid(padL, padT, panelW, panelH, 0.02f, 0.05f, 0.09f, 0.55f, font);
        ui.outline(padL, padT, panelW, panelH, dp(1f),
                Tuning.ACCENT_R, Tuning.ACCENT_G, Tuning.ACCENT_B, 0.28f, font);

        float x = padL + dp(10f);
        float y = padT + dp(5f);

        float fps = clock.fps();
        float r = fps >= 55f ? 0.42f : fps >= 40f ? 1.00f : 1.00f;
        float g = fps >= 55f ? 1.00f : fps >= 40f ? 0.78f : 0.38f;
        float b = fps >= 55f ? 0.72f : fps >= 40f ? 0.30f : 0.30f;
        ui.text(font, whole(fps) + " FPS", x, y, big, r, g, b, 1f);

        shownInstances += (drawnCrystals - shownInstances) * 0.15f;
        ui.textRight(font, nf(shownInstances) + " models", padL + panelW - dp(10f), y + dp(4f),
                small, Tuning.INK_R, Tuning.INK_G, Tuning.INK_B, 0.92f);

        y += dp(28f);
        ui.text(font, one(clock.frameMs()) + " ms   " + drawCalls + " calls   "
                        + k(triangles) + " tris",
                x, y, small, Tuning.DIM_R, Tuning.DIM_G, Tuning.DIM_B, 1f);

        y += line;
        ui.text(font, drawnModels + " static models   " + animatedCharacters + " animated",
                x, y, small, Tuning.INK_R, Tuning.INK_G, Tuning.INK_B, 0.92f);
        y += line;
        ui.text(font, "world " + k(triangles) + "   quality " + pct(clock.smoothDelta),
                x, y, small, Tuning.DIM_R, Tuning.DIM_G, Tuning.DIM_B, 0.75f);

        // --- score, top-right
        String score = String.valueOf(world.score);
        ui.textRight(font, score, width - padR, padT + dp(2f), dp(30f),
                Tuning.INK_R, Tuning.INK_G, Tuning.INK_B, 1f);
        ui.textRight(font, "SCORE", width - padR, padT + dp(34f), small,
                Tuning.DIM_R, Tuning.DIM_G, Tuning.DIM_B, 0.9f);

        // Pickup flash: the score brightens for a moment, which is feedback
        // without a particle system.
        if (world.pickupFlash > 0.01f) {
            ui.textRight(font, "+10", width - padR - dp(2f), padT + dp(46f), dp(15f),
                    Tuning.SHARD_R, Tuning.SHARD_G, Tuning.SHARD_B, world.pickupFlash);
        }

        if (world.nearestDistance < 999f) {
            ui.textRight(font, one(world.nearestDistance) + " m", width - padR,
                    padT + dp(66f), small, Tuning.SHARD_R, Tuning.SHARD_G, Tuning.SHARD_B, 0.65f);
        }

        // --- the sticks
        drawStick(ui, font, touch.moveOriginX, touch.moveOriginY,
                touch.moveKnobX, touch.moveKnobY, touch.moving,
                Tuning.ACCENT_R, Tuning.ACCENT_G, Tuning.ACCENT_B);

        drawStick(ui, font, touch.lookOriginX, touch.lookOriginY,
                touch.lookKnobX, touch.lookKnobY, touch.looking,
                Tuning.SHARD_R, Tuning.SHARD_G, Tuning.SHARD_B);

        // --- hint line, bottom-centre, only when the player has not moved yet
        if (world.time < 6f && world.collected == 0) {
            float al = Math.min(1f, (6f - world.time) * 0.8f);
            ui.textCentered(font, "left thumb to move   -   right thumb to look",
                    width * 0.5f, height - dp(30f) - padB, small,
                    Tuning.INK_R, Tuning.INK_G, Tuning.INK_B, al * 0.8f);
        }

        ui.flush(font, width, height);
    }

    private void drawStick(
            UiBatch ui, Font font,
            float ox, float oy, float kx, float ky, boolean active,
            float r, float g, float b) {

        if (ox == 0f && oy == 0f) return;

        float radius = Touch.STICK_RADIUS_DP * density;
        float a = active ? 0.34f : 0.16f;

        // Ring: an outlined box is cheaper than a circle and reads the same at
        // this size. The knob is a solid square, offset by the stick vector.
        ui.outline(ox - radius, oy - radius, radius * 2f, radius * 2f, dp(1.5f),
                r, g, b, a, font);

        float knob = dp(15f);
        ui.solid(kx - knob * 0.5f, ky - knob * 0.5f, knob, knob,
                r, g, b, active ? 0.85f : 0.45f, font);

        // Small centre cross: makes the neutral position readable.
        ui.solid(ox - dp(1f), oy - dp(5f), dp(2f), dp(10f), r, g, b, a * 0.7f, font);
        ui.solid(ox - dp(5f), oy - dp(1f), dp(10f), dp(2f), r, g, b, a * 0.7f, font);
    }

    // --- formatting helpers (no String.format in the frame loop: it allocates)

    private static String whole(float v) {
        return String.valueOf((int) (v + 0.5f));
    }

    private static String one(float v) {
        int scaled = (int) (v * 10f + 0.5f);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }

    private static String nf(float v) {
        int n = (int) (v + 0.5f);
        if (n >= 1000) return (n / 1000) + "." + ((n % 1000) / 100) + "k";
        return String.valueOf(n);
    }

    private static String k(int n) {
        if (n >= 1000000) return (n / 1000000) + "." + ((n % 1000000) / 100000) + "M";
        if (n >= 1000) return (n / 1000) + "k";
        return String.valueOf(n);
    }

    /** 0..100 quality, derived from how far the frame is from the 60 fps budget. */
    private static String pct(float smoothDelta) {
        float q = 1f - (smoothDelta - 1f / 60f) / (1f / 20f);
        int v = (int) (Math.max(0f, Math.min(1f, q)) * 100f + 0.5f);
        return v + "%";
    }
}
