package com.vane.browser.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内置本地分流代理（纯 Java，无原生依赖）。
 *
 * 浏览器所有请求（含 HTTPS 的 CONNECT）先打到 127.0.0.1:port，
 * 这里按 RuleEngine 判断：国内域名/国内 IP 直接连目标，境外走上游节点。
 * 上游支持 HTTP 代理与 SOCKS5（含用户名密码认证）。
 */
public class SplitProxyServer {

    public interface Logger { void log(String msg); }

    private final RuleEngine rules;
    private volatile Node upstream;
    private volatile Logger logger;
    private ServerSocket server;
    private volatile boolean running;
    private final AtomicInteger connId = new AtomicInteger();
    private Thread acceptor;

    public SplitProxyServer(RuleEngine rules, Node upstream, Logger logger) {
        this.rules = rules;
        this.upstream = upstream;
        this.logger = logger;
    }

    public void setUpstream(Node n) {
        this.upstream = n;
    }

    public Node upstream() {
        return upstream;
    }

    public int start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        running = true;
        acceptor = new Thread(this::acceptLoop, "split-proxy-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        log("本地分流代理已启动 127.0.0.1:" + server.getLocalPort());
        return server.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        server = null;
    }

    private void log(String s) {
        Logger l = logger;
        if (l != null) l.log(s);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket c = server.accept();
                final int id = connId.incrementAndGet();
                Thread t = new Thread(() -> handle(c, id), "split-proxy-" + id);
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (running) log("accept 异常：" + e.getMessage());
            }
        }
    }

    private void handle(Socket client, int id) {
        try {
            client.setTcpNoDelay(true);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            byte[] head = readHeader(in);
            if (head == null) {
                close(client);
                return;
            }
            String header = new String(head, StandardCharsets.ISO_8859_1);
            String first = header.split("\r\n", 2)[0];
            String[] parts = first.split(" ");
            if (parts.length < 2) {
                writeAndClose(out, "HTTP/1.1 400 Bad Request\r\n\r\n");
                close(client);
                return;
            }
            String method = parts[0];
            String target = parts[1];

            if ("CONNECT".equalsIgnoreCase(method)) {
                String host;
                int port;
                int c = target.lastIndexOf(':');
                if (c < 0) {
                    host = target;
                    port = 443;
                } else {
                    host = target.substring(0, c);
                    port = parseInt(target.substring(c + 1), 443);
                }
                if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
                boolean viaProxy = rules.proxyFor(host);
                log("#" + id + " CONNECT " + host + ":" + port + (viaProxy ? "  → 代理" : "  → 直连"));
                if (viaProxy) {
                    Socket up = openUpstream(host, port, header);
                    if (up == null) {
                        writeAndClose(out, "HTTP/1.1 502 Bad Gateway\r\n\r\n");
                        close(client);
                        return;
                    }
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    pump(client, up);
                } else {
                    Socket dst = new Socket();
                    dst.connect(new InetSocketAddress(host, port), 15000);
                    dst.setTcpNoDelay(true);
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    pump(client, dst);
                }
            } else {
                // 普通 HTTP
                String targetHost = hostOf(target, header);
                int targetPort = portOf(target, header);
                boolean viaProxy = rules.proxyFor(targetHost);
                log("#" + id + " " + method + " " + targetHost + (viaProxy ? "  → 代理" : "  → 直连"));
                if (viaProxy) {
                    Socket up = openUpstreamHttp(header, targetHost, targetPort);
                    if (up == null) {
                        writeAndClose(out, "HTTP/1.1 502 Bad Gateway\r\n\r\n");
                        close(client);
                        return;
                    }
                    pump(client, up);
                } else {
                    Socket dst = new Socket();
                    dst.connect(new InetSocketAddress(targetHost, targetPort), 15000);
                    dst.setTcpNoDelay(true);
                    String originForm = toOriginForm(header, target);
                    dst.getOutputStream().write(originForm.getBytes(StandardCharsets.ISO_8859_1));
                    dst.getOutputStream().flush();
                    pump(client, dst);
                }
            }
        } catch (Exception e) {
            log("连接异常 #" + id + "：" + e.getMessage());
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String hostOf(String target, String header) {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            try {
                return java.net.URI.create(target).getHost();
            } catch (Exception ignored) {}
        }
        for (String line : header.split("\r\n")) {
            if (line.toLowerCase().startsWith("host:")) {
                String h = line.substring(5).trim();
                int c = h.lastIndexOf(':');
                return c > 0 ? h.substring(0, c) : h;
            }
        }
        return "";
    }

    private static int portOf(String target, String header) {
        if (target.startsWith("https://")) return 443;
        for (String line : header.split("\r\n")) {
            if (line.toLowerCase().startsWith("host:")) {
                String h = line.substring(5).trim();
                int c = h.lastIndexOf(':');
                if (c > 0) return parseInt(h.substring(c + 1), 80);
            }
        }
        return 80;
    }

    private static String toOriginForm(String header, String absolute) {
        String path = "/";
        try {
            java.net.URI u = java.net.URI.create(absolute);
            path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
        } catch (Exception ignored) {}
        String[] lines = header.split("\r\n");
        StringBuilder sb = new StringBuilder();
        sb.append(lines[0].split(" ")[0]).append(' ').append(path).append(" HTTP/1.1\r\n");
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            String lower = l.toLowerCase();
            if (lower.startsWith("proxy-connection") || lower.startsWith("proxy-authorization")) continue;
            sb.append(l).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private Socket openUpstream(String host, int port, String header) throws IOException {
        Node n = upstream;
        if (n == null) return null;
        Socket up = new Socket();
        up.connect(new InetSocketAddress(n.server, n.port), 15000);
        up.setTcpNoDelay(true);
        if ("http".equals(n.type)) {
            String auth = basicAuth(n.uuid, n.password);
            String req = "CONNECT " + host + ":" + port + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\n"
                    + (auth.isEmpty() ? "" : "Proxy-Authorization: Basic " + auth + "\r\n")
                    + "Proxy-Connection: keep-alive\r\n\r\n";
            up.getOutputStream().write(req.getBytes(StandardCharsets.ISO_8859_1));
            up.getOutputStream().flush();
            // 读上游响应头，200 才算成功
            String resp = readLine(up.getInputStream());
            if (resp == null || !resp.contains(" 200")) {
                close(up);
                return null;
            }
            // 吃掉剩余头
            String l;
            while ((l = readLine(up.getInputStream())) != null && !l.isEmpty()) { /* skip */ }
        } else {
            if (!socks5Handshake(up, host, port, n)) {
                close(up);
                return null;
            }
        }
        return up;
    }

    private Socket openUpstreamHttp(String header, String host, int port) throws IOException {
        Node n = upstream;
        if (n == null) return null;
        Socket up = new Socket();
        up.connect(new InetSocketAddress(n.server, n.port), 15000);
        up.setTcpNoDelay(true);
        if ("http".equals(n.type)) {
            String auth = basicAuth(n.uuid, n.password);
            if (!auth.isEmpty()) {
                String extra = "Proxy-Authorization: Basic " + auth + "\r\n";
                header = header.replace("\r\n\r\n", "\r\n" + extra + "\r\n");
            }
            up.getOutputStream().write(header.getBytes(StandardCharsets.ISO_8859_1));
            up.getOutputStream().flush();
        } else {
            // socks5 上游：握手后按 origin-form 请求
            if (!socks5Handshake(up, host, port, n)) {
                close(up);
                return null;
            }
            String origin = toOriginForm(header, header.split("\r\n")[0].split(" ")[1]);
            up.getOutputStream().write(origin.getBytes(StandardCharsets.ISO_8859_1));
            up.getOutputStream().flush();
        }
        return up;
    }

    private static String basicAuth(String user, String pass) {
        if (user == null || user.isEmpty()) return "";
        String s = user + ":" + (pass == null ? "" : pass);
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private boolean socks5Handshake(Socket up, String host, int port, Node n) throws IOException {
        InputStream in = up.getInputStream();
        OutputStream out = up.getOutputStream();
        boolean auth = n.uuid != null && !n.uuid.isEmpty();
        out.write(new byte[]{0x05, auth ? (byte) 0x02 : (byte) 0x01, (byte) (auth ? 0x02 : 0x00)});
        out.flush();
        byte[] resp = new byte[2];
        readFully(in, resp, 2);
        if (resp[0] != 0x05) return false;
        if (resp[1] == 0x02) {
            byte[] u = n.uuid.getBytes(StandardCharsets.UTF_8);
            byte[] p = (n.password == null ? "" : n.password).getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write(0x01);
            b.write(u.length);
            b.write(u, 0, u.length);
            b.write(p.length);
            b.write(p, 0, p.length);
            out.write(b.toByteArray());
            out.flush();
            byte[] ar = new byte[2];
            readFully(in, ar, 2);
            if (ar[1] != 0x00) return false;
        } else if (resp[1] != 0x00) {
            return false;
        }
        byte[] hb = host.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        req.write(0x05);
        req.write(0x01);
        req.write(0x00);
        req.write(0x03);
        req.write(hb.length);
        req.write(hb, 0, hb.length);
        req.write((port >> 8) & 0xFF);
        req.write(port & 0xFF);
        out.write(req.toByteArray());
        out.flush();
        byte[] rep = new byte[4];
        readFully(in, rep, 4);
        if (rep[1] != 0x00) return false;
        int len;
        switch (rep[3]) {
            case 0x01: len = 4; break;
            case 0x04: len = 16; break;
            case 0x03: len = in.read(); break;
            default: len = 0;
        }
        byte[] skip = new byte[len + 2];
        readFully(in, skip, skip.length);
        return true;
    }

    private static void readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) throw new IOException("上游连接已关闭");
            off += r;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) break;
        }
        if (c < 0 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static byte[] readHeader(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        int c;
        while ((c = in.read()) >= 0) {
            b.write(c);
            if (c == '\r') state = 1;
            else if (c == '\n' && state == 1) {
                state = 2;
                if (b.size() >= 4) {
                    byte[] a = b.toByteArray();
                    int n = a.length;
                    if (a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r') return a;
                }
            } else state = 0;
            if (b.size() > 65536) break;
        }
        return b.size() == 0 ? null : b.toByteArray();
    }

    /** 双向转发，任一端结束就关掉两端。 */
    private void pump(Socket a, Socket b) {
        Thread t1 = new Thread(() -> copy(a, b), "pump-up");
        Thread t2 = new Thread(() -> copy(b, a), "pump-down");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ignored) {}
        close(a);
        close(b);
    }

    private static void copy(Socket from, Socket to) {
        byte[] buf = new byte[16384];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            try { to.shutdownOutput(); } catch (Exception ignored) {}
        }
    }

    private static void writeAndClose(OutputStream out, String s) {
        try {
            out.write(s.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        } catch (Exception ignored) {}
    }

    private static void close(Socket s) {
        try { if (s != null) s.close(); } catch (Exception ignored) {}
    }
}
