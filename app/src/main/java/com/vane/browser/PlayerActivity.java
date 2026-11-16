package com.vane.browser;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import android.app.PictureInPictureParams;
import android.util.Rational;
import java.util.Map;

/**
 * 内置播放器：hls.js + 原生 <video>，所有请求自动带上当前页面的 Referer / UA / Cookie，
 * 用来解决绝大多数站点的防盗链，做到「点开就能看」。
 */
public class PlayerActivity extends AppCompatActivity {

    private LinearLayout bar;

    private UiConfig ui;
    private WebView web;
    private String url = "", title = "", referer = "";
    private TextView titleView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ui = UiConfig.load();
        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        referer = getIntent().getStringExtra("referer");
        if (url == null) url = "";
        if (title == null) title = "播放器";
        if (referer == null) referer = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF0B0B0D);
        bar.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        TextView back = Widgets.text(this, "‹", 22, 0xFFFFFFFF);
        back.setGravity(android.view.Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Theme.dp(this, 36), Theme.dp(this, 36)));

        titleView = Widgets.text(this, title, 14, 0xFFE9EAEE);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView more = Widgets.text(this, "⋮", 20, 0xFFFFFFFF);
        more.setGravity(android.view.Gravity.CENTER);
        more.setOnClickListener(v -> showMenu());
        bar.addView(more, new LinearLayout.LayoutParams(Theme.dp(this, 36), Theme.dp(this, 36)));

        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(this, 48)));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setAllowFileAccess(true);
        // file:// 页面要能加载网络媒体，必须放开 file 源访问
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setUserAgentString(Prefs.get("ua_mode", "default").equals("iphone")
                ? "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
                : "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
        web.setBackgroundColor(0xFF000000);
        web.addJavascriptInterface(new PlayerBridge(), "VanePlayer");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (!u.startsWith("http")) return null;
                String r = req.getRequestHeaders().get("Referer");
                if (r == null) r = referer;
                // 只有当 Referer 是「另一个网页」时才自己取流（防盗链）；
                // 否则交给 WebView 原生加载，兼容性和性能都更好。
                boolean sameHost = false;
                try {
                    String mediaHost = android.net.Uri.parse(u).getHost();
                    String refHost = android.net.Uri.parse(r == null ? "" : r).getHost();
                    sameHost = mediaHost != null && mediaHost.equals(refHost);
                } catch (Throwable ignored) {}
                if (r == null || r.isEmpty() || sameHost) return null;
                String range = req.getRequestHeaders().get("Range");
                WebResourceResponse resp = proxyFetch(u, r, range);
                return resp;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(root);

        String playUrl = "file:///android_asset/player.html?src=" + URLEncoder.encode(url)
                + "&referer=" + URLEncoder.encode(referer)
                + "&name=" + URLEncoder.encode(title);
        web.loadUrl(playUrl);
    }

    /** 页面里的画中画按钮 */
    private class PlayerBridge {
        @JavascriptInterface
        public void pip() {
            runOnUiThread(() -> PlayerActivity.this.enterPip());
        }
    }

    private void enterPip() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
                b.setAspectRatio(new Rational(16, 9));
                enterPictureInPictureMode(b.build());
            } catch (Exception e) {
                Widgets.toast(this, "画中画不可用：" + e.getMessage());
            }
        } else {
            Widgets.toast(this, "系统版本不支持画中画（需要 Android 8+）");
        }
    }

    /** 投屏 / 交给外部播放器 */
    private void castOrExternal() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        i.setDataAndType(android.net.Uri.parse(url), "video/*");
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(android.content.Intent.createChooser(i, "投屏 / 用其他播放器打开"));
        } catch (Exception e) {
            Widgets.toast(this, "没有可接收视频的应用");
        }
    }

    /** 带 Referer/UA/Cookie 的取流，解决视频防盗链。 */
    private WebResourceResponse proxyFetch(String u, String ref, String range) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", web.getSettings().getUserAgentString());
            c.setRequestProperty("Accept", "*/*");
            if (ref != null && !ref.isEmpty()) c.setRequestProperty("Referer", ref);
            String cookie = CookieManager.getInstance().getCookie(u);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (range != null) c.setRequestProperty("Range", range);
            int code = c.getResponseCode();
            String type = c.getContentType();
            if (type == null) type = u.contains(".m3u8") ? "application/vnd.apple.mpegurl"
                    : (u.contains(".ts") ? "video/mp2t" : "application/octet-stream");
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, java.util.List<String>> e : c.getHeaderFields().entrySet()) {
                if (e.getKey() == null) continue;
                String k = e.getKey().toLowerCase();
                if (k.equals("content-range") || k.equals("content-length") || k.equals("accept-ranges")
                        || k.equals("content-type")) {
                    headers.put(e.getKey(), e.getValue().get(0));
                }
            }
            WebResourceResponse resp = new WebResourceResponse(type, "utf-8", code,
                    code >= 400 ? "ERR" : "OK", headers, in);
            return resp;
        } catch (Exception e) {
            return null;
        }
    }

    /** 边看边存：直接把当前播放地址交给内建下载器，落 下载/Vane。 */
    private void downloadThisVideo() {
        try {
            String name = "video-" + System.currentTimeMillis();
            try {
                String seg = android.net.Uri.parse(url).getLastPathSegment();
                if (seg != null && seg.length() > 3) name = seg;
            } catch (Exception ignored) {}
            final String fn = name;
            Widgets.toast(this, "开始下载…");
            com.vane.browser.core.Dl.start(this, url, fn, new com.vane.browser.core.Dl.Cb() {
                @Override public void progress(long done, long total) {}
                @Override public void done(boolean ok, String path, String msg) {
                    runOnUiThread(() -> Widgets.info(PlayerActivity.this, ui,
                            ok ? "下载完成" : "下载失败", ok ? path : msg));
                }
            });
        } catch (Exception e) {
            Widgets.toast(this, "下载失败：" + e.getMessage());
        }
    }

    /**
     * 播放器手势：右半边上下滑调媒体音量，左半边上下滑调屏幕亮度。
     * 放在 dispatchTouchEvent 里做：setOnTouchListener 返回 false 后收不到 MOVE，
     * 返回 true 又会把网页点击吃掉，这里只在「竖滑超过阈值」时消费事件。
     */
    private float gStartY, gStartX, gStartVal;
    private int gMode;                       // 0=未定 1=亮度 2=音量
    private boolean gDirty;                  // 这次手势有没有真的改过值
    private String gApplied = "";

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        android.media.AudioManager am =
                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                gStartY = e.getY();
                gStartX = e.getX();
                gMode = 0;
                gStartVal = 0;
                gDirty = false;
                break;
            case android.view.MotionEvent.ACTION_MOVE: {
                float dy = gStartY - e.getY();
                float dx = Math.abs(e.getX() - gStartX);
                if (gMode == 0 && Math.abs(dy) > 45 && Math.abs(dy) > dx * 1.4f) {
                    gMode = e.getX() > (web == null ? 0 : web.getWidth()) / 2f ? 2 : 1;
                    if (gMode == 2 && am != null)
                        gStartVal = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                    else
                        gStartVal = getWindow().getAttributes().screenBrightness;
                }
                if (gMode == 2 && am != null) {
                    int max = Math.max(1, am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
                    int target = Math.round(gStartVal + dy / 300f * max);
                    target = Math.max(0, Math.min(max, target));
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0);
                    Widgets.toast(this, "音量 " + (target * 100 / max) + "%");
                    gDirty = true;
                    gApplied = "volume=" + target + "/" + max;
                    return true;
                }
                if (gMode == 1) {
                    float base = gStartVal < 0 ? 0.5f : gStartVal;
                    float b = Math.max(0.05f, Math.min(1f, base + dy / 600f));
                    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = b;
                    getWindow().setAttributes(lp);
                    Widgets.toast(this, "亮度 " + Math.round(b * 100) + "%");
                    gDirty = true;
                    gApplied = "brightness=" + Math.round(b * 100) + "%";
                    return true;
                }
                break;
            }
            default:
                if (gDirty) {
                    gDirty = false;
                    try {
                        com.vane.browser.App.logEvent(this, "player-gesture",
                                "mode=" + (gMode == 2 ? "volume" : "brightness")
                                        + "\napplied: " + gApplied);
                    } catch (Throwable ignored) {}
                }
                gMode = 0;
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    private void showMenu() {
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("复制播放地址");
        items.add("投屏 / 外部播放器");
        items.add("画中画");
        items.add("用系统播放器打开");
        items.add("下载这个视频");
        items.add("重新加载");
        items.add("倍速 1.0x");
        items.add("倍速 1.5x");
        items.add("倍速 2.0x");
        Widgets.pick(this, ui, "播放器", items, i -> {
            switch (i) {
                case 0:
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url));
                    Widgets.toast(this, "已复制");
                    break;
                case 1:
                    castOrExternal();
                    break;
                case 2:
                    enterPip();
                    break;
                case 3:
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)));
                    } catch (Exception e) {
                        Widgets.toast(this, "没有可用的系统播放器");
                    }
                    break;
                case 4:
                    downloadThisVideo();
                    break;
                case 5:
                    web.reload();
                    break;
                default:
                    float r = i == 6 ? 1.0f : (i == 7 ? 1.5f : 2.0f);
                    web.evaluateJavascript("window.setRate && window.setRate(" + r + ")", null);
                    break;
            }
        });
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        // 进小窗时把顶栏藏掉，画面铺满；退出再显示
        try {
            if (bar != null) bar.setVisibility(inPip ? View.GONE : View.VISIBLE);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) {
            return;                       // 画中画里按返回先退出小窗
        }
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
