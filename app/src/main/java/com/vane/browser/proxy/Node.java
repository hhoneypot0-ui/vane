package com.vane.browser.proxy;

import org.json.JSONObject;

/** 一个节点（对应订阅里的一行）。 */
public class Node {
    public String type = "socks";      // ss / vmess / vless / trojan / hysteria2 / socks / http
    public String name = "未命名";
    public String server = "";
    public int port = 0;
    public String password = "";       // ss 密码 / trojan 密码 / hysteria2 密码
    public String method = "";         // ss 加密方式
    public String uuid = "";           // vmess / vless
    public int alterId = 0;
    public String network = "tcp";     // tcp / ws / grpc / h2
    public String path = "";
    public String host = "";
    public String sni = "";
    public String security = "";       // tls / reality / none
    public boolean tls = false;
    public String raw = "";

    // ---- 进阶参数（机场常见写法）----
    public String alpn = "";           // "h2,http/1.1"
    public String fingerprint = "";    // utls: chrome / firefox / safari ...
    public String flow = "";           // vless: xtls-rprx-vision
    public String realityPublicKey = ""; // reality pbk
    public String realityShortId = "";   // reality sid
    public boolean insecure = false;     // 跳过证书校验
    public String serverPorts = "";      // hysteria2 端口跳跃: "443:8443"
    public int upMbps = 0;               // hysteria2 上行
    public int downMbps = 0;             // hysteria2 下行
    public String obfs = "";             // hysteria2 obfs 类型
    public String obfsPassword = "";     // hysteria2 obfs 密码
    public String congestion = "";       // tuic: bbr / cubic / new_reno
    public String udpRelayMode = "";     // tuic: native / quic

    // ---- 追加协议 ----
    public String psk = "";              // snell
    public int version = 4;              // snell / shadowtls 版本
    public String wgPrivate = "";        // wireguard 私钥
    public String wgPublic = "";         // wireguard 对端公钥
    public String wgLocalIp = "";        // wireguard 本地地址，如 10.0.0.2/32
    public int wgMtu = 1420;

    public String id() {
        return (type + ":" + server + ":" + port + ":" + name).hashCode() + "";
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(type.toUpperCase()).append(" · ").append(server).append(':').append(port);
        if (security != null && security.startsWith("reality")) sb.append(" · REALITY");
        else if (tls) sb.append(" · TLS");
        if (network != null && !network.isEmpty() && !"tcp".equals(network)) sb.append(" · ").append(network);
        if (flow != null && !flow.isEmpty()) sb.append(" · ").append(flow);
        if (obfs != null && !obfs.isEmpty()) sb.append(" · obfs:").append(obfs);
        return sb.toString();
    }

    public boolean isPlainProxy() {
        return type.equals("socks") || type.equals("http");
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type); o.put("name", name); o.put("server", server); o.put("port", port);
            o.put("password", password); o.put("method", method); o.put("uuid", uuid);
            o.put("alterId", alterId); o.put("network", network); o.put("path", path);
            o.put("host", host); o.put("sni", sni); o.put("security", security);
            o.put("tls", tls); o.put("raw", raw);
            o.put("alpn", alpn); o.put("fingerprint", fingerprint); o.put("flow", flow);
            o.put("pbk", realityPublicKey); o.put("sid", realityShortId);
            o.put("insecure", insecure); o.put("ports", serverPorts);
            o.put("up", upMbps); o.put("down", downMbps);
            o.put("obfs", obfs); o.put("obfsPwd", obfsPassword);
            o.put("congestion", congestion); o.put("udpRelay", udpRelayMode);
            o.put("psk", psk); o.put("version", version);
            o.put("wgPrivate", wgPrivate); o.put("wgPublic", wgPublic);
            o.put("wgLocalIp", wgLocalIp); o.put("wgMtu", wgMtu);
        } catch (Exception ignored) {}
        return o;
    }

    public static Node fromJson(JSONObject o) {
        Node n = new Node();
        n.type = o.optString("type", "socks");
        n.name = o.optString("name", "未命名");
        n.server = o.optString("server", "");
        n.port = o.optInt("port", 0);
        n.password = o.optString("password", "");
        n.method = o.optString("method", "");
        n.uuid = o.optString("uuid", "");
        n.alterId = o.optInt("alterId", 0);
        n.network = o.optString("network", "tcp");
        n.path = o.optString("path", "");
        n.host = o.optString("host", "");
        n.sni = o.optString("sni", "");
        n.security = o.optString("security", "");
        n.tls = o.optBoolean("tls", false);
        n.raw = o.optString("raw", "");
        n.alpn = o.optString("alpn", "");
        n.fingerprint = o.optString("fingerprint", "");
        n.flow = o.optString("flow", "");
        n.realityPublicKey = o.optString("pbk", "");
        n.realityShortId = o.optString("sid", "");
        n.insecure = o.optBoolean("insecure", false);
        n.serverPorts = o.optString("ports", "");
        n.upMbps = o.optInt("up", 0);
        n.downMbps = o.optInt("down", 0);
        n.obfs = o.optString("obfs", "");
        n.obfsPassword = o.optString("obfsPwd", "");
        n.congestion = o.optString("congestion", "");
        n.udpRelayMode = o.optString("udpRelay", "");
        n.psk = o.optString("psk", "");
        n.version = o.optInt("version", 4);
        n.wgPrivate = o.optString("wgPrivate", "");
        n.wgPublic = o.optString("wgPublic", "");
        n.wgLocalIp = o.optString("wgLocalIp", "");
        n.wgMtu = o.optInt("wgMtu", 1420);
        return n;
    }
}
