package dev.spark3d;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Nothing between the player and the GPU.
 *
 * <p>No WebView, no AndroidX, no Compose: one {@link GameView} filling the
 * window. The activity's only jobs are to go fullscreen, keep the screen awake,
 * and hand lifecycle events to the {@code GLSurfaceView} so the EGL context is
 * released while the app is in the background instead of being killed by the
 * driver.
 *
 * <p>Anything newer than the minimum SDK lives in its own nested class. On API
 * 24 the verifier must not have to resolve {@code WindowInsetsController}.
 */
public final class MainActivity extends Activity {

    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= 28) {
            Cutout.enable(getWindow());
        }

        view = new GameView(this);
        setContentView(view);
        hideSystemUi();
    }

    private void hideSystemUi() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            Immersive30.hide(window);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    /** API 30+: edge-to-edge and transient system bars. */
    private static final class Immersive30 {
        static void hide(Window window) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    /** API 28+: draw into the cutout area; the HUD compensates via insets. */
    private static final class Cutout {
        static void enable(Window window) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        } else if (view != null) {
            view.onWindowFocusLost();
        }
    }

    @Override
    protected void onPause() {
        if (view != null) {
            view.releaseInput();
            view.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.onResume();
        hideSystemUi();
    }
}
