package com.vane.browser.core;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.vane.browser.MainActivity;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;

/** 一个标签页 = 一个 WebView。所有站点兼容性相关的开关都集中在这里。 */
public class BrowserTab {

    public final WebView web;
    public String title = "新标签页";
    /** 内置页（书签/历史）的显示名；非空时地址栏显示它而不是 URL。 */
    public String internalLabel;
    public String url = "";
    /** 上一次整页加载耗时（毫秒）。 */
    public volatile long lastLoadMs = -1;
    private long loadStartAt = 0;
    public String faviconGlyph = "◍";
    public int progress = 0;
    public boolean desktop = false;
    private final MainActivity act;
    public final JsBridge bridge;

    @SuppressLint("SetJavaScriptEnabled")
    public BrowserTab(MainActivity a) {
        this.act = a;
        web = new WebView(a);
        web.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 注意：@JavascriptInterface 的方法跑在 WebView 的 JavaBridge 线程上，
        // 那个线程没有 Looper，直接在那里弹窗/建 View 会被系统判为致命错误（表现为闪退）。
        // 所有回调统一 post 回 UI 线程。
        bridge = new JsBridge(new JsBridge.Host() {
            @Override public void onMedia(final String u, final String t, String page) {
                act.runOnUiThread(() -> act.onMediaFound(u, t));
            }
            @Override public void onLongPress(final String u, final String t) {
                act.runOnUiThread(() -> act.onLinkLongPress(u, t));
            }
            @Override public void onElement(final String selector, final String label) {
                act.runOnUiThread(() -> act.onElementLongPress(selector, label));
            }
            @Override public void onPassword(final String user, final String pass) {
                act.runOnUiThread(() -> act.onPasswordCaptured(url, user, pass));
            }
            @Override public void onImage(final String src, final String alt) {
                act.runOnUiThread(() -> act.onImageLongPress(src, alt));
            }
            @Override public void onReady() {}
            @Override public void onAction(final String actionId) {
                act.runOnUiThread(() -> act.dispatch(actionId));
            }
            @Override public void onPickEngine() {
                act.runOnUiThread(() -> act.showEngineSheet());
            }
            @Override public void onSearch(final String q, final String engineId) {
                act.runOnUiThread(() -> {
                    if (engineId != null && !engineId.isEmpty()) {
                        com.vane.browser.settings.Prefs.put("engine", engineId);
                    }
                    act.openUrlOrSearch(q);
                });
            }
        }, a);
        web.setBackgroundColor(com.vane.browser.ui.Theme.bg(a,
                com.vane.browser.settings.UiConfig.load()));
        bridge.attachWeb(web);
        web.addJavascriptInterface(bridge, "VaneBridge");
        configure();
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((u, ua, cd, mime, len) ->
                Downloads.enqueue(act, u, ua, cd, mime, web.getUrl()));
    }

    /** 高级开关：强制缩放 + 第三方 Cookie。 */
    public void applyExtras() {
        try {
            if (com.vane.browser.settings.Prefs.getBool("force_zoom", false)) {
                WebSettings st = web.getSettings();
                st.setUseWideViewPort(true);
                st.setLoadWithOverviewMode(false);
                st.setSupportZoom(true);
            }
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptThirdPartyCookies(web,
                    !com.vane.browser.settings.Prefs.getBool("block_third_cookie", false));
        } catch (Throwable ignored) {}
    }

    private void configure() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        applyExtras();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                web.setRendererPriorityPolicy(android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT, false);
            }
            s.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            s.setDatabaseEnabled(true);
        } catch (Throwable ignored) {}
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportMultipleWindows(true);
        s.setAllowFileAccess(true);            // 主页是 android_asset 的 file:// 页，必须开
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(false);
        s.setUserAgentString(act.userAgent());
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
    }

    /**
     * 网页深色统一走我们自己的 DARK_CSS（背景压深 + 文字提亮）。
     * 这里必须把系统「算法暗化」关掉：两套一起上会二次处理颜色，
     * 蓝色链接被反相成紫色、图片也发紫，就是之前那种「页面泛紫」。
     */
    public void applyDarkening() {
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.getSettings(), false);
            } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                web.getSettings().setForceDark(WebSettings.FORCE_DARK_OFF);
            }
        } catch (Throwable ignored) {}
    }

    private static String fontJsCache;

    /** 自定义字体的注入脚本（base64 只算一次）。 */
    private String customFontJs() {
        try {
            if (!com.vane.browser.settings.Prefs.getBool("font_custom", false)) return null;
            String path = com.vane.browser.settings.Prefs.get("font_file", "");
            if (path.isEmpty()) return null;
            if (fontJsCache != null) return fontJsCache;
            java.io.File f = new java.io.File(path);
            if (!f.exists() || f.length() == 0) return null;
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            in.close();
            String b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            String css = "@font-face{font-family:vaneCustom;src:url(data:font/ttf;base64," + b64
                    + ") format('truetype');font-display:swap}"
                    + "body,body *{font-family:vaneCustom,system-ui,sans-serif !important}";
            fontJsCache = "(function(){var id='__v_font';var o=document.getElementById(id);if(o)o.remove();"
                    + "var st=document.createElement('style');st.id=id;st.textContent="
                    + org.json.JSONObject.quote(css) + ";document.documentElement.appendChild(st);})();";
            return fontJsCache;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 自定义字体：把字体文件转 base64 内联进页面。 */
    private void applyCustomFont(android.webkit.WebView v) {
        try {
            if (!com.vane.browser.settings.Prefs.getBool("font_custom", false)) return;
            String path = com.vane.browser.settings.Prefs.get("font_file", "");
            if (path.isEmpty()) return;
            java.io.File f = new java.io.File(path);
            if (!f.exists() || f.length() == 0) return;
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            in.close();
            String b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            String css = "@font-face{font-family:vaneCustom;src:url(data:font/ttf;base64," + b64
                    + ") format('truetype');font-display:swap}"
                    + "body,body *{font-family:vaneCustom,system-ui,sans-serif !important}";
            String js = "(function(){var id='__g_font';var o=document.getElementById(id);if(o)o.remove();"
                    + "var st=document.createElement('style');st.id=id;st.textContent="
                    + org.json.JSONObject.quote(css) + ";document.documentElement.appendChild(st);})();";
            v.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    /** 按站点记忆：字体大小、电脑模式、不拦广告。 */
    public void applySiteMemory() {
        try {
            String h = url == null ? "" : android.net.Uri.parse(url).getHost();
            if (h == null || h.isEmpty()) return;
            String zoom = com.vane.browser.settings.Prefs.get("site_font:" + h, "");
            if (!zoom.isEmpty()) {
                web.getSettings().setTextZoom(Integer.parseInt(zoom));
            } else {
                web.getSettings().setTextZoom(com.vane.browser.settings.Prefs.getInt("font_zoom", 100));
            }
        } catch (Throwable ignored) {}
    }

    /** 记住本站的字体大小。 */
    public void rememberSiteFont(int zoom) {
        try {
            String h = url == null ? "" : android.net.Uri.parse(url).getHost();
            if (h == null || h.isEmpty()) return;
            com.vane.browser.settings.Prefs.put("site_font:" + h, String.valueOf(zoom));
        } catch (Throwable ignored) {}
    }

    /** 有保存过的账号就自动填上。 */
    private void fillSavedPassword() {
        try {
            java.net.URI u = new java.net.URI(url == null ? "" : url);
            String host = u.getHost();
            if (host == null) return;
            String[] cred = com.vane.browser.core.Passwords.get(act, host);
            if (cred == null) return;
            String js = "window.__gFill && window.__gFill("
                    + org.json.JSONObject.quote(cred[0]) + "," + org.json.JSONObject.quote(cred[1]) + ")";
            web.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    /** 把用户「长按隐藏」的规则注入页面。 */
    public void applyHiddenElements(String host) {
        if (host == null || host.isEmpty()) return;
        String sel = com.vane.browser.settings.Prefs.get("hide_" + host, "");
        if (sel.isEmpty()) return;
        StringBuilder css = new StringBuilder();
        for (String one : sel.split(",")) {
            one = one.trim();
            if (!one.isEmpty()) css.append(one).append("{display:none !important}");
        }
        String js = "(function(){var id='__vane_hide';var old=document.getElementById(id);"
                + "if(old)old.remove();var st=document.createElement('style');st.id=id;"
                + "st.textContent=" + org.json.JSONObject.quote(css.toString()) + ";"
                + "document.documentElement.appendChild(st);})();";
        try {
            web.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    /** 深色主题就往页面里塞深色样式；浅色就删掉。 */
    public void applyPageTheme() {
        // 内置主页 / 本地页面不注入强制深色，否则会把搜索框背景刷没
        String u = web.getUrl() == null ? "" : web.getUrl();
        if (u.startsWith("file://") || u.startsWith("data:") || u.isEmpty()) {
            try { web.evaluateJavascript(VideoSniffer.DARK_OFF, null); } catch (Throwable ignored) {}
            return;
        }
        boolean dark = com.vane.browser.ui.Theme.isDark(act,
                com.vane.browser.settings.UiConfig.load());
        android.net.Uri pu = android.net.Uri.parse(u);
        String ph = pu.getHost() == null ? "" : pu.getHost().toLowerCase();
        if (!ph.isEmpty()) {
            String skip = com.vane.browser.settings.Prefs.get("dark_skip", "").toLowerCase();
            for (String h : skip.split(",")) {
                h = h.trim();
                if (!h.isEmpty() && (ph.equals(h) || ph.endsWith("." + h))) dark = false;
            }
        }
        boolean forceDark = com.vane.browser.settings.Prefs.getBool("dark_web", false);
        try {
            web.evaluateJavascript((dark && forceDark) ? VideoSniffer.DARK_CSS : VideoSniffer.DARK_OFF, null);
        } catch (Throwable ignored) {}
    }

    public void applyAll() {
        WebSettings s = web.getSettings();
        s.setUserAgentString(act.userAgent());
        applyDarkening();
        web.setWebViewClient(web.getWebViewClient()); // 保留
    }

    public void load(String u) {
        url = u;
        String home = com.vane.browser.settings.UiConfig.load().homePage;
        if (u == null || u.isEmpty() || u.equals("about:blank")
                || u.equals(home) || u.startsWith("file:///android_asset/")) {
            loadHome();                       // 主页直接塞进 WebView，不依赖 file 访问
        } else {
            web.loadUrl(u);
        }
    }

    /** 主页：从 assets 读成字符串再 loadData，绕开 file:// 限制。 */
    public void loadHome() {
        try {
            java.io.InputStream in = act.getAssets().open("home.html");
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            url = com.vane.browser.settings.UiConfig.load().homePage;
            web.loadDataWithBaseURL(null, b.toString("UTF-8"), "text/html", "utf-8", null);
        } catch (Exception e) {
            web.loadUrl("about:blank");
        }
    }

    public boolean isHome() {
        String u = web.getUrl();
        if (u == null) return true;
        if (u.startsWith("file:///android_asset/")) return true;
        if (u.startsWith("about:") || u.startsWith("data:")) {
            return com.vane.browser.settings.UiConfig.load().homePage.equals(url)
                    || url == null || url.startsWith("file:///android_asset/");
        }
        return false;
    }

    public void destroy() {
        try {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.removeAllViews();
            web.destroy();
        } catch (Exception ignored) {}
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            return handle(req.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String u) {
            return handle(Uri.parse(u));
        }

        private boolean handle(Uri u) {
            if (u == null) return false;
            try {
                String full = u.toString();
                String low = full.toLowerCase();
                boolean media = low.matches(".*\\.(m3u8|mp4|flv|mkv|webm|mpd|mov)(\\?.*|#.*)?$");
                if (media && (low.startsWith("http"))) {
                    act.runOnUiThread(() -> act.askMediaOpen(full));
                    return true;              // 不交给 WebView，避免它自己占着播
                }
            } catch (Throwable ignored) {}
            try {
                String rules = com.vane.browser.settings.Prefs.get("block_links", "");
                if (!rules.isEmpty()) {
                    String full = u.toString();
                    for (String raw : rules.split("\\r?\\n")) {
                        final String k = raw.trim();
                        if (!k.isEmpty() && full.contains(k)) {
                            act.runOnUiThread(() -> com.vane.browser.ui.Widgets.toast(act, "已拦截链接：" + k));
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            String scheme = u.getScheme() == null ? "" : u.getScheme();
            switch (scheme) {
                case "http":
                case "https":
                case "file":
                case "about":
                case "data":
                case "blob":
                case "javascript":
                    return false;
                case "vane":
                    act.handleVaneUri(u);
                    return true;
                default:
                    act.openExternal(u);
                    return true;
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
            String host = req.getUrl().getHost();
            String pageHost = "";
            try { pageHost = android.net.Uri.parse(url == null ? "" : url).getHost(); } catch (Throwable ignored) {}
            boolean siteOff = false;
            if (pageHost != null && !pageHost.isEmpty()) {
                String off = com.vane.browser.settings.Prefs.get("site_noad", "");
                for (String x : off.split(",")) {
                    if (x.trim().equalsIgnoreCase(pageHost)) { siteOff = true; break; }
                }
            }
            boolean netHit = false;
            if (!siteOff && com.vane.browser.settings.Prefs.getBool("ad_strict", false)) {
                try {
                    com.vane.browser.core.FilterEngine.load(act);
                    netHit = com.vane.browser.core.FilterEngine.blocked(req.getUrl().toString());
                } catch (Throwable ignored) {}
            }
            boolean urlHit = false;
            if (!siteOff && act.blocker != null) {
                try { urlHit = act.blocker.shouldBlockUrl(req.getUrl().toString(), pageHost); } catch (Throwable ignored) {}
            }
            if (!siteOff && (netHit || urlHit || (act.blocker != null && act.blocker.shouldBlock(host)))) {
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView v, String u, Bitmap f) {
            try {
                if (u != null && (u.startsWith("http://") || u.startsWith("https://"))) internalLabel = null;
                loadStartAt = System.currentTimeMillis();
                boolean homeLoad = u != null && (u.startsWith("about:") || u.startsWith("data:"))
                        && isHome();
                if (!homeLoad) url = u;
                if (bridge != null) bridge.setPageUrl(u);
                act.onTabStateChanged();
            } catch (Throwable ignored) {}
        }

        @Override
        public void onPageFinished(WebView v, String u) {
            try {
            url = u;
            title = v.getTitle() == null ? u : v.getTitle();
            try { if (act.blocker != null) act.blocker.resetPageCount(); } catch (Throwable ignored) {}
            act.onTabStateChanged();
            act.syncHomeEngine(v);
            act.syncHomeCards(v);     // 主页：同步引擎名 + 图标
            applyDarkening();
            applyPageTheme();
            if (com.vane.browser.settings.Prefs.getBool("auto_sniff", false)) {
                v.evaluateJavascript(VideoSniffer.JS, null);
            }
            // 所有注入合并成一次 evaluateJavascript，减少跨进程往返
            StringBuilder inject = new StringBuilder();
            final String cosHostFinal = url == null ? "" : String.valueOf(android.net.Uri.parse(url).getHost());
            final android.webkit.WebView vFinal = v;
            // 元素规则首次加载放后台：首屏不等 1.3 万行解析
            new Thread(() -> {
                try {
                    com.vane.browser.core.Cosmetic.load(act);
                    String cosJs = com.vane.browser.core.Cosmetic.jsFor(cosHostFinal);
                    if (cosJs != null) vFinal.post(() -> vFinal.evaluateJavascript(cosJs, null));
                } catch (Throwable ignored) {}
            }, "cosmetic").start();
            String fontJs = customFontJs();
            if (fontJs != null) inject.append(fontJs);
            inject.append(VideoSniffer.LONG_PRESS_JS);
            inject.append(VideoSniffer.IMAGE_JS);
            inject.append(VideoSniffer.ELEMENT_JS);
            inject.append(VideoSniffer.PASSWORD_CAPTURE_JS);
            try {
                String us = com.vane.browser.core.UserScripts.codeFor(act, u);
                if (us != null) inject.append(us);
            } catch (Throwable ignored) {}
            if (inject.length() > 0) {
                v.evaluateJavascript(inject.toString(), null);
            }
            fillSavedPassword();
            applySiteMemory();
            try {
                String h = url == null ? "" : android.net.Uri.parse(url).getHost();
                String list = com.vane.browser.settings.Prefs.get("auto_translate", "");
                if (h != null && !h.isEmpty() && !u.startsWith("file:")) {
                    for (String x : list.split(",")) {
                        if (x.trim().equalsIgnoreCase(h)) {
                            act.runOnUiThread(() -> act.translatePage());
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (act.desktopMode) {
                v.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');" +
                        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                        "m.content='width=1280';})();", null);
            }
            } catch (Throwable ignored) {}
        }

        @Override
        public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail detail) {
            // 页面渲染进程崩了（常见于大站 OOM）。不处理的话系统会把整个 App 一起杀掉，
            // 表现就是「搜着搜着 App 就没了」。
            act.runOnUiThread(() -> act.handleRendererGone(BrowserTab.this));
            return true;
        }

        @Override
        public void onReceivedError(WebView v, int code, String desc, String failing) {
            act.onPageError(desc + " (" + failing + ")");
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onReceivedIcon(WebView v, android.graphics.Bitmap icon) {
            try {
                String h = android.net.Uri.parse(url == null ? "" : url).getHost();
                Favicons.save(act, h, icon);
            } catch (Throwable ignored) {}
        }

        @Override
        public void onProgressChanged(WebView v, int p) {
            try {
                progress = p;
                act.onProgress(p);
                if (p > 55) applyPageTheme();     // 加载中途就先暗下来，避免白屏闪过
            } catch (Throwable ignored) {}
        }

        @Override
        public void onReceivedTitle(WebView v, String t) {
            try {
                title = t == null ? url : t;
                act.onTabStateChanged();
            } catch (Throwable ignored) {}
        }

        @Override
        public void onCloseWindow(WebView w) {
            // B站这类站会用 window.close() 把页面自己关掉，我们不能跟着关标签
            try { w.stopLoading(); } catch (Throwable ignored) {}
        }

        @Override
        public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, android.os.Message resultMsg) {
            if (com.vane.browser.settings.Prefs.getBool("block_popup", false)) {
                act.runOnUiThread(() -> com.vane.browser.ui.Widgets.toast(act, "已拦截一个弹窗"));
                return true;      // 吞掉，不开新窗口
            }
            try {
                BrowserTab popup = act.newTabForPopup();
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup.web);
                resultMsg.sendToTarget();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            act.runOnUiThread(() -> act.askPermission(request));
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            return act.pickFile(cb, params);
        }

        @Override
        public void onShowCustomView(android.view.View view, CustomViewCallback cb) {
            act.enterFullscreen(view, cb);
        }

        @Override
        public void onHideCustomView() {
            act.exitFullscreen();
        }
    }
}
