package com.vane.browser.proxy;

/** 可选原生库桥接：把 sing-box / hev-socks5-tunnel 的 .so 放进 jniLibs 即可启用 VPN 全机分流。 */
public final class TunBridge {

    private static boolean loaded = false;
    private static boolean tried = false;

    private TunBridge() {}

    private static synchronized boolean load() {
        if (tried) return loaded;
        tried = true;
        try {
            System.loadLibrary("hev-socks5-tunnel");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
        return loaded;
    }

    public static boolean available() {
        return load();
    }

    public static boolean start(int tunFd, String proxyHost, int proxyPort) {
        if (!load()) return false;
        try {
            nativeStart(tunFd, proxyHost, proxyPort);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void stop() {
        if (!loaded) return;
        try { nativeStop(); } catch (Throwable ignored) {}
    }

    private static native void nativeStart(int tunFd, String host, int port);
    private static native void nativeStop();
}
