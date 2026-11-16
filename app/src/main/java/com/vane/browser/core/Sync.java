package com.vane.browser.core;

import android.content.Context;
import android.util.Base64;

import com.vane.browser.settings.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** WebDAV 同步：把书签/历史/设置打成一个 JSON 传到自己的服务器（坚果云、群晖都行）。 */
public final class Sync {

    public interface Cb { void done(boolean ok, String msg); }

    private Sync() {}

    private static String auth(Context c) {
        String u = Prefs.get("sync_user", "");
        String p = Prefs.get("sync_pass", "");
        return "Basic " + Base64.encodeToString((u + ":" + p).getBytes(), Base64.NO_WRAP);
    }

    public static String buildPayload(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "vane-sync");
        root.put("time", System.currentTimeMillis());
        root.put("bookmarks", Prefs.getArray("bookmarks"));
        root.put("history", Prefs.getArray("history"));
        root.put("home_cards", Prefs.getArray("home_cards"));
        JSONObject prefs = new JSONObject();
        for (String k : new String[]{"engine", "ua_mode", "font_zoom", "dark_skip", "ad_custom",
                "no_images", "adblock", "sub_auto", "reader_confirm",
                // 下面这些以前没进备份/同步，换机后要重新设一遍
                "export_target", "home_page", "simple_mode", "ad_level", "ad_rules_ver",
                "player_rate", "media_sniff", "open_link_mode", "whitelist", "sub_url",
                "update_url", "feedback_mail", "feedback_url", "search_custom", "ad_vip_hide"}) {
            prefs.put(k, Prefs.get(k, ""));
        }
        root.put("prefs", prefs);
        return root.toString(2);
    }

    public static void upload(final Context c, final Cb cb) {
        new Thread(() -> {
            try {
                String url = Prefs.get("sync_url", "");
                if (url.isEmpty()) { cb.done(false, "还没有填同步地址"); return; }
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Authorization", auth(c));
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = buildPayload(c).getBytes("UTF-8");
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.close();
                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                if (ok) Prefs.putLong("sync_local_time", System.currentTimeMillis());
                Prefs.put("sync_last", (ok ? "上传成功 " : "上传返回 ") + code
                        + " · " + new java.text.SimpleDateFormat("HH:mm:ss",
                        java.util.Locale.CHINA).format(new java.util.Date()));
                if (!ok) logFail(c, url, "HTTP " + code, null);
                cb.done(ok, "上传返回 " + code);
            } catch (Exception e) {
                Prefs.put("sync_last", "上传失败：" + e + " · " + new java.text.SimpleDateFormat(
                        "HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date()));
                logFail(c, Prefs.get("sync_url", ""), "上传异常", e);
                cb.done(false, "上传失败：" + e);
            }
        }, "sync-up").start();
    }

    /** 同步失败时把细节写一份到下载/Vane，方便用户直接看到原因。 */
    private static void logFail(Context c, String url, String what, Exception e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            pw.println("what: " + what);
            pw.println("url: " + url);
            pw.println("user: " + Prefs.get("sync_user", ""));
            pw.println("pass set: " + (!Prefs.get("sync_pass", "").isEmpty()));
            if (e != null) e.printStackTrace(pw);
            pw.flush();
            com.vane.browser.App.logEvent(c, "sync-upload", sw.toString());
        } catch (Throwable ignored) {}
    }

    /** 只读远端时间戳，用于冲突判断。 */
    public static long remoteTime(final Context c) {
        try {
            String url = Prefs.get("sync_url", "");
            if (url.isEmpty()) return -1;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Authorization", auth(c));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return -1;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return new JSONObject(b.toString("UTF-8")).optLong("time", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    public static void download(final Context c, final Cb cb) {
        new Thread(() -> {
            try {
                String url = Prefs.get("sync_url", "");
                if (url.isEmpty()) { cb.done(false, "还没有填同步地址"); return; }
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Authorization", auth(c));
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) { cb.done(false, "下载返回 " + code); return; }
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                JSONObject root = new JSONObject(b.toString("UTF-8"));
                if (!"vane-sync".equals(root.optString("app"))) {
                    cb.done(false, "服务器上的不是Vane同步文件");
                    return;
                }
                JSONArray bm = root.optJSONArray("bookmarks");
                if (bm != null) Prefs.putArray("bookmarks", bm);
                JSONArray hs = root.optJSONArray("history");
                if (hs != null) Prefs.putArray("history", hs);
                // 服务器上没有快捷入口时不要清空本机的：老客户端上传的空数组会误删别人的卡片
                JSONArray hc = root.optJSONArray("home_cards");
                if (hc != null && hc.length() > 0) Prefs.putArray("home_cards", hc);
                JSONObject prefs = root.optJSONObject("prefs");
                if (prefs != null) {
                    java.util.Iterator<String> it = prefs.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        Prefs.put(k, prefs.optString(k, ""));
                    }
                }
                cb.done(true, "已同步下来（书签 " + (bm == null ? 0 : bm.length()) + " 条）");
            } catch (Exception e) {
                cb.done(false, "下载失败：" + e.getMessage());
            }
        }, "sync-down").start();
    }
}
