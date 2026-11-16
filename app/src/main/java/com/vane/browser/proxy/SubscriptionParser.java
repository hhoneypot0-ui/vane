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

        // subconverter 的 Clash 配置（target=clash）走 YAML 分支
        if (looksLikeClash(text)) {
            out.addAll(parseClash(text));
            // 有些订阅会混着放分享链接，再把它们也捡出来
            for (String line : text.split("\r?\n")) {
                String t = line.trim();
                if (!t.contains("://")) continue;
                if (!t.matches("^(ss|ssr|vmess|vless|trojan|hysteria2?|hy2|tuic|socks5?|http|anytls|shadowtls|snell)://.*")) continue;
                Node n = parseOne(t);
                if (n != null) out.add(n);
            }
            return out;
        }
        // v2ray / sing-box 的 JSON（outbounds 数组）
        if (text.startsWith("{") && text.contains("outbounds")) {
            List<Node> jn = parseJsonOutbounds(text);
            if (!jn.isEmpty()) return jn;
        }
        // Surge / Quantumult 行格式：名字 = ss, host, port, ...
        if (!text.contains("://") && text.contains("=") && text.contains(",")) {
            List<Node> sn = parseSurge(text);
            if (!sn.isEmpty()) return sn;
        }
        if (!text.contains("://")) {
            String dec = b64(text);
            if (dec != null) text = dec;
            // base64 解开后可能还是 JSON / Surge
            if (text.startsWith("{") && text.contains("outbounds")) {
                List<Node> jn = parseJsonOutbounds(text);
                if (!jn.isEmpty()) return jn;
            }
            if (!text.contains("://") && text.contains("=") && text.contains(",")) {
                List<Node> sn = parseSurge(text);
                if (!sn.isEmpty()) return sn;
            }
        }
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("//")) continue;
            Node n = parseOne(l);
            if (n != null && !n.server.isEmpty() && n.port > 0) out.add(n);
        }
        return out;
    }

    /** v2rayN / sing-box 导出的 JSON：读 outbounds 里的节点。 */
    public static List<Node> parseJsonOutbounds(String json) {
        List<Node> out = new ArrayList<>();
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray arr = root.optJSONArray("outbounds");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type", o.optString("protocol", ""));
                if (type.isEmpty() || "direct".equals(type) || "block".equals(type)
                        || "dns".equals(type) || "selector".equals(type) || "urltest".equals(type)
                        || "freedom".equals(type) || "blackhole".equals(type)) continue;
                Node n = new Node();
                n.name = o.optString("tag", o.optString("remarks", type + " " + i));
                n.type = type;
                n.server = o.optString("server", o.optString("address", ""));
                n.port = o.optInt("server_port", o.optInt("port", 0));
                org.json.JSONObject st = o.optJSONObject("settings");
                if (st != null) {   // v2ray 老格式
                    org.json.JSONObject vnext = st.optJSONObject("vnext");
                    if (vnext != null) {
                        n.server = vnext.optString("address", n.server);
                        n.port = vnext.optInt("port", n.port);
                        org.json.JSONArray users = vnext.optJSONArray("users");
                        if (users != null && users.length() > 0) {
                            org.json.JSONObject u = users.optJSONObject(0);
                            n.uuid = u.optString("id", "");
                            n.alterId = u.optInt("alterId", 0);
                        }
                    }
                    org.json.JSONObject servers = st.optJSONObject("servers");
                    if (servers != null) {
                        n.server = servers.optString("address", n.server);
                        n.port = servers.optInt("port", n.port);
                        n.password = servers.optString("password", "");
                        n.method = servers.optString("method", "");
                    }
                }
                if (n.uuid.isEmpty()) n.uuid = o.optString("uuid", "");
                if (n.password.isEmpty()) n.password = o.optString("password", "");
                if (n.method.isEmpty()) n.method = o.optString("method", o.optString("security", ""));
                org.json.JSONObject tls = o.optJSONObject("tls");
                n.tls = tls != null && tls.optBoolean("enabled", false);
                if (tls != null) n.sni = tls.optString("server_name", n.sni);
                if (n.sni.isEmpty()) n.sni = o.optString("sni", "");
                if (o.has("transport")) {
                    org.json.JSONObject tr = o.optJSONObject("transport");
                    if (tr != null) {
                        String tt = tr.optString("type", "");
                        if ("ws".equals(tt)) { n.network = "ws"; n.path = tr.optString("path", "/"); }
                        else if ("grpc".equals(tt)) { n.network = "grpc"; n.path = tr.optString("service_name", ""); }
                    }
                }
                if (o.has("ws-opts") || o.has("ws_opts")) {
                    org.json.JSONObject ws = o.optJSONObject("ws-opts");
                    if (ws == null) ws = o.optJSONObject("ws_opts");
                    if (ws != null) { n.network = "ws"; n.path = ws.optString("path", "/"); }
                }
                if (n.server.isEmpty() || n.port <= 0) continue;
                out.add(n);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Surge / Quantumult X 行格式。 */
    public static List<Node> parseSurge(String text) {
        List<Node> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("//")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String name = line.substring(0, eq).trim();
            String[] f = line.substring(eq + 1).split(",");
            if (f.length < 3) continue;
            String type = f[0].trim().toLowerCase();
            if (!(type.equals("ss") || type.equals("vmess") || type.equals("trojan")
                    || type.equals("vless") || type.equals("hysteria2") || type.equals("tuic"))) continue;
            Node n = new Node();
            n.name = name;
            n.type = type;
            n.server = f[1].trim();
            try { n.port = Integer.parseInt(f[2].trim()); } catch (Exception e) { continue; }
            Map<String, String> kv = new java.util.HashMap<>();
            for (int i = 3; i < f.length; i++) {
                int e2 = f[i].indexOf('=');
                if (e2 > 0) kv.put(f[i].substring(0, e2).trim().toLowerCase(), f[i].substring(e2 + 1).trim());
            }
            n.method = kv.getOrDefault("encrypt-method", kv.getOrDefault("method", ""));
            n.password = kv.getOrDefault("password", "");
            n.uuid = kv.getOrDefault("password", "");
            n.sni = kv.getOrDefault("sni", kv.getOrDefault("obfs-host", ""));
            n.tls = kv.containsKey("tls") || kv.containsKey("sni");
            if (kv.containsKey("obfs") && "ws".equals(kv.get("obfs"))) {
                n.network = "ws";
                n.path = kv.getOrDefault("obfs-uri", kv.getOrDefault("path", "/"));
            }
            if (!n.server.isEmpty() && n.port > 0) out.add(n);
        }
        return out;
    }

    private static boolean looksLikeClash(String t) {
        if (!t.contains("proxies:")) return false;
        // 排除纯 YAML 规则文件：必须有 type: 且至少有一条 - { 或 - name:
        return t.contains("type:") && (t.contains("- {") || t.contains("- name:"));
    }

    /** 解析 Clash / subconverter 的 proxies 段。 */
    public static List<Node> parseClash(String text) {
        List<Node> out = new ArrayList<>();
        boolean inProxies = false;
        Map<String, Object> current = null;

        for (String raw : text.split("\r?\n")) {
            String line = raw;
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;

            if (!inProxies) {
                if (line.trim().equals("proxies:")) inProxies = true;
                continue;
            }
            // proxies 段结束：顶格的非列表行
            if (!line.startsWith(" ") && !line.startsWith("-")) {
                if (current != null) {
                    Node n = clashToNode(current);
                    if (n != null) out.add(n);
                    current = null;
                }
                inProxies = false;
                continue;
            }

            String t = line.trim();
            if (t.startsWith("-")) {
                if (current != null) {
                    Node n = clashToNode(current);
                    if (n != null) out.add(n);
                }
                String body = t.substring(1).trim();
                if (body.startsWith("{")) {
                    current = parseFlow(body);
                    Node n = clashToNode(current);
                    if (n != null) out.add(n);
                    current = null;
                } else {
                    current = new LinkedHashMap<>();
                    putKeyValue(current, body);
                }
            } else if (current != null) {
                putKeyValue(current, t);
            }
        }
        if (current != null) {
            Node n = clashToNode(current);
            if (n != null) out.add(n);
        }
        return out;
    }

    /** 一行 key: value（value 可能是 {...} 或 [..] 或标量）。 */
    private static void putKeyValue(Map<String, Object> m, String line) {
        int c = line.indexOf(':');
        if (c <= 0) return;
        String k = line.substring(0, c).trim();
        String v = line.substring(c + 1).trim();
        if (v.startsWith("{")) m.put(k, parseFlow(v));
        else if (v.startsWith("[")) m.put(k, parseList(v));
        else m.put(k, unquote(v));
    }

    /** 解析 {k: v, k2: {..}, k3: [..]}。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseFlow(String s) {
        Map<String, Object> m = new LinkedHashMap<>();
        int start = s.indexOf('{');
        if (start < 0) return m;
        int depth = 0, i = start;
        StringBuilder token = new StringBuilder();
        String key = null;
        boolean inQuote = false;
        char quote = 0;

        while (i < s.length()) {
            char ch = s.charAt(i);
            if (inQuote) {
                if (ch == quote) inQuote = false;
                else token.append(ch);
                i++;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                inQuote = true;
                quote = ch;
                i++;
                continue;
            }
            if (ch == '{' || ch == '[') {
                depth++;
                if (depth > 1) token.append(ch);
                i++;
                continue;
            }
            if (ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) {
                    if (key != null) m.put(key, unquote(token.toString().trim()));
                    break;
                }
                token.append(ch);
                i++;
                continue;
            }
            if (ch == ':' && depth == 1 && key == null) {
                key = unquote(token.toString().trim());
                token.setLength(0);
                i++;
                continue;
            }
            if (ch == ',' && depth == 1) {
                if (key != null) {
                    String v = token.toString().trim();
                    m.put(key, v.startsWith("{") ? parseFlow(v) : (v.startsWith("[") ? parseList(v) : unquote(v)));
                    key = null;
                }
                token.setLength(0);
                i++;
                continue;
            }
            token.append(ch);
            i++;
        }
        return m;
    }

    private static List<Object> parseList(String s) {
        List<Object> l = new ArrayList<>();
        int start = s.indexOf('['), end = s.lastIndexOf(']');
        if (start < 0 || end <= start) return l;
        for (String part : s.substring(start + 1, end).split(",")) {
            String v = unquote(part.trim());
            if (!v.isEmpty()) l.add(v);
        }
        return l;
    }

    private static String unquote(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** Clash 的 proxy 字段 → 我们的 Node。 */
    @SuppressWarnings("unchecked")
    private static Node clashToNode(Map<String, Object> m) {
        String type = str(m, "type").toLowerCase();
        String name = str(m, "name");
        if (type.isEmpty()) return null;
        if (name.startsWith("Traffic:") || name.startsWith("Expire:") || name.contains("剩余流量")
                || name.contains("到期时间") || name.contains("官网") || name.contains("套餐")) {
            return null;   // 机场塞在节点列表里的广告条目
        }
        Node n = new Node();
        n.name = name.isEmpty() ? str(m, "server") : name;
        n.server = str(m, "server");
        n.port = intVal(m, "port");
        n.raw = "";
        switch (type) {
            case "ss":
                n.type = "ss";
                n.method = str(m, "cipher");
                n.password = str(m, "password");
                break;
            case "vmess":
                n.type = "vmess";
                n.uuid = str(m, "uuid");
                n.alterId = intVal(m, "alterId");
                n.network = strOr(m, "network", "tcp");
                n.tls = boolVal(m, "tls");
                n.sni = firstNonEmpty(str(m, "servername"), str(m, "sni"));
                transport(n, m);
                break;
            case "vless":
                n.type = "vless";
                n.uuid = str(m, "uuid");
                n.network = strOr(m, "network", "tcp");
                n.tls = boolVal(m, "tls");
                n.flow = str(m, "flow");
                n.sni = firstNonEmpty(str(m, "servername"), str(m, "sni"));
                n.fingerprint = str(m, "client-fingerprint");
                Object reality = m.get("reality-opts");
                if (reality instanceof Map) {
                    Map<String, Object> r = (Map<String, Object>) reality;
                    n.realityPublicKey = str(r, "public-key");
                    n.realityShortId = str(r, "short-id");
                    if (!n.realityPublicKey.isEmpty()) {
                        n.security = "reality";
                        n.tls = true;
                    }
                }
                transport(n, m);
                break;
            case "trojan":
                n.type = "trojan";
                n.password = str(m, "password");
                n.tls = true;
                n.sni = firstNonEmpty(str(m, "sni"), str(m, "servername"));
                n.alpn = joinList(m.get("alpn"));
                break;
            case "hysteria2":
            case "hy2":
                n.type = "hysteria2";
                n.password = str(m, "password");
                n.tls = true;
                n.sni = firstNonEmpty(str(m, "sni"), str(m, "servername"));
                n.obfs = str(m, "obfs");
                n.obfsPassword = str(m, "obfs-password");
                n.upMbps = intVal(m, "up");
                n.downMbps = intVal(m, "down");
                break;
            case "tuic":
                n.type = "tuic";
                n.uuid = str(m, "uuid");
                n.password = str(m, "password");
                n.tls = true;
                n.sni = firstNonEmpty(str(m, "sni"), str(m, "servername"));
                n.congestion = str(m, "congestion-controller");
                n.udpRelayMode = str(m, "udp-relay-mode");
                n.alpn = joinList(m.get("alpn"));
                break;
            case "anytls":
                n.type = "anytls";
                n.password = str(m, "password");
                n.tls = true;
                n.sni = firstNonEmpty(str(m, "sni"), str(m, "servername"));
                n.fingerprint = str(m, "client-fingerprint");
                n.alpn = joinList(m.get("alpn"));
                break;
            case "shadowtls":
                n.type = "shadowtls";
                n.password = str(m, "password");
                n.tls = true;
                n.version = intVal(m, "version");
                if (n.version <= 0) n.version = 3;
                n.sni = firstNonEmpty(str(m, "sni"), str(m, "servername"));
                break;
            case "snell":
                n.type = "snell";
                n.psk = firstNonEmpty(str(m, "psk"), str(m, "password"));
                n.version = intVal(m, "version");
                if (n.version <= 0) n.version = 4;
                n.obfs = firstNonEmpty(str(m, "obfs"), str(m, "obfs-opts"));
                break;
            case "hysteria":
                n.type = "hysteria";
                n.password = firstNonEmpty(str(m, "auth-str"), firstNonEmpty(str(m, "auth_str"), str(m, "password")));
                n.tls = true;
                n.sni = firstNonEmpty(str(m, "sni"), str(m, "servername"));
                n.upMbps = intVal(m, "up");
                n.downMbps = intVal(m, "down");
                n.obfs = str(m, "obfs");
                break;
            case "wireguard":
                n.type = "wireguard";
                n.wgPrivate = str(m, "private-key");
                Object peers = m.get("peers");
                if (peers instanceof List && !((List<?>) peers).isEmpty()) {
                    Object p0 = ((List<?>) peers).get(0);
                    if (p0 instanceof Map) {
                        Map<String, Object> pm = (Map<String, Object>) p0;
                        n.wgPublic = str(pm, "public-key");
                        n.server = firstNonEmpty(n.server, str(pm, "server"));
                        if (n.port <= 0) n.port = intVal(pm, "port");
                    }
                }
                if (n.wgPublic.isEmpty()) n.wgPublic = str(m, "public-key");
                Object ip = m.get("ip");
                n.wgLocalIp = ip == null ? str(m, "address") : String.valueOf(ip).replace("[", "").replace("]", "").split(",")[0].trim();
                n.wgMtu = intVal(m, "mtu");
                if (n.wgMtu <= 0) n.wgMtu = 1420;
                break;
            case "http":
            case "socks5":
                n.type = type;
                n.uuid = str(m, "username");
                n.password = str(m, "password");
                n.tls = boolVal(m, "tls");
                break;
            default:
                return null;   // ssr / wireguard / snell 等先不支持
        }
        n.insecure = boolVal(m, "skip-cert-verify");
        if (n.server.isEmpty() || n.port <= 0) return null;
        return n;
    }

    @SuppressWarnings("unchecked")
    private static void transport(Node n, Map<String, Object> m) {
        Object ws = m.get("ws-opts");
        if (ws instanceof Map) {
            Map<String, Object> w = (Map<String, Object>) ws;
            n.network = "ws";
            n.path = str(w, "path");
            Object headers = w.get("headers");
            if (headers instanceof Map) n.host = str((Map<String, Object>) headers, "Host");
        }
        Object grpc = m.get("grpc-opts");
        if (grpc instanceof Map) {
            n.network = "grpc";
            n.path = str((Map<String, Object>) grpc, "grpc-service-name");
        }
    }

    private static String joinList(Object o) {
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object x : (List<Object>) o) {
                if (sb.length() > 0) sb.append(',');
                sb.append(String.valueOf(x));
            }
            return sb.toString();
        }
        return o == null ? "" : String.valueOf(o);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String strOr(Map<String, Object> m, String k, String def) {
        String v = str(m, k);
        return v.isEmpty() ? def : v;
    }

    private static int intVal(Map<String, Object> m, String k) {
        try {
            return Integer.parseInt(str(m, k).replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean boolVal(Map<String, Object> m, String k) {
        String v = str(m, k);
        return v.equalsIgnoreCase("true") || v.equals("1");
    }

    private static String firstNonEmpty(String a, String b) {
        return a == null || a.isEmpty() ? (b == null ? "" : b) : a;
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
                case "anytls": return parseUri(link, "anytls");
                case "shadowtls": return parseUri(link, "shadowtls");
                case "hysteria": return parseUri(link, "hysteria");
                case "snell": return parseUri(link, "snell");
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
            if (type.equals("trojan") || type.equals("hysteria2") || type.equals("anytls")
                    || type.equals("shadowtls") || type.equals("snell")) {
                n.password = urlDecode(userInfo);
            } else if (type.equals("tuic")) {
                // tuic://uuid:password@host:port
                int c = userInfo.indexOf(':');
                n.uuid = urlDecode(c > 0 ? userInfo.substring(0, c) : userInfo);
                n.password = c > 0 ? urlDecode(userInfo.substring(c + 1)) : "";
            } else {
                n.uuid = urlDecode(userInfo);
            }
            if (q.containsKey("auth") && n.password.isEmpty()) n.password = urlDecode(q.get("auth"));
            if (q.containsKey("auth_str") && n.password.isEmpty()) n.password = urlDecode(q.get("auth_str"));
        }
        if (n.password.isEmpty()) {
            for (String k : new String[]{"auth", "auth_str", "authStr", "password"}) {
                if (q.containsKey(k)) {
                    n.password = urlDecode(q.get(k));
                    break;
                }
            }
        }
        n.security = q.containsKey("security") ? q.get("security")
                : ((type.equals("trojan") || type.equals("hysteria2") || type.equals("tuic")
                    || type.equals("anytls") || type.equals("shadowtls") || type.equals("hysteria")) ? "tls" : "none");
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
        if (type.equals("snell")) {
            n.psk = n.password;
            n.version = intOr(pick(q, "version"), 4);
        }
        if (type.equals("shadowtls")) {
            n.version = intOr(pick(q, "version"), 3);
        }
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
