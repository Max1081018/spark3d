package dev.spark3d.gfx;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Asset loading for glTF models. Kept out of {@link Gltf} itself so the parser
 * stays free of Android dependencies and stays unit-testable on a plain JVM.
 */
public final class GltfLoader {

    private GltfLoader() {
    }

    public static Gltf load(Context context, String asset) {
        InputStream in = null;
        try {
            in = context.getAssets().open(asset);
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return Gltf.parse(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("cannot open glTF asset " + asset, e);
        } finally {
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {
            }
        }
    }
}
