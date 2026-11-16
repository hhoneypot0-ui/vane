package com.vane.browser.core;

import android.content.Context;
import android.text.TextUtils;

import com.vane.browser.settings.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 本地 hosts 式广告拦截。
 *
 * 规则来源：assets/hosts.txt（约 7.8 万条，内置）+ files/rules/hosts.txt（用户自定义）。
 * 不联网、不上报、不做任何统计。
 *
 * 规则量大，加载放在后台线程做，加载完成前放行所有请求（不会卡启动）。
 */
public class AdBlocker {

    private final Context ctx;
    private volatile Set<String> block = Collections.emptySet();
    private volatile Set<String> white = Collections.emptySet();
    private volatile boolean ready = false;
    private volatile boolean enabled = true;

    private final java.util.List<String> urlRules = new java.util.ArrayList<>();
    private boolean strict = false;

    /** 严格模式：额外按 URL 规则拦广告 JS/接口。 */
    public void setStrict(boolean b) { strict = b; }

    /** URL 级规则：assets + 远端/自定义，支持 * 通配。 */
    private void loadUrlRules(Context c) {
        urlRules.clear();
        for (String path : new String[]{"urlrules.txt"}) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(c.getAssets().open(path)), 8192)) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) urlRules.add(line);
                }
            } catch (Exception ignored) {}
        }
        File f = new File(c.getFilesDir(), "rules/remote-url.txt");
        if (f.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) urlRules.add(line);
                }
            } catch (Exception ignored) {}
        }
        String custom = com.vane.browser.settings.Prefs.get("ad_url_custom", "");
        for (String line : custom.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) urlRules.add(line);
        }
    }

    /** 这条 URL 是否命中广告规则（仅严格模式生效）。 */
    public boolean shouldBlockUrl(String url) {
        return shouldBlockUrl(url, null);
    }

    /** pageHost 与请求同域时，只允许明确广告特征命中（避免误伤站点自身接口）。 */
    public boolean shouldBlockUrl(String url, String pageHost) {
        if (!strict || url == null || url.isEmpty()) return false;
        boolean firstParty = false;
        try {
            String reqHost = android.net.Uri.parse(url).getHost();
            firstParty = pageHost != null && reqHost != null && reqHost.endsWith(pageHost);
        } catch (Throwable ignored) {}
        for (String rule : urlRules) {
            if (matches(rule, url)) {
                if (firstParty && !rule.matches(".*(adserver|advert|adsbygoogle|pagead|nativead|popunder).*")) {
                    continue;                    // 第一方 + 非明显广告 → 放行
                }
                pageBlocked++;
                totalBlocked++;
                bytesSaved += 30 * 1024;
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String rule, String url) {
        if (!rule.contains("*")) return url.contains(rule);
        StringBuilder re = new StringBuilder();
        for (String part : rule.split("\\*")) {
            if (re.length() > 0) re.append(".*");
            re.append(java.util.regex.Pattern.quote(part));
        }
        try {
            return url.matches(".*" + re + ".*");
        } catch (Exception e) {
            return false;
        }
    }

    public AdBlocker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        enabled = Prefs.getBool("adblock", true);
        Thread t = new Thread(this::loadAll, "adblock-load");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void loadAll() {
        Set<String> b = new HashSet<>(90000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open("hosts.txt")), 65536)) {
            String line;
            while ((line = r.readLine()) != null) parseLine(line, b);
        } catch (Exception ignored) {}
        // 用户自定义规则
        try {
            addCustom(Prefs.get("ad_custom", ""));
        loadUrlRules(ctx);
        strict = com.vane.browser.settings.Prefs.getBool("ad_strict", false);
        } catch (Throwable ignored) {}

        File f = new File(ctx.getFilesDir(), "rules/hosts.txt");
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f)), 32768)) {
                String line;
                while ((line = r.readLine()) != null) parseLine(line, b);
            } catch (Exception ignored) {}
        }
        Set<String> w = new HashSet<>();
        for (String s : Prefs.get("adblock_white", "").split("[,\\n]")) {
            if (!s.trim().isEmpty()) w.add(s.trim().toLowerCase(Locale.ROOT));
        }
        block = b;
        white = w;
        ready = true;
    }

    private void parseLine(String line, Set<String> into) {
        String s = line.trim();
        if (s.isEmpty() || s.startsWith("#") || s.startsWith("!")) return;
        int hash = s.indexOf('#');
        if (hash > 0) s = s.substring(0, hash).trim();
        String[] parts = s.split("\\s+");
        String d;
        if (parts.length >= 2 && (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1") || parts[0].equals("::1"))) {
            d = parts[1];
        } else {
            d = parts[0];
        }
        d = d.toLowerCase(Locale.ROOT);
        if (d.isEmpty() || d.indexOf('.') < 0) return;
        into.add(d);
    }

    public void setEnabled(boolean b) {
        enabled = b;
        Prefs.putBool("adblock", b);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isReady() {
        return ready;
    }

    public void reload() {
        ready = false;
        loadAll();
    }

    /** 最近的请求记录（最多 200 条），给「网络日志」用。 */
    private final java.util.List<String> recent = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public java.util.List<String> recentLog() {
        synchronized (recent) {
            return new java.util.ArrayList<>(recent);
        }
    }

    /** 当前页面拦掉的请求数（换页归零）。 */
    public volatile int pageBlocked = 0;

    public void resetPageCount() {
        pageBlocked = 0;
    }

    public volatile long totalBlocked = 0;
    public volatile long bytesSaved = 0;

    private void record(String host, boolean blocked) {
        if (blocked) {
            pageBlocked++;
            totalBlocked++;
            bytesSaved += 30 * 1024;      // 保守估计：每个被拦请求按 30KB 算
        }
        synchronized (recent) {
            recent.add((blocked ? "拦截  " : "放行  ") + host);
            if (recent.size() > 200) recent.remove(0);
        }
    }

    /** 自检用：忽略总开关，只看规则有没有命中。 */
    public boolean blockedByRules(String host) {
        if (TextUtils.isEmpty(host) || !ready) return false;
        return decide(host);
    }

    public boolean shouldBlock(String host) {
        if (!enabled || !ready || TextUtils.isEmpty(host)) return false;
        boolean blocked = decide(host);
        record(host, blocked);
        return blocked;
    }

    private boolean decide(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        Set<String> b = block;
        Set<String> w = white;
        if (w.contains(h)) return false;
        if (b.contains(h)) return true;
        int idx = h.indexOf('.');
        while (idx > 0 && idx < h.length() - 1) {
            String suffix = h.substring(idx + 1);
            if (w.contains(suffix)) return false;
            if (b.contains(suffix)) return true;
            idx = h.indexOf('.', idx + 1);
        }
        return false;
    }

    /** 用户自己加的拦截规则（一行一个域名）。 */
    public void addCustom(String lines) {
        if (lines == null) return;
        for (String one : lines.split("\\r?\\n|,")) {
            String h = one.trim().toLowerCase();
            if (h.isEmpty() || h.startsWith("#") || h.startsWith("!")) continue;
            if (h.startsWith("0.0.0.0 ")) h = h.substring(8).trim();
            if (h.contains(" ")) h = h.split("\\s+")[1];
            block.add(h);
        }
    }

    public int ruleCount() {
        return block.size();
    }
}
