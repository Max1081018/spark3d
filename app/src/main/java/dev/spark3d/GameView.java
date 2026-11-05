package dev.spark3d;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.MotionEvent;
import android.view.WindowInsets;

import dev.spark3d.engine.SafeArea;
import dev.spark3d.engine.Touch;
import dev.spark3d.game.GameRenderer;

/**
 * The GL surface plus the touch plumbing.
 *
 * <p>Pointer ids are tracked individually so two thumbs work at the same time,
 * which is the whole control scheme. {@code ACTION_UP} alone is not enough: when
 * both thumbs are down, lifting one delivers {@code ACTION_POINTER_UP} and the
 * remaining pointer must keep its stick.
 *
 * <p>The API-30 inset code lives in a nested class on purpose. Referencing
 * {@code WindowInsets.Type} from a method that runs on API 24 makes the ART
 * verifier try to resolve a class that does not exist on those devices. A nested
 * class is only loaded when it is first used, which is inside the version guard.
 */
public final class GameView extends GLSurfaceView {

    private final Touch touch = new Touch();
    private final GameRenderer renderer;

    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        // 8/8/8/8 colour, 16-bit depth, no stencil: no stencil buffer is used and
        // asking for one costs bandwidth on tile-based mobile GPUs.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);

        float density = context.getResources().getDisplayMetrics().density;
        Touch.setDensity(density);

        renderer = new GameRenderer(context, touch);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);

        setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(android.view.View v, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets30.read(insets);
                } else if (Build.VERSION.SDK_INT >= 28) {
                    Insets28.read(insets);
                } else {
                    SafeArea.set(
                            insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            insets.getSystemWindowInsetBottom());
                }
                return insets;
            }
        });
    }

    /** API 30+: system bar and display cutout insets in one call. */
    private static final class Insets30 {
        static void read(WindowInsets insets) {
            android.graphics.Insets cut = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            SafeArea.set(cut.left, cut.top, cut.right, cut.bottom);
        }
    }

    /** API 28-29: system window insets plus the cutout safe area. */
    private static final class Insets28 {
        static void read(WindowInsets insets) {
            int l = insets.getSystemWindowInsetLeft();
            int t = insets.getSystemWindowInsetTop();
            int r = insets.getSystemWindowInsetRight();
            int b = insets.getSystemWindowInsetBottom();
            android.view.DisplayCutout dc = insets.getDisplayCutout();
            if (dc != null) {
                l = Math.max(l, dc.getSafeInsetLeft());
                t = Math.max(t, dc.getSafeInsetTop());
                r = Math.max(r, dc.getSafeInsetRight());
                b = Math.max(b, dc.getSafeInsetBottom());
            }
            SafeArea.set(l, t, r, b);
        }
    }

    public GameRenderer renderer() {
        return renderer;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        touch.setViewport(getWidth());

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                touch.onDown(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int count = e.getPointerCount();
                for (int i = 0; i < count; i++) {
                    touch.onMove(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                touch.onUp(e.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                touch.cancelAll();
                break;
            }
            default:
                break;
        }
        return true;
    }

    /** Activity pause: GLSurfaceView owns the EGL surface, we stop the input. */
    public void releaseInput() {
        touch.cancelAll();
    }

    public void onWindowFocusLost() {
        touch.cancelAll();
    }
}
