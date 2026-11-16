package com.vane.browser.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.vane.browser.MainActivity;
import com.vane.browser.R;

/** 代理运行时挂前台通知，防止系统在后台把它杀掉。 */
public class ProxyKeepAlive extends Service {

    private static final String CH = "vane_proxy";
    private static final int ID = 1001;

    /** 默认静默：不弹任何通知，只挂一个 sticky 后台服务。 */
    public static void start(Context c) {
        try {
            if (!com.vane.browser.settings.Prefs.getBool("proxy_notify", false)) {
                c.startService(new Intent(c, ProxyKeepAlive.class));   // 无通知
                return;
            }
            Intent i = new Intent(c, ProxyKeepAlive.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {}
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, ProxyKeepAlive.class));
        } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!com.vane.browser.settings.Prefs.getBool("proxy_notify", false)) {
            return START_STICKY;                     // 静默模式：连前台都不进
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "代理运行中",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                if (nm != null) nm.createNotificationChannel(ch);
            }
            ProxyManager pm = ProxyManager.get(this);
            PendingIntent pi = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CH)
                    : new Notification.Builder(this);
            b.setContentTitle("Vane 代理运行中")
                    .setContentText(pm.linkLine())
                    .setSmallIcon(R.drawable.ic_proxy)
                    .setOngoing(true)
                    .setContentIntent(pi);
            startForeground(ID, b.build());
        } catch (Throwable ignored) {}
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
