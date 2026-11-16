package com.vane.browser;

import android.app.Application;
import android.os.Build;

import com.vane.browser.settings.Prefs;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局入口：顺手装一个崩溃记录。
 * 崩了以后堆栈写进 手机存储/Android/data/com.vane.browser/files/crash/，
 * 用户直接把那个文件发过来就能定位，不用连电脑抓 logcat。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                writeCrash(t, e);
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private void writeCrash(Thread t, Throwable e) {
        File dir = getExternalFilesDir("crash");
        if (dir == null) dir = new File(getFilesDir(), "crash");
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(new Date());
        File f = new File(dir, "crash-" + ts + ".txt");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
        pw.println("thread: " + t.getName());
        pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("abi: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?"));
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        String text = sw.toString();
        try (FileWriter w = new FileWriter(f)) {
            w.write(text);
        } catch (Exception ignored) {}
        writeToDownloads(text);
    }

    /** 同时写一份到「下载/Vane」，方便用户直接发出来。 */
    /** 记录一条事件（页面渲染进程崩溃、WebView 异常等），落到下载目录，方便排查。 */
    public static void logEvent(android.content.Context ctx, String tag, String detail) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            pw.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
            pw.println("event: " + tag);
            pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ")");
            pw.println("abi: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?"));
            pw.println("memory: " + (Runtime.getRuntime().totalMemory() / 1048576) + "MB used of "
                    + (Runtime.getRuntime().maxMemory() / 1048576) + "MB max");
            pw.println();
            pw.println(detail);
            pw.flush();
            String name = "vane-event-" + new SimpleDateFormat("MMdd-HHmmss", Locale.CHINA).format(new Date()) + ".txt";
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Vane");
                android.net.Uri uri = ctx.getContentResolver()
                        .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    try (java.io.OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                        if (out != null) out.write(sw.toString().getBytes("UTF-8"));
                    }
                }
            } else {
                File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), "Vane");
                if (!dir.exists()) dir.mkdirs();
                try (FileWriter w = new FileWriter(new File(dir, name))) {
                    w.write(sw.toString());
                }
            }
        } catch (Throwable ignored) {}
    }

    private void writeToDownloads(String text) {
        String name = "vane-crash-" + new SimpleDateFormat("MMdd-HHmmss", Locale.CHINA).format(new Date()) + ".txt";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Vane");
                android.net.Uri uri = getContentResolver()
                        .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out != null) out.write(text.getBytes("UTF-8"));
                    }
                }
            } else {
                File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), "Vane");
                if (!dir.exists()) dir.mkdirs();
                try (FileWriter w = new FileWriter(new File(dir, name))) {
                    w.write(text);
                }
            }
        } catch (Throwable ignored) {}
    }
}
