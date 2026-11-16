package com.vane.browser.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 极简图标加载器：内存缓存 + 后台线程，失败就留给调用方兜底。 */
public final class ImageLoader {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(96);

    private ImageLoader() {}

    public static String favicon(String host) {
        if (host == null || host.isEmpty()) return "";
        return "https://" + host + "/favicon.ico";
    }

    public static void load(final ImageView iv, final String url, final int fallbackRes) {
        if (iv == null) return;
        if (url == null || url.isEmpty()) {
            iv.setImageResource(fallbackRes);
            return;
        }
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        iv.setImageResource(fallbackRes);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                InputStream in = c.getInputStream();
                final Bitmap bmp = BitmapFactory.decodeStream(in);
                in.close();
                if (bmp == null) return;
                CACHE.put(url, bmp);
                iv.post(() -> iv.setImageBitmap(bmp));
            } catch (Exception ignored) {
            }
        }, "icon-load").start();
    }
}
