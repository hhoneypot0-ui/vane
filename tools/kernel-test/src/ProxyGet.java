import java.io.*;
import java.net.*;

/** 通过本地代理取一个 URL，验证内核链路真的通。 */
public class ProxyGet {
    public static void main(String[] a) throws Exception {
        String proxy = a[0];              // 127.0.0.1:2081
        String url = a[1];
        String expect = a.length > 2 ? a[2] : null;
        String[] pp = proxy.split(":");
        Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(pp[0], Integer.parseInt(pp[1])));
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(p);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "VaneKernelTest/1.0");
        int code = c.getResponseCode();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
            if (in != null) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            }
        }
        String body = b.toString("UTF-8");
        System.out.println("HTTP " + code + "  正文 " + body.length() + " 字节: " + body.trim().substring(0, Math.min(80, body.trim().length())));
        if (code != 200) { System.out.println("FAIL 状态码不是 200"); System.exit(2); }
        if (expect != null && !body.contains(expect)) { System.out.println("FAIL 正文不含 " + expect); System.exit(3); }
        System.out.println("OK 代理链路可用");
    }
}
