package com.vane.browser.proxy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 把节点 + 分流规则翻译成 sing-box 的 config.json。
 *
 * 目标版本：sing-box 1.14.x（用的是新 DNS schema：dns.servers[].type/server，
 * 以及 route.default_domain_resolver）。旧写法在 1.12 之后会被判为无效配置。
 */
public final class CoreConfig {

    private CoreConfig() {}

    public static String build(List<Node> nodes, String selectedName, RuleEngine rules, int listenPort) {
        JSONObject root = new JSONObject();
        try {
            // ---- log ----
            JSONObject log = new JSONObject();
            log.put("level", "warn");
            log.put("timestamp", true);
            root.put("log", log);

            // ---- 出站：所有节点 ----
            JSONArray outs = new JSONArray();
            JSONArray endpoints = new JSONArray();
            JSONArray nodeTags = new JSONArray();
            for (Node n : nodes) {
                if ("wireguard".equals(n.type)) {
                    JSONObject ep = wireguardEndpoint(n);
                    if (ep != null) {
                        endpoints.put(ep);
                        nodeTags.put(n.name);
                    }
                    continue;
                }
                JSONObject o = outbound(n);
                if (o != null) {
                    outs.put(o);
                    nodeTags.put(n.name);
                }
            }
            if (endpoints.length() > 0) root.put("endpoints", endpoints);
            outs.put(new JSONObject().put("type", "direct").put("tag", "direct"));
            JSONObject sel = new JSONObject();
            sel.put("type", "selector");
            sel.put("tag", "proxy");
            sel.put("outbounds", nodeTags.length() > 0 ? nodeTags : new JSONArray().put("direct"));
            if (selectedName != null && nodeTags.length() > 0) sel.put("default", selectedName);
            outs.put(sel);
            root.put("outbounds", outs);

            // ---- DNS：本地解析走直连，其余走代理；节点域名用 local 解析 ----
            JSONObject dns = new JSONObject();
            JSONArray servers = new JSONArray();
            // 端口必须用 server_port；不能写 "host:port"
            // local 不能设 detour=direct（sing-box 会报 "detour to an empty direct outbound makes no sense"），
            // 改由 route 里的 protocol=dns 规则把 DNS 流量打到直连。
            servers.put(new JSONObject().put("type", "udp").put("tag", "local")
                    .put("server", "223.5.5.5").put("server_port", 53));
            servers.put(new JSONObject().put("type", "udp").put("tag", "remote")
                    .put("server", "1.1.1.1").put("server_port", 53).put("detour", "proxy"));
            dns.put("servers", servers);

            JSONArray dnsRules = new JSONArray();
            JSONArray cnDomains = new JSONArray();
            cnDomains.put(".cn");
            for (String s : rules.cnSuffixes()) cnDomains.put(s);
            // 只按域名分流 DNS；ip_cidr 属于 1.14 已弃用的 address filter 写法
            dnsRules.put(new JSONObject().put("domain_suffix", cnDomains).put("server", "local"));
            dns.put("rules", dnsRules);
            dns.put("final", "remote");
            dns.put("strategy", "ipv4_only");
            root.put("dns", dns);

            // ---- 入站：本地 mixed 口（WebView 的代理指向这里）----
            JSONObject in = new JSONObject();
            in.put("type", "mixed");
            in.put("tag", "mixed-in");
            in.put("listen", "127.0.0.1");
            in.put("listen_port", listenPort);
            root.put("inbounds", new JSONArray().put(in));

            // ---- 路由 ----
            JSONObject route = new JSONObject();
            JSONArray rr = new JSONArray();
            // DNS 流量一律直连，避免「解析节点的域名要用节点」的死循环
            rr.put(new JSONObject().put("protocol", "dns").put("outbound", "direct"));
            rr.put(new JSONObject().put("ip_is_private", true).put("outbound", "direct"));
            if (rules != null && RuleEngine.MODE_GLOBAL.equals(rules.mode())) {
                // 全局：先清掉所有直连规则，只剩私有地址直连
            } else if (rules != null && RuleEngine.MODE_DIRECT.equals(rules.mode())) {
                route.put("final", "direct");
            }
            if (rules == null || !RuleEngine.MODE_GLOBAL.equals(rules.mode())) {
                JSONArray uProxy = new JSONArray();
                for (String s : rules == null ? java.util.Collections.<String>emptyList() : rules.userProxyRules()) uProxy.put(s);
                if (uProxy.length() > 0) {
                    rr.put(new JSONObject().put("domain_suffix", uProxy).put("outbound", "proxy"));
                }
                JSONArray dirDomains = new JSONArray();
                if (rules != null) for (String s : rules.cnSuffixes()) dirDomains.put(s);
                if (dirDomains.length() > 0) {
                    rr.put(new JSONObject().put("domain_suffix", dirDomains).put("outbound", "direct"));
                }
                JSONArray cnIp = cnCidrJson(rules);
                if (cnIp.length() > 0) {
                    rr.put(new JSONObject().put("ip_cidr", cnIp).put("outbound", "direct"));
                }
            }
            route.put("rules", rr);
            if (!route.has("final")) route.put("final", "proxy");
            route.put("default_domain_resolver", new JSONObject().put("server", "local").put("strategy", "ipv4_only"));
            route.put("auto_detect_interface", false);
            root.put("route", route);
        } catch (Exception ignored) {}
        return root.toString();
    }

    private static JSONArray cnCidrJson(RuleEngine rules) {
        JSONArray a = new JSONArray();
        if (rules == null) return a;
        for (String s : rules.cnCidrs()) a.put(s);
        return a;
    }

    /** wireguard 在 1.14 必须写成 endpoint（写成 outbound 会报 unknown field server）。 */
    private static JSONObject wireguardEndpoint(Node n) {
        try {
            JSONObject ep = new JSONObject();
            ep.put("type", "wireguard");
            ep.put("tag", n.name);
            String ip = n.wgLocalIp == null || n.wgLocalIp.isEmpty() ? "10.0.0.2/32" : n.wgLocalIp;
            if (!ip.contains("/")) ip = ip + "/32";
            ep.put("address", new JSONArray().put(ip));
            ep.put("private_key", n.wgPrivate);
            if (n.wgMtu > 0) ep.put("mtu", n.wgMtu);
            JSONObject peer = new JSONObject();
            peer.put("address", n.server);
            peer.put("port", n.port);
            peer.put("public_key", n.wgPublic);
            peer.put("allowed_ips", new JSONArray().put("0.0.0.0/0").put("::/0"));
            ep.put("peers", new JSONArray().put(peer));
            return ep;
        } catch (Exception e) {
            return null;
        }
    }

    /** 全局拨号优化开关（可在设置里关掉）。 */
    static boolean fastOpen = true;
    static boolean multiplex = false;

    private static void tune(JSONObject o) {
        // 保持 1.59.2 的原始行为：不加 tcp_fast_open / multiplex
    }

    private static JSONObject outbound(Node n) {
        try {
            JSONObject o = new JSONObject();
            o.put("tag", n.name == null || n.name.isEmpty() ? (n.type + "-" + n.port) : n.name);
            switch (n.type) {
                case "ss":
                    o.put("type", "shadowsocks");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("method", n.method);
                    o.put("password", n.password);
                    tune(o);
                    return o;
                case "vmess":
                    o.put("type", "vmess");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("uuid", n.uuid);
                    o.put("security", "auto");
                    o.put("alter_id", n.alterId);
                    if (hasTransport(n)) o.put("transport", transport(n));
                    if (n.tls) o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "vless":
                    o.put("type", "vless");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("uuid", n.uuid);
                    if (n.flow != null && !n.flow.isEmpty()) o.put("flow", n.flow);
                    if (hasTransport(n)) o.put("transport", transport(n));
                    if (n.tls) o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "trojan":
                    o.put("type", "trojan");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("password", n.password);
                    o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "hysteria2":
                    o.put("type", "hysteria2");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("password", n.password);
                    if (n.upMbps > 0) o.put("up_mbps", n.upMbps);
                    if (n.downMbps > 0) o.put("down_mbps", n.downMbps);
                    if (n.obfs != null && !n.obfs.isEmpty()) {
                        o.put("obfs", new JSONObject().put("type", n.obfs).put("password",
                                n.obfsPassword == null ? "" : n.obfsPassword));
                    }
                    if (n.serverPorts != null && !n.serverPorts.isEmpty()) {
                        JSONArray ports = new JSONArray();
                        for (String pt : n.serverPorts.split(",")) {
                            if (!pt.trim().isEmpty()) ports.put(pt.trim());
                        }
                        if (ports.length() > 0) o.put("server_ports", ports);
                    }
                    o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "tuic":
                    o.put("type", "tuic");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("uuid", n.uuid);
                    o.put("password", n.password);
                    if (n.congestion != null && !n.congestion.isEmpty()) o.put("congestion_control", n.congestion);
                    if (n.udpRelayMode != null && !n.udpRelayMode.isEmpty()) o.put("udp_relay_mode", n.udpRelayMode);
                    o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "anytls":
                    o.put("type", "anytls");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("password", n.password);
                    o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "shadowtls":
                    o.put("type", "shadowtls");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("version", n.version <= 0 ? 3 : n.version);
                    o.put("password", n.password);
                    o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "hysteria":
                    o.put("type", "hysteria");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    if (n.upMbps > 0) o.put("up_mbps", n.upMbps);
                    if (n.downMbps > 0) o.put("down_mbps", n.downMbps);
                    if (n.password != null && !n.password.isEmpty()) o.put("auth_str", n.password);
                    if (n.obfs != null && !n.obfs.isEmpty()) o.put("obfs", n.obfs);
                    o.put("tls", tls(n));
                    tune(o);
                    return o;
                case "snell":
                    o.put("type", "snell");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    o.put("psk", n.psk == null || n.psk.isEmpty() ? n.password : n.psk);
                    o.put("version", n.version <= 0 ? 4 : n.version);
                    tune(o);
                    return o;
                case "socks":
                    o.put("type", "socks");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    if (n.uuid != null && !n.uuid.isEmpty()) {
                        o.put("username", n.uuid);
                        o.put("password", n.password);
                    }
                    tune(o);
                    return o;
                case "http":
                    o.put("type", "http");
                    o.put("server", n.server);
                    o.put("server_port", n.port);
                    if (n.uuid != null && !n.uuid.isEmpty()) {
                        o.put("username", n.uuid);
                        o.put("password", n.password);
                    }
                    tune(o);
                    return o;
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** tcp 不需要 transport 字段；写了反而会被服务端判为协议不匹配。 */
    private static boolean hasTransport(Node n) {
        String net = n.network == null ? "" : n.network;
        return net.equals("ws") || net.equals("grpc") || net.equals("h2") || net.equals("http");
    }

    private static JSONObject transport(Node n) throws Exception {
        if ("ws".equals(n.network)) {
            JSONObject t = new JSONObject();
            t.put("type", "ws");
            t.put("path", n.path == null || n.path.isEmpty() ? "/" : n.path);
            if (n.host != null && !n.host.isEmpty()) {
                t.put("headers", new JSONObject().put("Host", n.host));
            }
            return t;
        }
        if ("grpc".equals(n.network)) {
            return new JSONObject().put("type", "grpc").put("service_name",
                    n.path == null ? "" : n.path.replaceFirst("^/", ""));
        }
        if ("h2".equals(n.network) || "http".equals(n.network)) {
            JSONObject t = new JSONObject().put("type", "http");
            if (n.host != null && !n.host.isEmpty()) {
                t.put("host", new JSONArray().put(n.host));
            }
            if (n.path != null && !n.path.isEmpty()) t.put("path", n.path);
            return t;
        }
        return new JSONObject().put("type", "http");
    }

    private static JSONObject tls(Node n) throws Exception {
        JSONObject t = new JSONObject();
        t.put("enabled", true);
        if (n.sni != null && !n.sni.isEmpty()) t.put("server_name", n.sni);
        t.put("insecure", n.insecure);
        if (n.alpn != null && !n.alpn.isEmpty()) {
            JSONArray a = new JSONArray();
            for (String x : n.alpn.split("[,\\s]+")) {
                if (!x.trim().isEmpty()) a.put(x.trim());
            }
            if (a.length() > 0) t.put("alpn", a);
        }
        boolean reality = n.security != null && n.security.startsWith("reality");
        if (n.fingerprint != null && !n.fingerprint.isEmpty()) {
            t.put("utls", new JSONObject().put("enabled", true).put("fingerprint", n.fingerprint));
        } else if (reality) {
            t.put("utls", new JSONObject().put("enabled", true).put("fingerprint", "chrome"));
        }
        if (reality) {
            JSONObject r = new JSONObject().put("enabled", true);
            if (n.realityPublicKey != null && !n.realityPublicKey.isEmpty()) r.put("public_key", n.realityPublicKey);
            if (n.realityShortId != null && !n.realityShortId.isEmpty()) r.put("short_id", n.realityShortId);
            t.put("reality", r);
        }
        return t;
    }
}
