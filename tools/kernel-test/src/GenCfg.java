import com.vane.browser.proxy.*;
import java.nio.file.*;
import java.util.*;

/**
 * 用项目里真正的 CoreConfig 生成配置，供 sing-box 校验与联调。
 * 用法：java GenCfg <输出文件> <mode: rule|global|direct> <节点类型: vmess|trojan|ss>
 */
public class GenCfg {
    public static void main(String[] a) throws Exception {
        String out = a[0];
        String mode = a.length > 1 ? a[1] : "rule";
        String type = a.length > 2 ? a[2] : "vmess";

        RuleEngine rules = new RuleEngine(new android.content.Context());
        rules.setMode(mode);

        List<Node> nodes = new ArrayList<>();
        if ("vmess".equals(type)) {
            Node vm = new Node();
            vm.type = "vmess"; vm.name = "test-vmess"; vm.server = "127.0.0.1"; vm.port = 10086;
            vm.uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"; vm.alterId = 0; vm.network = "tcp";
            nodes.add(vm);
        } else if ("trojan".equals(type)) {
            Node tj = new Node();
            tj.type = "trojan"; tj.name = "test-trojan"; tj.server = "127.0.0.1"; tj.port = 10443;
            tj.password = "tjpass"; tj.tls = true; tj.sni = "localhost"; tj.security = "tls";
            nodes.add(tj);
        } else if ("hysteria2".equals(type)) {
            Node hy = new Node();
            hy.type = "hysteria2"; hy.name = "test-hy2"; hy.server = "127.0.0.1"; hy.port = 10444;
            hy.password = "hypass"; hy.tls = true; hy.sni = "localhost"; hy.security = "tls";
            hy.insecure = true; hy.obfs = "salamander"; hy.obfsPassword = "ob";
            hy.upMbps = 100; hy.downMbps = 200;
            nodes.add(hy);
        } else if ("tuic".equals(type)) {
            Node tu = new Node();
            tu.type = "tuic"; tu.name = "test-tuic"; tu.server = "127.0.0.1"; tu.port = 10445;
            tu.uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"; tu.password = "tuicpass";
            tu.tls = true; tu.sni = "localhost"; tu.security = "tls"; tu.insecure = true;
            tu.alpn = "h3"; tu.congestion = "bbr"; tu.udpRelayMode = "native";
            nodes.add(tu);
        } else {
            Node ss = new Node();
            ss.type = "ss"; ss.name = "test-ss"; ss.server = "127.0.0.1"; ss.port = 10888;
            ss.method = "aes-128-gcm"; ss.password = "sspass";
            nodes.add(ss);
        }

        String cfg = CoreConfig.build(nodes, nodes.get(0).name, rules, 2081);
        if ("trojan".equals(type) || "hysteria2".equals(type) || "tuic".equals(type)) {
            // 自签证书联调：仅测试时放宽校验
            cfg = cfg.replace("\"insecure\":false", "\"insecure\":true");
        }
        Files.write(Paths.get(out), cfg.getBytes("UTF-8"));
        System.out.println("生成配置: " + out + " (" + cfg.length() + " 字节, mode=" + mode + ", node=" + type + ")");
    }
}
