package com.vane.browser.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** 极简键值仓库：所有用户设置都存在一个 SP 文件里，值为字符串/JSON。 */
public final class Prefs {
    private static SharedPreferences sp;

    private Prefs() {}

    public static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("vane", Context.MODE_PRIVATE);
    }

    /**
     * 取字符串。同一个键可能被 putBool / putInt 存成了别的类型（比如 adblock），
     * 直接 getString 会抛 ClassCastException（云同步 payload 就因此整段失败），
     * 所以这里按实际类型转成字符串。
     */
    public static String get(String k, String def) {
        if (sp == null) return def;
        try {
            return sp.getString(k, def);
        } catch (ClassCastException e) {
            Object o = sp.getAll().get(k);
            return o == null ? def : String.valueOf(o);
        }
    }

    public static void put(String k, String v) {
        if (sp == null) return;
        sp.edit().putString(k, v).apply();
    }

    public static int getInt(String k, int def) {
        if (sp == null) return def;
        try {
            return sp.getInt(k, def);
        } catch (ClassCastException e) {
            try {
                return Integer.parseInt(get(k, String.valueOf(def)));
            } catch (Exception e2) {
                return def;
            }
        }
    }

    public static long getLong(String k, long def) {
        try {
            return Long.parseLong(get(k, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public static void putLong(String k, long v) {
        put(k, String.valueOf(v));
    }

    public static void putInt(String k, int v) {
        if (sp == null) return;
        sp.edit().putInt(k, v).apply();
    }

    public static boolean getBool(String k, boolean def) {
        if (sp == null) return def;
        try {
            return sp.getBoolean(k, def);
        } catch (ClassCastException e) {
            return "true".equalsIgnoreCase(get(k, String.valueOf(def)));
        }
    }

    public static void putBool(String k, boolean v) {
        if (sp == null) return;
        sp.edit().putBoolean(k, v).apply();
    }

    public static JSONObject getJson(String k) {
        try {
            String s = get(k, null);
            if (s == null || s.isEmpty()) return null;
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONArray getArray(String k) {
        try {
            String s = get(k, null);
            if (s == null || s.isEmpty()) return new JSONArray();
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void putJson(String k, JSONObject o) {
        put(k, o == null ? "" : o.toString());
    }

    public static void putArray(String k, JSONArray a) {
        put(k, a == null ? "[]" : a.toString());
    }

    public static void clearAll() {
        if (sp != null) sp.edit().clear().apply();
    }
}
