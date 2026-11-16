package com.vane.browser.core;

import android.content.Context;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;

/** 内建下载器：支持 Range 的文件切成 3 段并发下载，再合并落盘。 */
public final class Dl {

    public interface Cb {
        void progress(long done, long total);
        void done(boolean ok, String path, String msg);
    }

    private static final int THREADS = 3;

    /** 内建下载任务（内存里维护，界面用来暂停/继续/取消）。 */
    public static class Task {
        public String id = String.valueOf(System.currentTimeMillis());
        public String url;
        public String name;
        public boolean toVault;
        public volatile long done, total;
        public volatile int state = 1;      // 1运行 2暂停 3完成 4失败 5取消
        public volatile String msg = "";
        public volatile boolean stopFlag = false;
        public String path;
    }

    public static final java.util.List<Task> TASKS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<Task>());

    public static Task find(String id) {
        synchronized (TASKS) {
            for (Task t : TASKS) if (t.id.equals(id)) return t;
        }
        return null;
    }

    public static void pause(Task t) {
        if (t == null) return;
        t.stopFlag = true;
        t.state = 2;
        t.msg = "已暂停";
    }

    public static void cancel(Task t) {
        if (t == null) return;
        t.stopFlag = true;
        t.state = 5;
        t.msg = "已取消";
    }

    /** 删除任务：停掉线程，把断点临时文件和状态 json 一起清掉。 */
    public static void remove(Context c, Task t) {
        if (t == null) return;
        t.stopFlag = true;
        t.state = 5;
        t.msg = "已删除";
        try {
            File dir = t.toVault ? new File(c.getFilesDir(), "vault")
                    : c.getExternalFilesDir("dl");
            if (dir != null) {
                File part = new File(dir, t.name);
                if (part.exists()) part.delete();
                File st = new File(stateFile(part));
                if (st.exists()) st.delete();
            }
        } catch (Throwable ignored) {}
        synchronized (TASKS) {
            TASKS.remove(t);
        }
    }

    public static void resume(Task t, Context c, Cb cb) {
        if (t == null) return;
        t.stopFlag = false;
        t.state = 1;
        start(c, t.url, t.name, t.toVault, new Cb() {
            @Override public void progress(long done, long total) {
                t.done = done; t.total = total;
                if (cb != null) cb.progress(done, total);
            }
            @Override public void done(boolean ok, String path, String msg) {
                t.state = ok ? 3 : 4;
                t.path = path; t.msg = msg;
                if (cb != null) cb.done(ok, path, msg);
            }
        });
    }

    private Dl() {}

    /** 分片状态：数组 [已下字节, 起始, 结束]。 */
    private static String stateFile(File part) { return part.getAbsolutePath() + ".json"; }

    private static long[] loadState(File part, long total, int n) {
        long[] st = new long[n * 3];
        try {
            java.io.File f = new java.io.File(stateFile(part));
            if (!f.exists()) return null;
            org.json.JSONObject o = new org.json.JSONObject(
                    new String(java.nio.file.Files.readAllBytes(f.toPath())));
            if (o.optLong("total") != total) return null;
            org.json.JSONArray a = o.optJSONArray("chunks");
            if (a == null || a.length() != n) return null;
            for (int i = 0; i < n; i++) {
                org.json.JSONArray c = a.optJSONArray(i);
                st[i * 3] = c.optLong(0);
                st[i * 3 + 1] = c.optLong(1);
                st[i * 3 + 2] = c.optLong(2);
            }
            return st;
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveState(File part, long total, long[][] chunks) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("total", total);
            org.json.JSONArray a = new org.json.JSONArray();
            for (long[] ch : chunks) {
                org.json.JSONArray c = new org.json.JSONArray();
                c.put(ch[0]); c.put(ch[1]); c.put(ch[2]);
                a.put(c);
            }
            o.put("chunks", a);
            java.io.File f = new java.io.File(stateFile(part));
            java.nio.file.Files.write(f.toPath(), o.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    public static void start(final Context c, final String url, final String suggestedName, final Cb cb) {
        start(c, url, suggestedName, false, cb);
    }

    /** 隐私空间落盘必须是加密的 .enc，明文临时文件用完即删。 */
    private static boolean sealToVault(File plain, Context c, Cb cb) {
        try {
            File dir = new File(c.getFilesDir(), "vault");
            if (!dir.exists()) dir.mkdirs();
            File enc = new File(dir, "v-" + System.currentTimeMillis() + ".enc");
            boolean ok = com.vane.browser.core.VaultFile.encrypt(plain, enc);
            plain.delete();
            if (ok) {
                cb.done(true, enc.getAbsolutePath(), "已加密存入隐私空间");
            } else {
                enc.delete();
                cb.done(false, null, "加密失败");
            }
            return ok;
        } catch (Exception e) {
            plain.delete();
            cb.done(false, null, "加密失败：" + e.getMessage());
            return false;
        }
    }

    /** toVault=true 时直接落进隐私空间，不进公共下载目录。 */
    public static void start(final Context c, final String url, final String suggestedName,
                             final boolean toVault, final Cb cb) {
        new Thread(() -> {
            File tmp = null;
            try {
                HttpURLConnection head = (HttpURLConnection) new URL(url).openConnection();
                head.setConnectTimeout(10000);
                head.setReadTimeout(15000);
                head.setRequestProperty("User-Agent", "Vane");
                head.setRequestProperty("Range", "bytes=0-0");
                long total = -1;
                boolean ranges = false;
                int code = head.getResponseCode();
                if (code == 206) {
                    ranges = true;
                    String cr = head.getHeaderField("Content-Range");
                    if (cr != null && cr.contains("/")) {
                        try {
                            total = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim());
                        } catch (Exception ignored) {}
                    }
                } else if (code == 200) {
                    String len = head.getHeaderField("Content-Length");
                    if (len != null) {
                        try {
                            total = Long.parseLong(len.trim());
                        } catch (Exception ignored) {}
                    }
                }
                head.disconnect();
                com.vane.browser.App.logEvent(c, "dl-probe", "url: " + url
                        + "\ncode: " + code + "\nranges: " + ranges + "\ntotal: " + total);

                final String name = suggestedName == null || suggestedName.isEmpty() ? "vane-download" : suggestedName;
                File dir;
                if (toVault) {
                    dir = new File(c.getFilesDir(), "vault");
                } else {
                    dir = c.getExternalFilesDir("dl");
                    if (dir == null) dir = c.getCacheDir();
                }
                if (!dir.exists()) dir.mkdirs();
                final File fTmp = new File(dir, name);
                tmp = fTmp;
                // 同一地址 + 同名文件的旧任务先摘掉：暂停后再「继续」会重复进队列，列表里就出现两行
                for (int i = TASKS.size() - 1; i >= 0; i--) {
                    Task old = TASKS.get(i);
                    if (old != null && url.equals(old.url) && name.equals(old.name)) TASKS.remove(i);
                }
                final Task task = new Task();
                task.url = url; task.name = name; task.toVault = toVault;
                TASKS.add(0, task);
                while (TASKS.size() > 20) TASKS.remove(TASKS.size() - 1);

                if (!ranges || total <= 0) {
                    com.vane.browser.App.logEvent(c, "dl-single", "url: " + url
                            + " (服务器没回 206，用单流)");
                    single(c, url, tmp, toVault, cb);
                    return;
                }
                com.vane.browser.App.logEvent(c, "dl-chunks", "url: " + url
                        + "\nthreads: " + THREADS + "\nsize: " + total);

                RandomAccessFile raf = new RandomAccessFile(tmp, "rw");
                raf.setLength(total);
                final long size = total;
                long part = size / THREADS;
                // 读旧状态：有就续传
                long[] saved = loadState(tmp, size, THREADS);
                final long[][] chunks = new long[THREADS][3];
                long already = 0;
                for (int i = 0; i < THREADS; i++) {
                    long from = i * part;
                    long to = (i == THREADS - 1) ? size - 1 : (from + part - 1);
                    chunks[i][1] = from;
                    chunks[i][2] = to;
                    chunks[i][0] = saved == null ? 0 : Math.max(0, Math.min(to - from + 1, saved[i * 3]));
                    already += chunks[i][0];
                }
                final AtomicLong done = new AtomicLong(already);
                task.total = size;
                task.done = already;
                if (already > 0) cb.progress(already, size);
                Thread[] ts = new Thread[THREADS];
                final boolean[] fail = {false};
                for (int i = 0; i < THREADS; i++) {
                    final int idx = i;
                    ts[i] = new Thread(() -> {
                        try {
                            long start = chunks[idx][1] + chunks[idx][0];
                            long to = chunks[idx][2];
                            if (start > to) return;
                            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                            conn.setRequestProperty("User-Agent", "Vane");
                            conn.setRequestProperty("Range", "bytes=" + start + "-" + to);
                            conn.setConnectTimeout(12000);
                            conn.setReadTimeout(20000);
                            InputStream in = conn.getInputStream();
                            byte[] buf = new byte[16384];
                            long pos = start;
                            int n;
                            long lastSave = 0;
                            while ((n = in.read(buf)) > 0) {
                                if (task.stopFlag) { in.close(); conn.disconnect(); return; }
                                synchronized (raf) {
                                    raf.seek(pos);
                                    raf.write(buf, 0, n);
                                }
                                pos += n;
                                chunks[idx][0] += n;
                                long d = done.addAndGet(n);
                                task.done = d;
                                cb.progress(d, size);
                                if (d - lastSave > 2 * 1024 * 1024) {
                                    lastSave = d;
                                    saveState(fTmp, size, chunks);
                                }
                            }
                            in.close();
                            conn.disconnect();
                            saveState(fTmp, size, chunks);
                        } catch (Exception e) {
                            fail[0] = true;
                            saveState(fTmp, size, chunks);
                        }
                    }, "dl-part-" + i);
                    ts[i].start();
                }
                for (Thread t : ts) t.join();
                raf.close();
                if (fail[0]) {
                    cb.done(false, null, "部分分片失败，文件可能不完整");
                    return;
                }
                if (task.stopFlag) { task.state = 2; task.msg = "已暂停"; return; }
                if (toVault) {
                    task.state = 3;
                    sealToVault(fTmp, c, cb);
                    return;
                }
                task.state = 3;
                publish(c, tmp, name, cb);
            } catch (Exception e) {
                cb.done(false, null, "下载失败：" + e.getMessage());
            }
        }, "dl").start();
    }

    private static void single(Context c, String url, File out, Cb cb) {
        single(c, url, out, false, cb);
    }

    private static void single(Context c, String url, File out, boolean toVault, Cb cb) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Vane");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            long total = conn.getContentLengthLong();
            InputStream in = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[16384];
            int n;
            long done = 0;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                done += n;
                cb.progress(done, total > 0 ? total : done);
            }
            fos.close();
            in.close();
            if (toVault) {
                sealToVault(out, c, cb);
            } else {
                publish(c, out, out.getName(), cb);
            }
        } catch (Exception e) {
            cb.done(false, null, "下载失败：" + e.getMessage());
        }
    }

    /** 落到公共下载目录（10+ 走 MediaStore）。 */
    private static void publish(Context c, File src, String name, Cb cb) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Vane");
                android.net.Uri uri = c.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                java.io.OutputStream os = c.getContentResolver().openOutputStream(uri);
                java.io.FileInputStream fis = new java.io.FileInputStream(src);
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
                fis.close();
                os.close();
                src.delete();
                cb.done(true, "下载/Vane/" + name, "下载完成");
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "Vane");
                if (!dir.exists()) dir.mkdirs();
                File dst = new File(dir, name);
                java.io.FileInputStream fis = new java.io.FileInputStream(src);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(dst);
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
                fis.close();
                fos.close();
                src.delete();
                android.media.MediaScannerConnection.scanFile(c,
                        new String[]{dst.getAbsolutePath()}, null, null);
                cb.done(true, dst.getAbsolutePath(), "下载完成");
            }
        } catch (Exception e) {
            cb.done(false, null, "保存失败：" + e.getMessage());
        }
    }
}
