package com.vane.browser.core;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/** DNS 预热：把即将访问的域名先在后台解析掉，点开时少一次解析等待。 */
public final class DnsWarm {

    private static final Map<String, Long> DONE = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 48; }
    };

    private DnsWarm() {}

    public static void warm(final String host) {
        if (host == null || host.isEmpty()) return;
        synchronized (DONE) {
            Long t = DONE.get(host);
            if (t != null && System.currentTimeMillis() - t < 10 * 60 * 1000) return;
            DONE.put(host, System.currentTimeMillis());
        }
        new Thread(() -> {
            try {
                InetAddress[] all = InetAddress.getAllByName(host);
                if (all != null && all.length > 0) {
                    // 顺手连一下 443，把 TCP/TLS 握手也做掉（WebView 会复用系统连接缓存的一部分）
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.connect(new java.net.InetSocketAddress(all[0], 443), 1500);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }, "dns-warm").start();
    }

    /** 从一串 URL 里提取域名批量预热。 */
    public static void warmUrls(java.util.List<String> urls) {
        java.util.Set<String> hosts = new java.util.HashSet<>();
        for (String u : urls) {
            try {
                String h = android.net.Uri.parse(u).getHost();
                if (h != null && !h.isEmpty()) hosts.add(h);
            } catch (Exception ignored) {}
        }
        int i = 0;
        for (String h : hosts) {
            if (i++ >= 3) break;
            warm(h);
        }
    }
}
