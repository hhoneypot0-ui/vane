import com.vane.browser.proxy.*;
import java.util.List;

public class SubscriptionSelfTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String extra) {
        if (ok) { pass++; System.out.println("  ok   " + what + (extra.isEmpty() ? "" : "  [" + extra + "]")); }
        else { fail++; System.out.println("  FAIL " + what + "  [" + extra + "]"); }
    }
    public static void main(String[] a) {
        // 1. 单条 ss（带 tag，base64 凭证）
        String ss = "ss://" + java.util.Base64.getEncoder().encodeToString("aes-256-gcm:pass123".getBytes())
                + "@1.2.3.4:8388#%E9%A6%99%E6%B8%AF01";
        List<Node> l1 = SubscriptionParser.parse(ss);
        check("ss 解析出 1 条", l1.size() == 1, "size=" + l1.size());
        if (!l1.isEmpty()) {
            Node n = l1.get(0);
            check("ss server/port", "1.2.3.4".equals(n.server) && n.port == 8388, n.server + ":" + n.port);
            check("ss method/pass", "aes-256-gcm".equals(n.method) && "pass123".equals(n.password), n.method + "/" + n.password);
            check("ss tag 解码", "香港01".equals(n.name), n.name);
        }
        // 2. 整段 base64 订阅（trojan + vless + socks）
        String body = "trojan://pw%40x@t.example.com:443?sni=t.example.com#TR-1\n"
                + "vless://uuid-1234@v.example.com:8443?security=tls&type=ws&path=%2Fws&sni=v.example.com#VL-1\n"
                + "socks5://user:pa55@127.0.0.1:1080#SOCKS-1\n";
        String sub = java.util.Base64.getEncoder().encodeToString(body.getBytes());
        List<Node> l2 = SubscriptionParser.parse(sub);
        check("订阅 base64 解出 3 条", l2.size() == 3, "size=" + l2.size());
        for (Node n : l2) {
            System.out.println("       -> " + n.type + " " + n.name + " " + n.server + ":" + n.port
                    + (n.tls ? " tls" : "") + " net=" + n.network + " path=" + n.path);
        }
        if (l2.size() == 3) {
            check("trojan 密码 URL 解码", "pw@x".equals(l2.get(0).password), l2.get(0).password);
            check("vless tls/ws", l2.get(1).tls && "ws".equals(l2.get(1).network) && "/ws".equals(l2.get(1).path), l2.get(1).network + l2.get(1).path);
            check("socks 认证", "user".equals(l2.get(2).uuid) && "pa55".equals(l2.get(2).password), l2.get(2).uuid + "/" + l2.get(2).password);
            check("socks 默认端口", l2.get(2).port == 1080, "" + l2.get(2).port);
        }
        // 3. 进阶参数：reality / hysteria2 / tuic
        String adv =
              "vless://uuid-r@r.example.com:443?security=reality&sni=www.microsoft.com&fp=chrome"
            + "&pbk=jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0&sid=abcd&type=tcp"
            + "&flow=xtls-rprx-vision&alpn=h2,http/1.1#R-1\n"
            + "hysteria2://pw@hy.example.com:443?sni=hy.example.com&obfs=salamander&obfs-password=ob"
            + "&up=100&down=200&insecure=1&mport=443,8443#HY-1\n"
            + "tuic://uuid-t:pass-t@tu.example.com:443?sni=tu.example.com&alpn=h3"
            + "&congestion_control=bbr&udp_relay_mode=native&allow_insecure=1#TU-1\n";
        List<Node> l3 = SubscriptionParser.parse(adv);
        check("进阶参数解析出 3 条", l3.size() == 3, "size=" + l3.size());
        if (l3.size() == 3) {
            Node r = l3.get(0);
            check("vless reality 字段", "reality".equals(r.security)
                    && !r.realityPublicKey.isEmpty() && "abcd".equals(r.realityShortId)
                    && "chrome".equals(r.fingerprint), r.security + "/" + r.realityShortId);
            check("vless flow + alpn", "xtls-rprx-vision".equals(r.flow) && "h2,http/1.1".equals(r.alpn), r.flow + "/" + r.alpn);
            Node h = l3.get(1);
            check("hysteria2 obfs/带宽", "salamander".equals(h.obfs) && "ob".equals(h.obfsPassword)
                    && h.upMbps == 100 && h.downMbps == 200, h.obfs + "/" + h.upMbps + "/" + h.downMbps);
            check("hysteria2 insecure + 端口跳跃", h.insecure && "443,8443".equals(h.serverPorts), h.serverPorts);
            Node t = l3.get(2);
            check("tuic uuid/密码拆分", "uuid-t".equals(t.uuid) && "pass-t".equals(t.password), t.uuid + "/" + t.password);
            check("tuic 拥塞控制/中继", "bbr".equals(t.congestion) && "native".equals(t.udpRelayMode) && "h3".equals(t.alpn),
                    t.congestion + "/" + t.udpRelayMode + "/" + t.alpn);
        }

        // 4. 脏数据
        check("垃圾输入不炸", SubscriptionParser.parse("hello world").isEmpty(), "");
        check("畸形链接不炸", SubscriptionParser.parse("ss://nonsense").isEmpty(), "");
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
