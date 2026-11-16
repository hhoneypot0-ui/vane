import com.vane.browser.proxy.*;
import java.nio.file.*;
import java.util.*;

/**
 * 联调链条：订阅文本 → SubscriptionParser → CoreConfig → config.json。
 * 节点是本地构造的测试值，不涉及任何真实账号。
 */
public class SubChain {
    public static void main(String[] a) throws Exception {
        String out = a[0];
        String links =
            "ss://" + java.util.Base64.getEncoder().encodeToString("aes-128-gcm:sspass".getBytes())
                + "@10.0.0.1:8388#SS-%E8%8A%82%E7%82%B9\n"
            + "vmess://" + java.util.Base64.getEncoder().encodeToString((
                "{\"v\":\"2\",\"ps\":\"VMess-ws\",\"add\":\"10.0.0.2\",\"port\":\"443\",\"id\":\"b831381d-6324-4d53-ad4f-8cda48b30811\","
                + "\"aid\":\"0\",\"net\":\"ws\",\"type\":\"none\",\"host\":\"cdn.example.com\",\"path\":\"/ray\",\"tls\":\"tls\",\"sni\":\"cdn.example.com\"}")
                .getBytes("UTF-8")) + "\n"
            + "vless://b831381d-6324-4d53-ad4f-8cda48b30811@10.0.0.3:443"
                + "?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome&pbk=jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0"
                + "&sid=0123abcd&type=tcp&flow=xtls-rprx-vision#VLESS-REALITY\n"
            + "trojan://tjpass@10.0.0.4:443?sni=t.example.com&alpn=h2,http/1.1&allowInsecure=1#Trojan\n"
            + "hysteria2://hypass@10.0.0.5:443?sni=h.example.com&obfs=salamander&obfs-password=ob&up=100&down=200&insecure=1#HY2\n"
            + "tuic://b831381d-6324-4d53-ad4f-8cda48b30811:tuicpass@10.0.0.6:443?sni=t6.example.com&alpn=h3"
                + "&congestion_control=bbr&udp_relay_mode=native#TUIC\n";

        String sub = java.util.Base64.getEncoder().encodeToString(links.getBytes("UTF-8"));
        List<Node> nodes = SubscriptionParser.parse(sub);
        System.out.println("解析到节点: " + nodes.size());
        Set<String> types = new LinkedHashSet<>();
        for (Node n : nodes) {
            types.add(n.type);
            System.out.println("  " + n.type + " | " + n.name + " | " + n.summary()
                    + " | alpn=" + n.alpn + " fp=" + n.fingerprint + " flow=" + n.flow
                    + " reality=" + (n.realityPublicKey.isEmpty() ? "-" : "yes")
                    + " obfs=" + n.obfs + " congestion=" + n.congestion);
        }
        List<String> need = Arrays.asList("ss", "vmess", "vless", "trojan", "hysteria2", "tuic");
        List<String> missing = new ArrayList<>();
        for (String t : need) if (!types.contains(t)) missing.add(t);
        if (nodes.size() != 6 || !missing.isEmpty()) {
            System.out.println("FAIL 缺类型: " + missing);
            System.exit(2);
        }
        RuleEngine rules = new RuleEngine(new android.content.Context());
        String cfg = CoreConfig.build(nodes, nodes.get(0).name, rules, 2081);
        if (!cfg.contains("\"reality\"") || !cfg.contains("\"up_mbps\"") || !cfg.contains("\"tuic\"")) {
            System.out.println("FAIL 配置里缺少 reality / up_mbps / tuic");
            System.exit(3);
        }
        Files.write(Paths.get(out), cfg.getBytes("UTF-8"));
        System.out.println("OK 6 种节点解析并生成配置: " + out);
    }
}
