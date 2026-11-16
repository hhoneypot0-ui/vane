package com.vane.browser.proxy;

import android.content.Context;

import com.vane.browser.settings.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 代理总控：订阅、节点、分流、启动/停止。 */
public class ProxyManager {

    private static ProxyManager INSTANCE;

    public static synchronized ProxyManager get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new ProxyManager(ctx.getApplicationContext());
        return INSTANCE;
    }

    private final Context ctx;
    public final RuleEngine rules;
    public final List<Subscription> subs = new ArrayList<>();
    public final List<Node> nodes = new ArrayList<>();
    public String selectedNode = "";
    public boolean enabled = false;
    public int localPort = 0;
    public String lastError = "";
    public final List<String> logs = new ArrayList<>();

    private SplitProxyServer server;
    private ProxyCore core;

    private ProxyManager(Context ctx) {
        this.ctx = ctx;
        this.rules = new RuleEngine(ctx);
        this.core = new ProxyCore(ctx);
        load();
    }

    // ---------- 持久化 ----------

    public void load() {
        JSONArray arr = Prefs.getArray("subs");
        subs.clear();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) subs.add(Subscription.fromJson(o));
        }
        rebuildNodes();
        selectedNode = Prefs.get("node_selected", nodes.isEmpty() ? "" : nodes.get(0).name);
        enabled = Prefs.getBool("proxy_enabled", false);
    }

    public void save() {
        // 订阅里的节点也同步保存，避免下次重建时丢
        JSONArray arr = new JSONArray();
        for (Subscription s : subs) arr.put(s.toJson());
        Prefs.putArray("subs", arr);
        Prefs.put("node_selected", selectedNode);
        Prefs.putBool("proxy_enabled", enabled);
    }

    public void rebuildNodes() {
        nodes.clear();
        for (Subscription s : subs) nodes.addAll(s.nodes);
        JSONArray manual = Prefs.getArray("nodes_manual");
        for (int i = 0; i < manual.length(); i++) {
            JSONObject o = manual.optJSONObject(i);
            if (o != null) nodes.add(Node.fromJson(o));
        }
    }

    public void addManualNode(Node n) {
        JSONArray arr = Prefs.getArray("nodes_manual");
        arr.put(n.toJson());
        Prefs.putArray("nodes_manual", arr);
        rebuildNodes();
    }

    public Node node() {
        for (Node n : nodes) if (n.name.equals(selectedNode)) return n;
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    // ---------- 订阅 ----------

    public interface Callback { void done(boolean ok, String msg); }

    public void refreshSubscription(final Subscription sub, final Callback cb) {
        new Thread(() -> {
            try {
                String text = fetch(sub.url);
                List<Node> parsed = SubscriptionParser.parse(text);
                sub.nodes = parsed;
                sub.updated = System.currentTimeMillis();
                sub.error = parsed.isEmpty() ? "解析到 0 个节点" : "";
                rebuildNodes();
                save();
                if (cb != null) cb.done(!parsed.isEmpty(), parsed.isEmpty() ? sub.error : ("已更新 " + parsed.size() + " 个节点"));
            } catch (Exception e) {
                sub.error = e.getMessage();
                if (cb != null) cb.done(false, "刷新失败：" + e.getMessage());
            }
        }, "sub-refresh").start();
    }

    /** 不联网，直接用粘贴的内容建订阅。 */
    public void addSubscriptionFromText(String name, String text) {
        Subscription s = new Subscription();
        s.url = "manual://" + name;
        s.name = name;
        try {
            s.nodes = SubscriptionParser.parse(text);
            s.updated = System.currentTimeMillis();
            s.error = s.nodes.isEmpty() ? "内容里没解析到节点" : "";
        } catch (Exception e) {
            s.error = "解析失败：" + e.getMessage();
        }
        subs.add(s);
        rebuildNodes();
        save();
    }

    public void addSubscription(String url) {
        Subscription s = new Subscription();
        s.url = url.trim();
        s.name = "订阅 " + (subs.size() + 1);
        subs.add(s);
        refreshSubscription(s, null);
    }

    public void removeSubscription(Subscription s) {
        subs.remove(s);
        rebuildNodes();
        save();
    }

    /** 取订阅内容：先按 URL 猜 UA（和最早版本一致），失败再少量轮换，避免把机场 WAF 惹毛。 */
    public static String fetch(String url) throws Exception {
        boolean looksClash = url.contains("clash");
        String first = looksClash ? "clash-verge/v1.6.0" : "Vane/1.0 (Android)";
        String other = looksClash ? "Vane/1.0 (Android)" : "clash-verge/v1.6.0";

        // 上次成功过的 UA 优先（记在 prefs 里）
        String remembered = com.vane.browser.settings.Prefs.get("sub_ua", "");
        String[] order = remembered.isEmpty()
                ? new String[]{first, other}
                : new String[]{remembered, first, other};

        Exception last = null;
        for (String ua : order) {
            try {
                String text = fetchWith(url, ua);
                if (text != null && !text.trim().isEmpty()) {
                    com.vane.browser.settings.Prefs.put("sub_ua", ua);
                    return text;
                }
            } catch (Exception e) {
                last = e;
                // 403 多半是请求过密被挡，先停一下别继续打
                if (e.getMessage() != null && e.getMessage().contains("403")) {
                    try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                }
            }
        }
        String[] variants = {url + (url.contains("?") ? "&" : "?") + "flag=clash"};
        for (String v : variants) {
            try {
                String text = fetchWith(v, "clash-verge/v1.6.0");
                if (text != null && !text.trim().isEmpty()) return text;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new Exception("服务器没有返回内容") : last;
    }

    /** 诊断用：只发一次请求，把状态和内容原样返回。 */
    public static String fetchRaw(String url) throws Exception {
        boolean clash = url.contains("clash");
        return fetchWith(url, clash ? "clash-verge/v1.6.0" : "Vane/1.0 (Android)");
    }

    public java.util.List<Subscription> subscriptions() {
        return subs;
    }

    private static String fetchWith(String url, String ua) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("Accept-Encoding", "gzip");
        int code = c.getResponseCode();
        if (code >= 400) {
            String body = "";
            try (InputStream es = c.getErrorStream()) {
                if (es != null) {
                    ByteArrayOutputStream eb = new ByteArrayOutputStream();
                    byte[] b2 = new byte[4096];
                    int m;
                    while ((m = es.read(b2)) > 0) eb.write(b2, 0, m);
                    body = eb.toString("UTF-8");
                }
            } catch (Exception ignored) {}
            String hint = body.replaceAll("\\s+", " ").trim();
            if (hint.length() > 140) hint = hint.substring(0, 140);
            throw new Exception("服务器返回 " + code + (hint.isEmpty() ? "" : "：" + hint));
        }
        InputStream in = c.getInputStream();
        java.io.BufferedInputStream bin = new java.io.BufferedInputStream(in);
        bin.mark(2);
        int b0 = bin.read(), b1 = bin.read();
        bin.reset();
        if (b0 == 0x1f && b1 == 0x8b) in = new java.util.zip.GZIPInputStream(bin);
        else in = bin;
        try (InputStream src = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    // ---------- 启动 / 停止 ----------

    /**
     * 内核状态：给界面显示用。
     * 这里绝不执行进程——上次就是因为把 80MB 内核的 version 跑在主线程，弹个面板都卡。
     */
    public boolean enabled() {
        return enabled;
    }

    /** 取一个空闲本地端口给内核用。 */
    private static int freePort() {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        } catch (Exception e) {
            return ProxyCore.PORT;
        }
    }

    /** 真的在跑：分流器或内核有一个在工作，并且本地端口有效。 */
    /** 给自检用的内核对象。 */
    public ProxyCore core() {
        return core;
    }

    public boolean isRunning() {
        if (localPort <= 0) return false;
        if (core != null && core.isRunning()) return true;
        return server != null && server.isRunning();
    }

    public boolean isStarting() {
        return starting;
    }

    public boolean coreRunning() {
        return core != null && core.isRunning() && localPort > 0;
    }

    /** 通过本地端口（走节点）取一个 URL，返回 状态码 + 正文前 200 字。 */
    public String fetchViaLocalProxy(String url) {
        if (localPort <= 0) return "本地端口未就绪（localPort=0）";
        try {
            java.net.Proxy p = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress("127.0.0.1", localPort));
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection(p);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "VaneDiag/1.0");
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while (in != null && (n = in.read(buf)) > 0) b.write(buf, 0, n);
            String body = b.toString("UTF-8");
            return "HTTP " + code + " · " + (body.length() > 200 ? body.substring(0, 200) : body);
        } catch (Exception e) {
            return "失败：" + e.getClass().getSimpleName() + " " + e.getMessage();
        }
    }

    /** 不走代理直连同一个 URL，用来对比。 */
    public static String fetchDirect(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "VaneDiag/1.0");
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while (in != null && (n = in.read(buf)) > 0) b.write(buf, 0, n);
            String body = b.toString("UTF-8");
            return "HTTP " + code + " · " + (body.length() > 200 ? body.substring(0, 200) : body);
        } catch (Exception e) {
            return "失败：" + e.getClass().getSimpleName() + " " + e.getMessage();
        }
    }

    public String kernelInfo() {
        if (core == null) return "内核：未初始化";
        if (!core.available()) {
            return "内核：未内置（当前 ABI " + com.vane.browser.proxy.ProxyCore.abi() + "）";
        }
        String v = core.cachedVersion();
        return "内核：" + (v == null ? "sing-box（版本检测中）" : ("sing-box " + v))
                + (core.isRunning() ? " · 运行中" : "");
    }

    /** 后台探测一次内核版本，界面下次刷新就能显示。 */
    public void probeKernelAsync() {
        if (core == null || !core.available() || core.cachedVersion() != null) return;
        new Thread(() -> core.version(), "kernel-version").start();
    }

    public boolean kernelAvailable() {
        return core != null && core.available();
    }

    public void log(String s) {
        synchronized (logs) {
            logs.add(s);
            if (logs.size() > 300) logs.remove(0);
        }
    }

    private volatile boolean starting = false;

    public synchronized void start() {
        if (starting) return;        // 正在启动中
        if (isRunning()) {            // 已经在跑：绝不重启（重启会杀旧内核、换端口，导致请求全失败）
            if (localPort > 0) WebViewProxyBridge.set("127.0.0.1", localPort);
            return;
        }
        starting = true;
        try {
            startInner();
        } finally {
            starting = false;
        }
    }

    private void startInner() {
        Node n = node();
        String attempt = "start: node=" + (n == null ? "无" : (n.type + " " + n.name))
                + " server=" + (n == null ? "-" : (n.server + ":" + n.port))
                + " kernel=" + core.version()
                + " bin=" + (core.binary() == null ? "缺失" : core.binary().getAbsolutePath())
                + " canExec=" + (core.binary() != null && core.binary().canExecute());
        log(attempt);
        try {
            com.vane.browser.App.logEvent(ctx, "proxy-start", attempt);
        } catch (Throwable ignored) {}
        if (n == null) {
            lastError = "还没有任何节点，先添加订阅或手动节点";
            enabled = false;
            WebViewProxyBridge.clear();
            return;
        }
        lastError = "";
        WebViewProxyBridge.clear();   // 先摘掉旧代理，端口还没监听时页面才不会全部失败
        try {
            if (n.isPlainProxy() || n.type.equals("ss")) {
                // 纯 Java 分流器：http/socks 节点可直连；ss 交给内核（若存在）
                if (n.isPlainProxy()) {
                    stopInternal();
                    server = new SplitProxyServer(rules, n, this::log);
                    localPort = server.start();
                    WebViewProxyBridge.set("127.0.0.1", localPort);
                    enabled = true;
                    save();
                    log("已启用本地分流，上游 " + n.summary());
                    try { ProxyKeepAlive.start(ctx); } catch (Throwable ignored) {}
                    return;
                }
            }
            // 需要内核的协议
            com.vane.browser.proxy.CoreConfig.fastOpen =
                    com.vane.browser.settings.Prefs.getBool("proxy_fastopen", true);
            com.vane.browser.proxy.CoreConfig.multiplex =
                    com.vane.browser.settings.Prefs.getBool("proxy_mux", false);
            int port = freePort();   // 每次随机取空闲端口，避免被上次没退干净的内核占住
            int p = core.start(CoreConfig.build(nodes, n.name, rules, port), port, this::log);
            if (p > 0) {
                // 只停掉纯 Java 分流器；绝不能调 stopInternal()——那会把刚起来的内核也杀掉
                if (server != null) {
                    server.stop();
                    server = null;
                }
                localPort = p;
                WebViewProxyBridge.set("127.0.0.1", localPort);
                enabled = true;
                save();
                log("内核分流已启用（" + n.summary() + "）");
                try {
                    com.vane.browser.App.logEvent(ctx, "proxy-start-ok",
                            attempt + "\nlocalPort=" + localPort);
                } catch (Throwable ignored) {}
            } else {
                WebViewProxyBridge.clear();
                if (core.available()) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> ls = core.logs();
                    for (int i = Math.max(0, ls.size() - 25); i < ls.size(); i++) sb.append(ls.get(i)).append('\n');
                    lastError = "内核启动失败";
                    try {
                        com.vane.browser.App.logEvent(ctx, "core-start-failed",
                                "node: " + n.type + " " + n.name + "\nport: " + port + "\n\n" + sb);
                    } catch (Throwable ignored) {}
                } else {
                    lastError = "该节点（" + n.type + "）需要内核：把 libsingbox.so 打进 jniLibs/" + ProxyCore.abi();
                }
                enabled = false;
            }
        } catch (Exception e) {
            WebViewProxyBridge.clear();
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            enabled = false;
            log("启动异常：" + lastError);
            try {
                StringBuilder sb = new StringBuilder(attempt).append('\n').append(lastError);
                java.util.List<String> ls = core.logs();
                for (int i = Math.max(0, ls.size() - 15); i < ls.size(); i++) sb.append('\n').append(ls.get(i));
                com.vane.browser.App.logEvent(ctx, "proxy-start-failed", sb.toString());
            } catch (Throwable ignored) {}
        }
        save();
    }

    private void stopInternal() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (core != null) core.stop();
        localPort = 0;
        try { ProxyKeepAlive.stop(ctx); } catch (Throwable ignored) {}
    }

    public synchronized void stop() {
        stopInternal();
        WebViewProxyBridge.clear();
        enabled = false;
        save();
    }

    /** 节点连通性：TCP 连 server:port 的耗时（毫秒），失败 -1。 */
    public final java.util.Map<String, Integer> latency = new java.util.concurrent.ConcurrentHashMap<>();

    public interface TestCallback { void done(); }

    public void testAllAsync(final TestCallback cb) {
        new Thread(() -> {
            for (Node n : nodes) {
                if (n.server == null || n.server.isEmpty() || n.port <= 0) {
                    latency.put(n.name, -1);
                    continue;
                }
                long t0 = System.currentTimeMillis();
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(n.server, n.port), 3000);
                    latency.put(n.name, (int) (System.currentTimeMillis() - t0));
                } catch (Exception e) {
                    latency.put(n.name, -1);
                }
            }
            if (cb != null) cb.done();
        }, "node-test").start();
    }

    public String latencyText(String name) {
        if (name == null) return lastLatency < 0 ? "未测" : (lastLatency + " ms");
        Integer v = latency.get(name);
        if (v == null) return "";
        return v < 0 ? " · 连不上" : (" · " + v + " ms");
    }

    // ---- 节点延迟 / 地区（给界面显示用）----
    private volatile int lastLatency = -1;
    private volatile long lastProbe = 0;
    private volatile String exitCountry = null;   // 真实出口国家（经节点查得）
    private volatile String exitIp = null;
    private volatile long exitGeoAt = 0;

    public String exitIp() {
        return exitIp;
    }

    /**
     * 经本地代理查一次出口 IP 的归属地（请求本身走节点，所以拿到的是节点的出口国家）。
     * 结果缓存 10 分钟。失败就退回按节点名猜。
     */
    public void probeExitGeo(Runnable onDone) {
        if (!enabled || localPort <= 0) {
            if (onDone != null) onDone.run();
            return;
        }
        if (exitCountry != null && System.currentTimeMillis() - exitGeoAt < 10 * 60 * 1000) {
            if (onDone != null) onDone.run();
            return;
        }
        new Thread(() -> {
            String[] r = fetchGeo();
            if (r != null) {
                exitCountry = r[0];
                exitIp = r[1];
                exitGeoAt = System.currentTimeMillis();
            }
            if (onDone != null) onDone.run();
        }, "exit-geo").start();
    }

    private String[] fetchGeo() {
        String[][] apis = {
                {"http://ip-api.com/json/?fields=status,country,query&lang=zh-CN", "country"},
                {"https://ipinfo.io/json", "country"},
        };
        for (String[] api : apis) {
            try {
                java.net.Proxy p = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress("127.0.0.1", localPort));
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(api[0]).openConnection(p);
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Vane/1.0");
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                org.json.JSONObject o = new org.json.JSONObject(b.toString("UTF-8"));
                String country = o.optString("country", "");
                String ip = o.optString("query", o.optString("ip", ""));
                if (!country.isEmpty()) return new String[]{country, ip};
            } catch (Exception ignored) {}
        }
        return null;
    }

    public int lastLatency() {
        return lastLatency;
    }

    /** 节点名里通常写着地区，先从名字猜；猜不到就显示未知。 */
    public String nodeCountry() {
        Node n = node();
        if (n == null) return "未知";
        String text = (n.name == null ? "" : n.name) + " " + (n.server == null ? "" : n.server);
        String up = text.toUpperCase();
        String[][] map = {
                {"香港", "香港"}, {"HK", "香港"}, {"HONG KONG", "香港"}, {"HKG", "香港"},
                {"台湾", "台湾"}, {"臺灣", "台湾"}, {"TW", "台湾"}, {"TAIWAN", "台湾"},
                {"日本", "日本"}, {"JP", "日本"}, {"东京", "日本"}, {"大阪", "日本"}, {"TOKYO", "日本"},
                {"新加坡", "新加坡"}, {"SG", "新加坡"}, {"SINGAPORE", "新加坡"},
                {"美国", "美国"}, {"US", "美国"}, {"洛杉矶", "美国"}, {"圣何塞", "美国"}, {"西雅图", "美国"},
                {"纽约", "美国"}, {"硅谷", "美国"}, {"LOS ANGELES", "美国"},
                {"韩国", "韩国"}, {"KR", "韩国"}, {"首尔", "韩国"}, {"SEOUL", "韩国"},
                {"英国", "英国"}, {"UK", "英国"}, {"伦敦", "英国"},
                {"德国", "德国"}, {"DE", "德国"}, {"法兰克福", "德国"},
                {"法国", "法国"}, {"FR", "法国"}, {"荷兰", "荷兰"}, {"NL", "荷兰"},
                {"俄罗斯", "俄罗斯"}, {"RU", "俄罗斯"}, {"加拿大", "加拿大"}, {"CA", "加拿大"},
                {"澳大利亚", "澳大利亚"}, {"AU", "澳大利亚"}, {"印度", "印度"}, {"IN", "印度"},
                {"越南", "越南"}, {"VN", "越南"}, {"泰国", "泰国"}, {"TH", "泰国"},
                {"马来", "马来西亚"}, {"MY", "马来西亚"}, {"菲律宾", "菲律宾"}, {"PH", "菲律宾"},
                {"印尼", "印度尼西亚"}, {"ID", "印度尼西亚"}, {"土耳其", "土耳其"}, {"TR", "土耳其"},
                {"巴西", "巴西"}, {"BR", "巴西"}, {"迪拜", "阿联酋"}, {"AE", "阿联酋"},
        };
        for (String[] m : map) {
            if (up.contains(m[0])) return m[1];
        }
        return "未知";
    }

    /** 页面信息里那一行：已连接/未连接 + 地区 + 延迟。 */
    /** 展示用的国家：优先真实出口，其次节点名。 */
    public String displayCountry() {
        return exitCountry != null && !exitCountry.isEmpty() ? exitCountry : nodeCountry();
    }

    public String linkLine() {
        Node n = node();
        if (!enabled) return "代理：未连接";
        if (n == null) return "代理：已开，但没有节点";
        String lat;
        if (lastProbe == 0) lat = "正在测速…";
        else if (lastLatency < 0) return "代理：已开启，但节点连不上 · " + displayCountry();
        else lat = lastLatency + " ms";
        return "代理：已连接 · " + displayCountry() + " · " + lat;
    }

    /** 后台测一次到节点的 TCP 延迟（3 秒超时），完成后回调。 */
    public void probeLatency(Runnable onDone) {
        final Node n = node();
        if (n == null) {
            lastLatency = -1;
            if (onDone != null) onDone.run();
            return;
        }
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(n.server, n.port), 3000);
                lastLatency = (int) (System.currentTimeMillis() - t0);
            } catch (Exception e) {
                lastLatency = -1;
            }
            lastProbe = System.currentTimeMillis();
            if (onDone != null) onDone.run();
        }, "node-latency").start();
    }

    public String status() {
        if (!enabled) return "代理：关闭";
        Node n = node();
        return "代理：开 · " + (n == null ? "-" : n.name) + " · " + modeName(rules.mode()) + " · 127.0.0.1:" + localPort;
    }

    public static String modeName(String mode) {
        switch (mode) {
            case RuleEngine.MODE_GLOBAL: return "全局";
            case RuleEngine.MODE_DIRECT: return "直连";
            default: return "规则分流";
        }
    }
}
