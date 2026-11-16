package com.vane.browser.core;

import android.webkit.JavascriptInterface;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注入脚本回传给原生层的通道。 */
public class JsBridge {

    public interface Host {
        void onMedia(String url, String type, String pageUrl);
        void onLongPress(String url, String title);
        void onImage(String src, String alt);
        void onElement(String selector, String label);
        void onPassword(String user, String pass);
        void onReady();
        void onAction(String actionId);
        void onPickEngine();
        void onSearch(String query, String engineId);
    }

    /** 当前页面嗅探到的媒体资源，按 URL 去重。 */
    public static final Map<String, String[]> MEDIA = new LinkedHashMap<>();

    private final Host host;
    private final android.content.Context ctx;

    public JsBridge(Host host, android.content.Context c) {
        this.host = host;
        this.ctx = c;
    }
    private android.webkit.WebView web;

    public void attachWeb(android.webkit.WebView w) {
        this.web = w;
    }
    private String pageUrl;

    public JsBridge(Host host) {
        this.host = host;
        this.ctx = null;
    }

    public void setPageUrl(String u) {
        this.pageUrl = u;
    }

    @JavascriptInterface
    public void media(String url, String type) {
        if (url == null) return;
        synchronized (MEDIA) {
            MEDIA.put(url, new String[]{type == null ? "media" : type, System.currentTimeMillis() + ""});
        }
        if (host != null) host.onMedia(url, type, pageUrl);
    }

    @JavascriptInterface
    public void element(String selector, String label) {
        if (host != null && selector != null && !selector.isEmpty()) host.onElement(selector, label);
    }

    /** 下载任务列表（给下载页用）。 */
    @JavascriptInterface
    public String dlList() {
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager)
                    ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (dm == null) return "[]";
            org.json.JSONArray arr = new org.json.JSONArray();
            android.database.Cursor c = dm.query(new android.app.DownloadManager.Query());
            if (c != null) {
                int iId = c.getColumnIndex(android.app.DownloadManager.COLUMN_ID);
                int iTitle = c.getColumnIndex(android.app.DownloadManager.COLUMN_TITLE);
                int iStatus = c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int iDone = c.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int iTotal = c.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int iUri = c.getColumnIndex(android.app.DownloadManager.COLUMN_URI);
                while (c.moveToNext() && arr.length() < 80) {
                    int st = c.getInt(iStatus);
                    long done = c.getLong(iDone), total = c.getLong(iTotal);
                    String state = st == android.app.DownloadManager.STATUS_SUCCESSFUL ? "已完成"
                            : st == android.app.DownloadManager.STATUS_FAILED ? "失败"
                            : st == android.app.DownloadManager.STATUS_PAUSED ? "已暂停" : "下载中";
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", c.getLong(iId));
                    o.put("title", c.getString(iTitle) == null ? "未命名" : c.getString(iTitle));
                    o.put("state", state);
                    o.put("pct", total > 0 ? (done * 100 / Math.max(1, total)) : -1);
                    o.put("url", c.getString(iUri) == null ? "" : c.getString(iUri));
                    arr.put(o);
                }
                c.close();
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public String gmGet(String ns, String k) {
        return com.vane.browser.settings.Prefs.get("gm:" + ns + ":" + k, "");
    }

    @JavascriptInterface
    public void gmSet(String ns, String k, String v) {
        com.vane.browser.settings.Prefs.put("gm:" + ns + ":" + k, v == null ? "" : v);
    }

    @JavascriptInterface
    public void gmDel(String ns, String k) {
        com.vane.browser.settings.Prefs.put("gm:" + ns + ":" + k, "");
    }

    /** GM_xmlhttpRequest：原生代发，绕开跨域限制。 */
    @JavascriptInterface
    public void gmXhr(final String ns, final String id, final String url, final String method,
                      final String body, final String headers) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            String text = "";
            int status = 0;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestMethod(method == null || method.isEmpty() ? "GET" : method);
                c.setRequestProperty("User-Agent", "Vane");
                if (body != null && !body.isEmpty()) {
                    c.setDoOutput(true);
                    c.getOutputStream().write(body.getBytes("UTF-8"));
                }
                status = c.getResponseCode();
                java.io.InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                    in.close();
                    text = b.toString("UTF-8");
                }
            } catch (Exception ignored) {}
            final int st = status;
            final String tx = text;
            if (web != null) {
                web.post(() -> {
                    try {
                        web.evaluateJavascript("window.__gmXhr && window.__gmXhr("
                                + org.json.JSONObject.quote(id) + "," + st + ","
                                + org.json.JSONObject.quote(tx) + ")", null);
                    } catch (Throwable ignored) {}
                });
            }
        }, "gm-xhr").start();
    }

    @JavascriptInterface
    public void pw(String user, String pass) {
        if (host != null && pass != null && !pass.isEmpty()) host.onPassword(user, pass);
    }

    @JavascriptInterface
    public void image(String src, String alt) {
        if (host != null && src != null && !src.isEmpty()) host.onImage(src, alt);
    }

    @JavascriptInterface
    public void longPress(String url, String text) {
        if (host != null) host.onLongPress(url, text);
    }

    @JavascriptInterface
    public void pickEngine() {
        if (host != null) host.onPickEngine();
    }

    @JavascriptInterface
    public void action(String id) {
        if (host != null && id != null) host.onAction(id);
    }

    @JavascriptInterface
    public void search(String q, String engine) {
        if (host != null) host.onSearch(q, engine);
    }

    @JavascriptInterface
    public void ready() {
        if (host != null) host.onReady();
    }

    public static List<String> mediaUrls() {
        synchronized (MEDIA) {
            return new java.util.ArrayList<>(MEDIA.keySet());
        }
    }

    public static void clearMedia() {
        synchronized (MEDIA) {
            MEDIA.clear();
        }
    }
}
