package com.vane.browser.core;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import com.vane.browser.settings.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

/** 站点密码：保存在本机，用设备 ID 做轻量混淆（不是强加密，够挡住明文直读）。 */
public final class Passwords {

    private Passwords() {}

    private static String key(Context c) {
        String id = "vane";
        try {
            id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {}
        return (id == null ? "vane" : id) + "::v1";
    }

    /** 加密：优先 Keystore AES-GCM；解不开的老数据回退旧的异或格式（只读兼容）。 */
    private static String enc(Context c, String s) {
        if (s == null || s.isEmpty()) return "";
        String v = Vault.encrypt(c, s);
        return v != null ? v : legacyEnc(c, s);
    }

    private static String dec(Context c, String s) {
        if (s == null || s.isEmpty()) return "";
        String v = Vault.decrypt(c, s);
        if (v != null) return v;
        return legacyDec(c, s);          // 老版本存的异或数据
    }

    private static String legacyEnc(Context c, String s) {
        byte[] k = key(c).getBytes();
        byte[] d = s.getBytes();
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) out[i] = (byte) (d[i] ^ k[i % k.length]);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private static String legacyDec(Context c, String s) {
        try {
            byte[] k = key(c).getBytes();
            byte[] d = Base64.decode(s, Base64.NO_WRAP);
            byte[] out = new byte[d.length];
            for (int i = 0; i < d.length; i++) out[i] = (byte) (d[i] ^ k[i % k.length]);
            return new String(out);
        } catch (Exception e) {
            return "";
        }
    }

    public static void save(Context c, String host, String user, String pass) {
        if (host == null || host.isEmpty() || pass == null || pass.isEmpty()) return;
        try {
            JSONArray arr = Prefs.getArray("passwords");
            JSONArray fresh = new JSONArray();
            String u = enc(c, user == null ? "" : user), p = enc(c, pass);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean sameHost = host.equals(o.optString("h"));
                boolean sameUser = (user == null ? "" : user).equals(dec(c, o.optString("u")));
                if (!(sameHost && sameUser)) fresh.put(o);   // 同站同账号才覆盖，允许多账号
            }
            JSONObject o = new JSONObject();
            o.put("h", host);
            o.put("u", u);
            o.put("p", p);
            o.put("t", System.currentTimeMillis());
            fresh.put(o);
            Prefs.putArray("passwords", fresh);
        } catch (Exception ignored) {}
    }

    /** 返回该域名最近保存的一组 {user, pass}。 */
    public static String[] get(Context c, String host) {
        String[] best = null;
        long bestT = -1;
        try {
            JSONArray arr = Prefs.getArray("passwords");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || !host.equals(o.optString("h"))) continue;
                long t = o.optLong("t", 0);
                if (t >= bestT) {
                    bestT = t;
                    best = new String[]{dec(c, o.optString("u")), dec(c, o.optString("p"))};
                }
            }
        } catch (Exception ignored) {}
        return best;
    }

    /** 该域名的全部账号。 */
    public static java.util.List<String[]> all(Context c, String host) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            JSONArray arr = Prefs.getArray("passwords");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && host.equals(o.optString("h"))) {
                    out.add(new String[]{dec(c, o.optString("u")), dec(c, o.optString("p"))});
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 列表：{"host","user"} 数组，密码不出现在列表里。 */
    public static java.util.List<String[]> list(Context c) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            JSONArray arr = Prefs.getArray("passwords");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new String[]{o.optString("h"), dec(c, o.optString("u"))});
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void delete(Context c, String host) {
        try {
            JSONArray arr = Prefs.getArray("passwords");
            JSONArray fresh = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !host.equals(o.optString("h"))) fresh.put(o);
            }
            Prefs.putArray("passwords", fresh);
        } catch (Exception ignored) {}
    }
}
