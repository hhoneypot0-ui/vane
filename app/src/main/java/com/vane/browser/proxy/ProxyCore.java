package com.vane.browser.proxy;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * 可选内核（sing-box）：负责 ss / vmess / vless / trojan / hysteria2 这些纯 Java 分流器处理不了的协议。
 *
 * 关键点：Android 10+ 禁止从 App 私有目录 exec 二进制，所以内核以 libsingbox.so 的形式随 APK 分发，
 * 安装时被解压到 nativeLibraryDir（该目录可执行，且不受 W^X 限制）。
 * 同时保留 files/core/sing-box 作为旧路径兼容。
 */
public class ProxyCore {

    public static final int PORT = 2081;
    private static final String LIB_NAME = "libsingbox.so";

    private final Context ctx;
    private Process process;
    private final List<String> logs = new ArrayList<>();
    private String cachedVersion;

    public ProxyCore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static String abi() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis != null && abis.length > 0 ? abis[0] : "arm64-v8a";
    }

    /** 内核可执行文件（找不到返回 null）。 */
    public File binary() {
        try {
            String dir = ctx.getApplicationInfo().nativeLibraryDir;
            if (dir != null) {
                File lib = new File(dir, LIB_NAME);
                if (lib.exists() && lib.canExecute()) return lib;
            }
        } catch (Exception ignored) {}
        File f = new File(new File(ctx.getFilesDir(), "core"), "sing-box");
        if (f.exists() && f.canExecute()) return f;
        return null;
    }

    public boolean available() {
        return binary() != null;
    }

    /** 不执行进程，只返回已探测到的版本；没探测过返回 null。 */
    public String cachedVersion() {
        return cachedVersion;
    }

    public synchronized String version() {
        if (cachedVersion != null) return cachedVersion;
        File bin = binary();
        if (bin == null) return cachedVersion = "未内置";
        try {
            Process p = new ProcessBuilder(bin.getAbsolutePath(), "version").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                p.waitFor();
                if (line != null) {
                    cachedVersion = line.replace("sing-box version ", "").trim();
                    return cachedVersion;
                }
            }
        } catch (Exception e) {
            return cachedVersion = "启动失败：" + e.getMessage();
        }
        return cachedVersion = "未知";
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /** 启动内核，成功返回本地 mixed 端口，失败返回 -1。 */
    public synchronized int start(String configJson, int listenPort, SplitProxyServer.Logger logger) {
        File bin = binary();
        if (bin == null) {
            log(logger, "未找到内核二进制：需要把 libsingbox.so 打进 APK 的 jniLibs/" + abi());
            return -1;
        }
        stop();
        try {
            File dir = new File(ctx.getFilesDir(), "core");
            if (!dir.exists() && !dir.mkdirs()) {
                log(logger, "无法创建内核工作目录");
                return -1;
            }
            File cfg = new File(dir, "config.json");
            try (FileWriter w = new FileWriter(cfg)) {
                w.write(configJson);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    bin.getAbsolutePath(), "run", "-c", cfg.getAbsolutePath(), "-D", dir.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.directory(dir);
            process = pb.start();
            final Process p = process;
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        log(logger, "  " + stripAnsi(line));
                    }
                } catch (Exception ignored) {}
            }, "sing-box-log");
            t.setDaemon(true);
            t.start();
            log(logger, "内核 " + version() + " 正在启动…（首次启动可能要十几秒）");
            if (waitPort(listenPort, 25000)) {
                log(logger, "内核已就绪，本地端口 " + listenPort);
                return listenPort;
            }
            log(logger, "内核 25 秒内没监听 " + listenPort + "，最后几行日志：");
            java.util.List<String> ls = logs();
            for (int i = Math.max(0, ls.size() - 8); i < ls.size(); i++) log(logger, "  " + ls.get(i));
            stop();
            return -1;
        } catch (Exception e) {
            log(logger, "内核启动失败：" + e.getMessage());
            stop();
            return -1;
        }
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    private static boolean waitPort(int port, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return true;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private void log(SplitProxyServer.Logger l, String s) {
        synchronized (logs) {
            logs.add(s);
            if (logs.size() > 400) logs.remove(0);
        }
        if (l != null) l.log(s);
    }

    public List<String> logs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public synchronized void stop() {
        Process p = process;
        process = null;
        if (p == null) return;
        try {
            p.destroy();
            if (!p.waitFor(1200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                p.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
            try {
                p.destroyForcibly();
            } catch (Throwable ignored2) {}
        }
    }
}
