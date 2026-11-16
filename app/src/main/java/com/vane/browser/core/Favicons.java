package com.vane.browser.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;

/** 站点图标缓存：浏览时抓到的 favicon 存本地，下次直接内联，不用再联网。 */
public final class Favicons {

    private Favicons() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "favicons");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String key(String host) {
        if (host == null) return "";
        return host.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** 浏览时收到图标就存一份。 */
    public static void save(Context c, String host, Bitmap bmp) {
        if (host == null || host.isEmpty() || bmp == null) return;
        try {
            File f = new File(dir(c), key(host) + ".png");
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
        } catch (Exception ignored) {}
    }

    /** 取本地缓存（没有就返回 null）。 */
    public static byte[] get(Context c, String host) {
        if (host == null || host.isEmpty()) return null;
        File f = new File(dir(c), key(host) + ".png");
        if (!f.exists() || f.length() == 0) return null;
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            in.close();
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    public static String toDataUri(byte[] data) {
        if (data == null || data.length == 0) return null;
        return "data:image/png;base64,"
                + android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }
}
