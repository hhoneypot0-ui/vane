package com.vane.browser.proxy;

import android.content.Context;

import com.vane.browser.settings.Prefs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 分流规则：判断一个请求该直连还是走代理。
 * 规则来源：assets/geosite_cn.txt（国内域名后缀）、assets/geoip_cn.txt（国内 IP 段）、
 * prefs 里的用户自定义直连/代理清单。
 */
public class RuleEngine {

    public static final String MODE_RULE = "rule";
    public static final String MODE_GLOBAL = "global";
    public static final String MODE_DIRECT = "direct";

    private final Set<String> cnSuffix = new HashSet<>();
    private final Set<String> userDirect = new HashSet<>();
    private final Set<String> userProxy = new HashSet<>();
    private final List<long[]> cnCidr = new ArrayList<>(); // {network, mask}
    private volatile String mode = MODE_RULE;

    public RuleEngine(Context ctx) {
        mode = Prefs.get("proxy_mode", MODE_RULE);
        loadAsset(ctx, "geosite_cn.txt", cnSuffix);
        loadAsset(ctx, "geoip_cn.txt", null);
        for (String s : Prefs.get("rules_direct", "").split("[,\\n]"))
            if (!s.trim().isEmpty()) userDirect.add(s.trim().toLowerCase(Locale.ROOT));
        for (String s : Prefs.get("rules_proxy", "").split("[,\\n]"))
            if (!s.trim().isEmpty()) userProxy.add(s.trim().toLowerCase(Locale.ROOT));
    }

    private void loadAsset(Context ctx, String name, Set<String> into) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open(name)))) {
            String line;
            while ((line = r.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                if (into != null) {
                    into.add(s.toLowerCase(Locale.ROOT));
                } else {
                    String[] p = s.split("/");
                    if (p.length == 2) {
                        long net = ip2long(p[0]);
                        int bits = Integer.parseInt(p[1]);
                        if (net >= 0) cnCidr.add(new long[]{net, bits});
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void setMode(String m) {
        mode = m;
        Prefs.put("proxy_mode", m);
    }

    public String mode() {
        return mode;
    }

    public void setUserRules(String direct, String proxy) {
        userDirect.clear();
        userProxy.clear();
        for (String s : direct.split("[,\\n]")) if (!s.trim().isEmpty()) userDirect.add(s.trim().toLowerCase(Locale.ROOT));
        for (String s : proxy.split("[,\\n]")) if (!s.trim().isEmpty()) userProxy.add(s.trim().toLowerCase(Locale.ROOT));
        Prefs.put("rules_direct", direct);
        Prefs.put("rules_proxy", proxy);
    }

    /** true = 走代理，false = 直连。 */
    public boolean proxyFor(String host) {
        if (MODE_DIRECT.equals(mode)) return false;
        if (MODE_GLOBAL.equals(mode)) return true;
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (containsOrParent(userProxy, h)) return true;
        if (containsOrParent(userDirect, h)) return false;
        if (isIpv4(h)) return !isCnIp(h);
        if (h.endsWith(".cn")) return false;
        if (containsOrParent(cnSuffix, h)) return false;
        return true;
    }

    private boolean containsOrParent(Set<String> set, String h) {
        if (set.contains(h)) return true;
        int idx = h.indexOf('.');
        while (idx > 0 && idx < h.length() - 1) {
            if (set.contains(h.substring(idx + 1))) return true;
            idx = h.indexOf('.', idx + 1);
        }
        return false;
    }

    public static boolean isIpv4(String h) {
        return h.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }

    public boolean isCnIp(String ip) {
        long v = ip2long(ip);
        if (v < 0) return false;
        for (long[] c : cnCidr) {
            int bits = (int) c[1];
            long mask = bits == 0 ? 0 : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
            if ((v & mask) == (c[0] & mask)) return true;
        }
        return false;
    }

    public static long ip2long(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) return -1;
        try {
            long v = 0;
            for (String s : p) {
                int o = Integer.parseInt(s);
                if (o < 0 || o > 255) return -1;
                v = (v << 8) | o;
            }
            return v & 0xFFFFFFFFL;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 供内核配置生成用：国内域名后缀（不含注释）。 */
    public java.util.List<String> cnSuffixes() {
        return new ArrayList<>(cnSuffix);
    }

    /** 供内核配置生成用：国内 IP 段，CIDR 文本。 */
    public java.util.List<String> cnCidrs() {
        java.util.List<String> out = new ArrayList<>();
        for (long[] c : cnCidr) {
            int bits = (int) c[1];
            long net = c[0];
            out.add(((net >> 24) & 0xFF) + "." + ((net >> 16) & 0xFF) + "." + ((net >> 8) & 0xFF) + "." + (net & 0xFF) + "/" + bits);
        }
        return out;
    }

    public java.util.List<String> userProxyRules() {
        return new ArrayList<>(userProxy);
    }

    public int cnDomainCount() {
        return cnSuffix.size();
    }

    public int cnIpCount() {
        return cnCidr.size();
    }
}
