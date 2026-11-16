package com.vane.browser.proxy;

import android.util.Base64;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅解析：机场订阅一般是一整段 base64，解出来是若干行分享链接。
 * 支持 ss / vmess / vless / trojan / hysteria2 / socks / http。
 */
public final class SubscriptionParser {

    private SubscriptionParser() {}

    public static List<Node> parse(String input) {
        List<Node> out = new ArrayList<>();
        if (input == null) return out;
        String text = input.trim();
        if (!text.contains("://")) {
            String dec = b64(text);
            if (dec != null) text = dec;
        }
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("//")) continue;
            Node n = parseOne(l);
            if (n != null && !n.server.isEmpty() && n.port > 0) out.add(n);
        }
        return out;
    }

    public static Node parseOne(String link) {
        try {
            String scheme = link.substring(0, link.indexOf("://")).toLowerCase();
            switch (scheme) {
                case "ss": return parseSs(link);
                case "vmess": return parseVmess(link);
                case "vless": return parseVless(link);
                case "trojan": return parseTrojan(link);
                case "hysteria2":
                case "hy2": return parseHy2(link);
                case "tuic": return parseTuic(link);
                case "socks":
                case "socks5": return parseSocks(link, "socks");
                case "http":
                case "https": return parseSocks(link, "http");
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Node parseSs(String link) throws Exception {
        String body = link.substring(5);
        String tag = "";
        int h = body.indexOf('#');
        if (h > 0) {
            tag = urlDecode(body.substring(h + 1));
            body = body.substring(0, h);
        }
        body = body.split("\\?")[0];
        String method, pass, hostport;
        String fMethod = "", fPass = "";
        if (body.contains("@")) {
            String cred = body.substring(0, body.indexOf('@'));
            hostport = body.substring(body.indexOf('@') + 1);
            String dec = b64(cred);
            if (dec == null) dec = urlDecode(cred);
            method = dec.substring(0, dec.indexOf(':'));
            pass = dec.substring(dec.indexOf(':') + 1);
        } else {
            String dec = b64(body);
            if (dec == null) return null;
            method = dec.substring(0, dec.indexOf(':'));
            String rest = dec.substring(dec.indexOf(':') + 1);
            pass = rest.substring(0, rest.lastIndexOf('@'));
            hostport = rest.substring(rest.lastIndexOf('@') + 1);
        }
        fMethod = method;
        fPass = pass;
        final String fm = fMethod, fp = fPass;
        return make("ss", tag, hostport, p -> {
            p.method = fm;
            p.password = fp;
        });
    }

    private static Node parseVmess(String link) {
        try {
            String dec = b64(link.substring(8));
            if (dec == null) return null;
            org.json.JSONObject o = new org.json.JSONObject(dec);
            Node n = new Node();
            n.type = "vmess";
            n.name = o.optString("ps", o.optString("add", "vmess"));
            n.server = o.optString("add", "");
            n.port = o.optInt("port", 0);
            n.uuid = o.optString("id", "");
            n.alterId = o.optInt("aid", 0);
            n.network = o.optString("net", "tcp");
            n.path = o.optString("path", "");
            n.host = o.optString("host", "");
            n.sni = o.optString("sni", o.optString("host", ""));
            n.tls = "tls".equalsIgnoreCase(o.optString("tls", ""));
            n.security = n.tls ? "tls" : "none";
            n.alpn = o.optString("alpn", "");
            n.fingerprint = o.optString("fp", "");
            if (o.optInt("insecure", 0) == 1) n.insecure = true;
            n.raw = link;
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private static Node parseVless(String link) throws Exception {
        return parseUri(link, "vless");
    }

    private static Node parseTrojan(String link) throws Exception {
        return parseUri(link, "trojan");
    }

    private static Node parseHy2(String link) throws Exception {
        return parseUri(link, "hysteria2");
    }

    private static Node parseTuic(String link) throws Exception {
        return parseUri(link, "tuic");
    }

    /** 通用 URL 形式：scheme://认证@host:port?params#name */
    private static Node parseUri(String link, String type) throws Exception {
        URI u = URI.create(link.replace(" ", "%20"));
        String userInfo = u.getUserInfo();
        String query = u.getRawQuery();
        Map<String, String> q = query == null ? new LinkedHashMap<>() : queryMap(query);
        String frag = u.getFragment();
        Node n = new Node();
        n.type = type;
        n.server = u.getHost() == null ? "" : u.getHost();
        n.port = u.getPort() > 0 ? u.getPort() : 443;
        n.name = frag == null ? n.server : urlDecode(frag);
        n.raw = link;
        if (userInfo != null) {
            if (type.equals("trojan") || type.equals("hysteria2")) {
                n.password = urlDecode(userInfo);
            } else if (type.equals("tuic")) {
                // tuic://uuid:password@host:port
                int c = userInfo.indexOf(':');
                n.uuid = urlDecode(c > 0 ? userInfo.substring(0, c) : userInfo);
                n.password = c > 0 ? urlDecode(userInfo.substring(c + 1)) : "";
            } else {
                n.uuid = urlDecode(userInfo);
            }
            if (q.containsKey("auth") && n.password.isEmpty()) n.password = q.get("auth");
        }
        n.security = q.containsKey("security") ? q.get("security")
                : (type.equals("trojan") || type.equals("hysteria2") || type.equals("tuic") ? "tls" : "none");
        n.tls = !"none".equals(n.security) && !n.security.isEmpty();
        n.sni = pick(q, "sni", "peer", "servername");
        n.network = q.containsKey("type") ? q.get("type") : "tcp";
        n.path = q.containsKey("path") ? urlDecode(q.get("path")) : "";
        n.host = q.containsKey("host") ? q.get("host") : "";
        if (q.containsKey("serviceName") && n.path.isEmpty()) n.path = q.get("serviceName");
        if (n.sni.isEmpty()) n.sni = n.host;

        // ---- 进阶参数 ----
        n.alpn = pick(q, "alpn");
        n.fingerprint = pick(q, "fp", "fingerprint");
        n.flow = pick(q, "flow");
        n.realityPublicKey = pick(q, "pbk", "publicKey", "public_key");
        n.realityShortId = pick(q, "sid", "shortId", "short_id");
        if (!n.realityPublicKey.isEmpty() && n.security.isEmpty()) {
            n.security = "reality";
            n.tls = true;
        }
        n.insecure = truthy(pick(q, "allowInsecure", "insecure", "skip-cert-verify", "allow_insecure"));
        n.serverPorts = pick(q, "mport", "ports", "server_ports");
        n.upMbps = intOr(pick(q, "up", "upmbps", "up_mbps"), 0);
        n.downMbps = intOr(pick(q, "down", "downmbps", "down_mbps"), 0);
        n.obfs = pick(q, "obfs");
        n.obfsPassword = pick(q, "obfs-password", "obfsParam", "obfs_password");
        n.congestion = pick(q, "congestion_control", "congestion");
        n.udpRelayMode = pick(q, "udp_relay_mode", "udp-relay-mode");
        // 兼容旧的 method 字段读法
        if (n.obfsPassword.isEmpty() && q.containsKey("obfs-password")) n.obfsPassword = q.get("obfs-password");
        return n;
    }

    private static Node parseSocks(String link, String type) throws Exception {
        URI u = URI.create(link.replace(" ", "%20"));
        Node n = new Node();
        n.type = type;
        n.server = u.getHost() == null ? "" : u.getHost();
        n.port = u.getPort() > 0 ? u.getPort() : (type.equals("http") ? 80 : 1080);
        String ui = u.getUserInfo();
        if (ui != null) {
            int c = ui.indexOf(':');
            n.uuid = urlDecode(c > 0 ? ui.substring(0, c) : ui);
            n.password = c > 0 ? urlDecode(ui.substring(c + 1)) : "";
        }
        String frag = u.getFragment();
        n.name = frag == null ? n.server : urlDecode(frag);
        n.raw = link;
        return n;
    }

    private interface Fill { void apply(Node n); }

    private static Node make(String type, String tag, String hostport, Fill f) {
        int c = hostport.lastIndexOf(':');
        if (c <= 0) return null;
        Node n = new Node();
        n.type = type;
        n.name = tag.isEmpty() ? hostport : tag;
        String host = hostport.substring(0, c);
        if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
        n.server = host;
        try {
            n.port = Integer.parseInt(hostport.substring(c + 1).replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return null;
        }
        n.tls = false;   // ss 没有 TLS（除非套 ws/plugin，暂不支持）
        f.apply(n);
        return n;
    }

    public static Map<String, String> queryMap(String query) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(kv.substring(0, i), kv.substring(i + 1));
        }
        return m;
    }

    private static String pick(Map<String, String> q, String... keys) {
        for (String k : keys) {
            String v = q.get(k);
            if (v != null && !v.isEmpty()) return urlDecode(v);
        }
        return "";
    }

    private static boolean truthy(String v) {
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    private static int intOr(String v, int def) {
        try {
            return v == null || v.isEmpty() ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** 宽松 base64 解码：兼容 url-safe、缺 padding、带换行。 */
    public static String b64(String s) {
        try {
            String t = s.replaceAll("\\s", "").replace('-', '+').replace('_', '/');
            while (t.length() % 4 != 0) t += "=";
            byte[] b = Base64.decode(t, Base64.DEFAULT);
            String out = new String(b, "UTF-8");
            // 解出乱码就认为失败
            if (out.indexOf('\uFFFD') >= 0) return null;
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
