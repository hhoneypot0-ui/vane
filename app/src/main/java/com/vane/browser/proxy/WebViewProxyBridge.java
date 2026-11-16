package com.vane.browser.proxy;

import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 把 WebView 的流量指到本地分流代理（或者用户自己填的代理地址）。 */
public final class WebViewProxyBridge {

    private static final String TAG = "VaneProxy";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private WebViewProxyBridge() {}

    public static boolean supported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);
    }

    public static void set(String host, int port) {
        if (!supported()) {
            Log.w(TAG, "当前 WebView 不支持代理设置（PROXY_OVERRIDE），需要 Android System WebView 76+");
            return;
        }
        String rule = host + ":" + port;
        ProxyConfig cfg = new ProxyConfig.Builder()
                .addProxyRule(rule)
                .addDirect()
                .build();
        ProxyController.getInstance().setProxyOverride(cfg, EXEC, () -> Log.i(TAG, "代理已生效：" + rule));
    }

    public static void clear() {
        if (!supported()) return;
        ProxyController.getInstance().clearProxyOverride(EXEC, () -> Log.i(TAG, "代理已关闭"));
    }

    public static void warmUp(WebView w) {
        // 某些机型需要先加载过页面才能应用代理覆盖
        if (w != null) w.loadUrl("about:blank");
    }
}
