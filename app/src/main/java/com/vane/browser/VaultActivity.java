package com.vane.browser;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 隐私空间：私密网站 + 私密视频，进入需验证，界面禁止截屏。 */
public class VaultActivity extends AppCompatActivity {

    private static final int REQ_UNLOCK = 0x7001;
    private static final int REQ_PICK = 0x7002;

    private UiConfig ui;
    private LinearLayout list;
    private int tab = 0;              // 0 网站 1 视频
    private boolean unlocked = false;
    private long leftAt = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ui = UiConfig.load();
        Theme.applyToWindow(this, ui);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Theme.bg(this, ui));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 30));
        sc.addView(list);
        setContentView(sc);
        requestUnlock();
    }

    private void requestUnlock() {
        // 先看有没有设过「隐私空间密码」：有就用应用内密码，不必每次都走系统指纹/锁屏
        if (!Prefs.get("vault_pin_hash", "").isEmpty()) {
            askPin();
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isDeviceSecure()) {
            Intent i = km.createConfirmDeviceCredentialIntent("隐私空间", "验证后进入");
            if (i != null) {
                startActivityForResult(i, REQ_UNLOCK);
                return;
            }
        }
        unlocked = true;      // 设备没设锁屏，直接进（提示用户去设）
        render();
    }

    /** 应用内密码：输入后比对 SHA-256，连续错 3 次直接退出。 */
    private void askPin() {
        final int[] tries = {0};
        Widgets.input(this, ui, "隐私空间密码", "", false, v -> {
            String hash = sha256(v == null ? "" : v.trim());
            if (hash.equals(Prefs.get("vault_pin_hash", ""))) {
                unlocked = true;
                render();
            } else if (++tries[0] >= 3) {
                Widgets.toast(this, "密码不对，已退出");
                finish();
            } else {
                Widgets.toast(this, "密码不对，再试一次");
                askPin();
            }
        });
    }

    static String sha256(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_UNLOCK) {
            if (res == Activity.RESULT_OK) {
                unlocked = true;
                render();
            } else {
                finish();
            }
            return;
        }
        if (req == REQ_PICK && res == Activity.RESULT_OK && data != null && data.getData() != null) {
            importVideo(data.getData());
        }
    }

    @Override protected void onStop() {
        super.onStop();
        leftAt = System.currentTimeMillis();
    }

    /** 解密到缓存里的临时播放文件：进来时清掉旧的，退出时全清。 */
    private void purgeTemps(boolean all) {
        File[] fs = getCacheDir() == null ? null : getCacheDir().listFiles();
        if (fs == null) return;
        long now = System.currentTimeMillis();
        for (File f : fs) {
            if (!f.getName().startsWith("play-tmp")) continue;
            if (all || now - f.lastModified() > 10 * 60_000L) f.delete();
        }
    }

    @Override protected void onDestroy() {
        purgeTemps(true);
        super.onDestroy();
    }

    @Override protected void onStart() {
        super.onStart();
        purgeTemps(false);
        if (leftAt > 0 && System.currentTimeMillis() - leftAt > 30_000) {
            unlocked = false;
            leftAt = 0;
            requestUnlock();
        }
    }

    private void render() {
        list.removeAllViews();
        if (!unlocked) return;

        // 顶栏
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(Theme.dp(this, 18), Theme.dp(this, 14), Theme.dp(this, 18), Theme.dp(this, 10));
        TextView back = Widgets.text(this, "‹", 22, Theme.fg(this, ui));
        back.setPadding(0, 0, Theme.dp(this, 14), 0);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        head.addView(Widgets.text(this, "隐私空间", 19, Theme.fg(this, ui)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(head);

        // 分段
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(Theme.dp(this, 18), 0, Theme.dp(this, 18), Theme.dp(this, 8));
        seg.addView(segBtn("网站", 0), new LinearLayout.LayoutParams(0, Theme.dp(this, 36), 1f));
        seg.addView(segBtn("视频", 1), new LinearLayout.LayoutParams(0, Theme.dp(this, 36), 1f));
        list.addView(seg);

        if (tab == 0) renderSites(); else renderVideos();
    }

    private View segBtn(String name, int idx) {
        TextView t = Widgets.text(this, name, 13,
                tab == idx ? Theme.bg(this, ui) : Theme.hint(this, ui));
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(Theme.rounded(tab == idx ? Theme.fg(this, ui) : Theme.field(this, ui),
                Theme.dp(this, 10)));
        t.setOnClickListener(v -> {
            tab = idx;
            render();
        });
        return t;
    }

    private void renderSites() {
        JSONArray arr = Prefs.getArray("vault_sites");
        final String pageUrl = getIntent() == null ? "" : getIntent().getStringExtra("page_url");
        if (pageUrl != null && !pageUrl.isEmpty()) {
            list.addView(row("＋ 存当前页", pageUrl, () -> {
                try {
                    JSONArray a = Prefs.getArray("vault_sites");
                    JSONObject o = new JSONObject();
                    o.put("url", pageUrl);
                    o.put("title", pageUrl);
                    a.put(o);
                    Prefs.putArray("vault_sites", a);
                    Widgets.toast(this, "已存进隐私空间");
                    render();
                } catch (Exception ignored) {}
            }));
        }
        list.addView(row("＋ 添加网站", "把当前页或一个网址存进隐私空间", () ->
                Widgets.input(this, ui, "网址", "", true, u -> {
                    if (u == null || u.trim().isEmpty()) return;
                    try {
                        JSONArray a = Prefs.getArray("vault_sites");
                        JSONObject o = new JSONObject();
                        o.put("url", u.trim());
                        o.put("title", u.trim());
                        a.put(o);
                        Prefs.putArray("vault_sites", a);
                        render();
                    } catch (Exception ignored) {}
                })));

        for (int i = 0; i < arr.length(); i++) {
            final int idx = i;
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final String url = o.optString("url", "");
            list.addView(row(o.optString("title", url), url, () -> {
                startActivity(new Intent(this, MainActivity.class)
                        .putExtra("open_url", url)
                        .putExtra("private_url", true));   // 极简浏览器：隐私空间的站不进历史
            }, () -> {
                Widgets.confirm(this, ui, "从隐私空间移除？", url, () -> {
                    JSONArray a = Prefs.getArray("vault_sites");
                    JSONArray fresh = new JSONArray();
                    for (int k = 0; k < a.length(); k++) if (k != idx) fresh.put(a.opt(k));
                    Prefs.putArray("vault_sites", fresh);
                    render();
                });
            }));
        }
        if (arr.length() == 0) list.addView(hint("还没有私密网站"));
    }

    private void renderVideos() {
        File dir = new File(getFilesDir(), "vault");
        if (!dir.exists()) dir.mkdirs();
        File[] all = dir.listFiles();
        final java.util.List<File> files = new java.util.ArrayList<>();
        if (all != null) for (File f : all) if (f.getName().endsWith(".enc")) files.add(f);
        if (all != null) for (File f : all) {
            if (f.getName().endsWith(".tmp")) f.delete();
            // 老版本「下载到隐私空间」会落明文在这里，顺手清掉，只留 .enc
            else if (!f.getName().endsWith(".enc")) f.delete();
        }
        list.addView(row("＋ 导入视频", "选一个视频，复制进隐私空间（原文件不动）", () ->
                Widgets.pick(this, ui, "从哪里导入", java.util.Arrays.asList(
                        "相册视频（最近 30 个）", "系统文件选择器"), k -> {
                    if (k == 0) pickVideoFromGallery();
                    else {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("video/*");
                        startActivityForResult(i, REQ_PICK);
                    }
                })));

        list.addView(row("＋ 导入加密副本", "把之前导出的 vault-enc-*.enc 收回隐私空间",
                this::pickEncFromDownloads));
        if (files.isEmpty()) {
            list.addView(hint("还没有私密视频"));
            return;
        }
        java.util.Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (final File f : files) {
            list.addView(row("私密视频 " + f.getName().replace(".enc", ""),
                    humanSize(f.length()) + " · 已加密", () -> {
                new Thread(() -> {
                    File cache = new File(getCacheDir(), "play-tmp.mp4");
                    boolean ok = com.vane.browser.core.VaultFile.decrypt(f, cache);
                    runOnUiThread(() -> {
                        if (!ok) {
                            Widgets.toast(this, "解密失败");
                            return;
                        }
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(androidx.core.content.FileProvider.getUriForFile(this,
                                getPackageName() + ".fileprovider", cache), "video/*");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(i);
                        } catch (Exception e) {
                            Widgets.toast(this, "没有能播放的应用");
                        }
                    });
                }, "vault-decrypt").start();
            }, () -> Widgets.pick(this, ui, f.getName(), java.util.Arrays.asList(
                    "删除", "导出加密副本到 下载/Vane"), k -> {
                if (k == 1) exportEncCopy(f);
                else Widgets.confirm(this, ui, "删除这个视频？",
                        f.getName() + "\n" + humanSize(f.length()) + "，删了找不回来",
                        () -> {
                            f.delete();
                            purgeTemps(true);
                            Widgets.toast(this, "已删除");
                            render();
                        });
            })));
        }
    }

    /** 收回导出的加密副本：只搬文件，不解密。 */
    private void pickEncFromDownloads() {
        final java.util.List<String> names = new java.util.ArrayList<>();
        final java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
        try {
            android.net.Uri base = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] proj = {android.provider.MediaStore.MediaColumns._ID,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.SIZE};
            try (android.database.Cursor cur = getContentResolver().query(base, proj,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'vault-enc-%'",
                    null, android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                while (cur != null && cur.moveToNext() && names.size() < 30) {
                    long id = cur.getLong(0);
                    names.add(cur.getString(1) + " · " + humanSize(cur.getLong(2)));
                    uris.add(android.content.ContentUris.withAppendedId(base, id));
                }
            }
        } catch (Exception ignored) {}
        if (names.isEmpty()) {
            Widgets.toast(this, "下载/Vane 里没有 vault-enc-* 加密副本");
            return;
        }
        Widgets.pick(this, ui, "选择要收回的加密副本", names, i -> {
            final android.net.Uri u = uris.get(i);
            new Thread(() -> {
                try {
                    File dir = new File(getFilesDir(), "vault");
                    if (!dir.exists()) dir.mkdirs();
                    String nm = names.get(i);
                    String base = nm.substring(0, nm.indexOf(" · ")).replace("vault-enc-", "");
                    if (!base.endsWith(".enc")) base = base + ".enc";
                    File out = new File(dir, base);
                    java.io.InputStream in = getContentResolver().openInputStream(u);
                    java.io.FileOutputStream os = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    os.close();
                    in.close();
                    runOnUiThread(() -> {
                        Widgets.toast(this, "已收回");
                        render();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Widgets.toast(this, "收回失败：" + e.getMessage()));
                }
            }, "vault-import-enc").start();
        });
    }

    /** 把 .enc 原样导出到公共下载目录：加密文件本身可以备份，但离开 App 解不开。 */
    private void exportEncCopy(File f) {
        new Thread(() -> {
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                String name = "vault-enc-" + f.getName();
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Vane");
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.close();
                in.close();
                runOnUiThread(() -> Widgets.info(this, ui, "已导出加密副本",
                        "下载/Vane/" + name + "\n这个文件在原机之外解不开（AES-GCM，密钥在 Android Keystore）。"));
            } catch (Exception e) {
                runOnUiThread(() -> Widgets.toast(this, "导出失败：" + e.getMessage()));
            }
        }, "vault-export").start();
    }

    private static final int REQ_VIDEO_PERM = 0x51;

    /** 相册视频直接列出来选：系统选择器在分区存储下常常只给「最近」，选不到相册。 */
    private void pickVideoFromGallery() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.READ_MEDIA_VIDEO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.READ_MEDIA_VIDEO"}, REQ_VIDEO_PERM);
            return;
        }
        listVideos();
    }

    private void listVideos() {
        final java.util.List<String> names = new java.util.ArrayList<>();
        final java.util.List<Uri> uris = new java.util.ArrayList<>();
        try {
            String[] proj = {
                    android.provider.MediaStore.Video.Media._ID,
                    android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Video.Media.SIZE};
            try (android.database.Cursor cur = getContentResolver().query(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    proj, null, null,
                    android.provider.MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                while (cur != null && cur.moveToNext() && names.size() < 30) {
                    long id = cur.getLong(0);
                    String nm = cur.getString(1);
                    long size = cur.getLong(2);
                    uris.add(android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id));
                    names.add(nm + " · " + (size / 1048576) + " MB");
                }
            }
        } catch (Exception ignored) {}
        if (names.isEmpty()) {
            Widgets.toast(this, "相册里没读到视频（可能没有权限）");
            return;
        }
        Widgets.pick(this, ui, "选择视频（加密后存进隐私空间）", names, i -> importVideo(uris.get(i)));
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_VIDEO_PERM) {
            if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                listVideos();
            } else {
                Widgets.toast(this, "没给读相册权限，改用系统文件选择器");
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("video/*");
                startActivityForResult(i, REQ_PICK);
            }
        }
    }

    private void importVideo(Uri uri) {
        new Thread(() -> {
            try {
                File dir = new File(getFilesDir(), "vault");
                if (!dir.exists()) dir.mkdirs();
                String name = "v-" + System.currentTimeMillis();
                File tmp = new File(dir, name + ".tmp");
                InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                // 加密成 .enc，删掉明文临时文件
                File enc = new File(dir, name + ".enc");
                boolean ok = com.vane.browser.core.VaultFile.encrypt(tmp, enc);
                tmp.delete();
                if (!ok) throw new Exception("加密失败");
                runOnUiThread(() -> {
                    Widgets.toast(this, "已导入");
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Widgets.toast(this, "导入失败：" + e.getMessage()));
            }
        }, "vault-import").start();
    }

    /** 453KB 别再显示成 0 MB。 */
    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private View hint(String t) {
        TextView v = Widgets.text(this, t, 13, Theme.hint(this, ui));
        v.setPadding(Theme.dp(this, 18), Theme.dp(this, 24), Theme.dp(this, 18), Theme.dp(this, 24));
        return v;
    }

    private View row(String title, String desc, Runnable onClick) {
        return row(title, desc, onClick, null);
    }

    private View row(String title, String desc, Runnable onClick, Runnable onLong) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(Theme.dp(this, 18), Theme.dp(this, 13), Theme.dp(this, 18), Theme.dp(this, 13));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Widgets.text(this, title, 15, Theme.fg(this, ui)));
        if (desc != null && !desc.isEmpty()) {
            TextView d = Widgets.text(this, desc, 11, Theme.hint(this, ui));
            d.setPadding(0, Theme.dp(this, 2), 0, 0);
            texts.addView(d);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Widgets.text(this, "›", 16, Theme.hint(this, ui)));
        r.setOnClickListener(v -> onClick.run());
        if (onLong != null) r.setOnLongClickListener(v -> {
            onLong.run();
            return true;
        });
        return r;
    }
}
