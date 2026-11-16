package com.vane.browser.core;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 元素级去广告：按域名索引的 CSS 选择器，命中后注入页面隐藏，并持续清理新增节点。 */
public final class Cosmetic {

    private static final Map<String, List<String>> RULES = new HashMap<>();
    private static boolean loaded = false;
    private static final java.util.Map<String, String> CACHE = new java.util.HashMap<>();

    private Cosmetic() {}

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("cosmetic.txt")), 8192)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("##")) continue;
                int i = line.indexOf("##");
                String domain = line.substring(0, i).trim().toLowerCase();
                String sel = line.substring(i + 2).trim();
                if (domain.isEmpty() || sel.isEmpty()) continue;
                RULES.computeIfAbsent(domain, k -> new ArrayList<>()).add(sel);
            }
        } catch (Exception ignored) {}
        // 用户自定义（点选隐藏 / 长按隐藏）一并纳入
        String custom = com.vane.browser.settings.Prefs.get("cosmetic_custom", "");
        for (String line : custom.split("\n")) {
            if (!line.contains("##")) continue;
            int i = line.indexOf("##");
            RULES.computeIfAbsent(line.substring(0, i).trim().toLowerCase(), k -> new ArrayList<>())
                    .add(line.substring(i + 2).trim());
        }
    }

    /** 该 host 命中多少条选择器。 */
    public static List<String> selectorsFor(String host) {
        List<String> out = new ArrayList<>();
        if (host == null || host.isEmpty()) return out;
        String h = host.toLowerCase();
        while (true) {
            List<String> l = RULES.get(h);
            if (l != null) out.addAll(l);
            int dot = h.indexOf('.');
            if (dot < 0) break;
            h = h.substring(dot + 1);
        }
        return out;
    }

    public static void addCustom(String host, String selector) {
        if (host == null || selector == null || selector.isEmpty()) return;
        String line = host + "##" + selector;
        String cur = com.vane.browser.settings.Prefs.get("cosmetic_custom", "");
        if (cur.contains(line)) return;
        com.vane.browser.settings.Prefs.put("cosmetic_custom",
                (cur.isEmpty() ? line : cur + "\n" + line));
        RULES.computeIfAbsent(host.toLowerCase(), k -> new ArrayList<>()).add(selector);
    }

    public static int ruleCount() {
        int n = 0;
        for (List<String> l : RULES.values()) n += l.size();
        return n;
    }

    /** 生成注入脚本（按域名缓存，避免每页重复拼 CSS）。 */
    public static String jsFor(String host) {
        if (host == null) return null;
        if (CACHE.containsKey(host)) return CACHE.get(host);
        String js = buildJs(host);
        CACHE.put(host, js);
        return js;
    }

    private static String buildJs(String host) {
        List<String> sels = selectorsFor(host);
        if (sels.isEmpty()) return null;
        StringBuilder css = new StringBuilder();
        for (String s : sels) css.append(s).append("{display:none !important}\n");
        return "(function(){if(window.__vaneCos)return;window.__vaneCos=1;"
                + "var st=document.createElement('style');st.id='__vane_cos';"
                + "st.textContent=" + org.json.JSONObject.quote(css.toString()) + ";"
                + "(document.head||document.documentElement).appendChild(st);"
                + "var last=0;var mo=new MutationObserver(function(){var n=Date.now();"
                + "if(n-last<400)return;last=n;"
                + "if(!document.getElementById('__vane_cos'))"
                + "(document.head||document.documentElement).appendChild(st);});"
                + "mo.observe(document.documentElement,{childList:true,subtree:true});"
                + "setTimeout(function(){try{mo.disconnect();}catch(e){}},20000);"
                + "})();";
    }
}
