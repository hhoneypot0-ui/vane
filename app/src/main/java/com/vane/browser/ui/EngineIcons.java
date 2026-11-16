package com.vane.browser.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** 从 assets/icons 里读各引擎的官方图标，读过的缓存起来不重复解码。 */
public final class EngineIcons {

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private EngineIcons() {}

    public static Bitmap get(Context c, String file) {
        if (file == null || file.isEmpty()) return null;
        synchronized (CACHE) {
            if (CACHE.containsKey(file)) return CACHE.get(file);
        }
        Bitmap b = null;
        try (InputStream in = c.getAssets().open("icons/" + file)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {}
        synchronized (CACHE) {
            CACHE.put(file, b);
        }
        return b;
    }
}
