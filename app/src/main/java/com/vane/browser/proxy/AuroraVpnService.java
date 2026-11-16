package com.vane.browser.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.vane.browser.MainActivity;

/**
 * 可选的全机分流（默认不用）。浏览器自身只靠 WebView 代理即可分流；
 * 需要让别的 App 也走这套规则时，再开这个 VpnService。
 *
 * 工作方式：建 tun → 把 fd 交给内置分流器（hev-socks5-tunnel / tun2socks），
 * 分流器把流量喂给 127.0.0.1:localPort 的本地代理（SplitProxyServer 或内核）。
 * 没放 tun2socks 的 .so 时服务会安全退出。
 */
public class AuroraVpnService extends VpnService {

    public static final String ACTION_START = "com.vane.browser.VPN_START";
    public static final String ACTION_STOP = "com.vane.browser.VPN_STOP";
    private static final String CHANNEL = "vane_vpn";
    private ParcelFileDescriptor tun;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        ProxyManager pm = ProxyManager.get(this);
        if (!pm.enabled || pm.localPort <= 0) {
            pm.log("VPN 模式需要先启用代理");
            stopSelf();
            return;
        }
        try {
            Builder b = new Builder();
            b.setSession("Vane分流");
            b.addAddress("10.10.0.2", 32);
            b.addRoute("0.0.0.0", 0);
            b.addDnsServer("223.5.5.5");
            b.addDnsServer("1.1.1.1");
            b.setMtu(1400);
            if (Build.VERSION.SDK_INT >= 29) {
                b.setMetered(false);
            }
            b.addDisallowedApplication(getPackageName()); // 自身走 WebView 代理，避免环回
            tun = b.establish();
            if (tun == null) {
                pm.log("VPN 建立失败（可能未授权）");
                stopSelf();
                return;
            }
            boolean ok = TunBridge.start(tun.getFd(), "127.0.0.1", pm.localPort);
            pm.log(ok ? "VPN 分流已启动（tun fd=" + tun.getFd() + "）"
                    : "缺少 tun2socks 原生库，VPN 模式不可用；浏览器内分流不受影响");
            showNotification();
        } catch (Exception e) {
            ProxyManager.get(this).log("VPN 异常：" + e.getMessage());
            stopSelf();
        }
    }

    private void stopVpn() {
        TunBridge.stop();
        if (tun != null) {
            try { tun.close(); } catch (Exception ignored) {}
            tun = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void showNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "分流服务", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = nb.setContentTitle("Vane").setContentText("分流运行中")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi).setOngoing(true).build();
        startForeground(1001, n);
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }
}
