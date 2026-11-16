package com.vane.browser.core;

import android.content.Context;

import com.vane.browser.settings.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 用户脚本（Tampermonkey 风格的最小实现）：解析 @name/@match，按域名注入。 */
public final class UserScripts {

    public static class Script {
        public String name = "";
        public List<String> matches = new ArrayList<>();
        public String code = "";
        public String src = "";      // 安装来源 URL（用于检查更新）
        public boolean enabled = true;
    }

    private UserScripts() {}

    /** 从脚本源码里解析元数据。 */
    public static Script parse(String src) {
        Script s = new Script();
        s.code = src == null ? "" : src;
        if (src == null) return s;
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("// @name")) {
                s.name = t.replaceFirst("//\\s*@name\\s*", "").trim();
            } else if (t.startsWith("// @match") || t.startsWith("// @include")) {
                String v = t.replaceFirst("//\\s*@(match|include)\\s*", "").trim();
                if (!v.isEmpty()) s.matches.add(v);
            }
        }
        if (s.name.isEmpty()) s.name = "未命名脚本";
        if (s.matches.isEmpty()) s.matches.add("*://*/*");
        return s;
    }

    public static List<Script> all(Context c) {
        List<Script> out = new ArrayList<>();
        JSONArray arr = Prefs.getArray("userscripts");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Script s = new Script();
            s.name = o.optString("name", "脚本");
            s.src = o.optString("src", "");
            s.code = o.optString("code", "");
            s.enabled = o.optBoolean("on", true);
            JSONArray m = o.optJSONArray("matches");
            if (m != null) for (int k = 0; k < m.length(); k++) s.matches.add(m.optString(k));
            out.add(s);
        }
        return out;
    }

    public static void save(Context c, List<Script> list) {
        JSONArray arr = new JSONArray();
        for (Script s : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", s.name);
                o.put("src", s.src == null ? "" : s.src);
                o.put("code", s.code);
                o.put("on", s.enabled);
                JSONArray m = new JSONArray();
                for (String x : s.matches) m.put(x);
                o.put("matches", m);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        Prefs.putArray("userscripts", arr);
    }

    /** 给脚本准备的 GM_* 兼容层。 */
    private static String shim(String ns) {
        return "(function(){var NS=" + org.json.JSONObject.quote(ns) + ";"
                + "window.GM_getValue=function(k,d){var v=window.VaneBridge.gmGet(NS,k);"
                + "return (v===null||v===undefined||v==='')?d:v;};"
                + "window.GM_setValue=function(k,v){window.VaneBridge.gmSet(NS,k,String(v));};"
                + "window.GM_deleteValue=function(k){window.VaneBridge.gmDel(NS,k);};"
                + "window.GM_addStyle=function(css){var s=document.createElement('style');s.textContent=css;"
                + "(document.head||document.documentElement).appendChild(s);return s;};"
                + "window.GM_log=function(){};window.GM_info={scriptHandler:'Vane',version:'1.32'};"
                + "var seq=0,pend={};window.__gmXhr=function(id,status,text){var cb=pend[id];if(cb){delete pend[id];"
                + "try{cb({status:status,responseText:text,response:text});}catch(e){}}};"
                + "window.GM_xmlhttpRequest=function(o){var id=String(++seq);pend[id]=o.onload;"
                + "try{window.VaneBridge.gmXhr(NS,id,o.url,o.method||'GET',o.data||'','');}catch(e){}};"
                + "window.GM_openInTab=function(u){location.href=u;};"
                + "})();";
    }

    /** 该 URL 应该跑哪些脚本（拼接成一段 JS）。 */
    public static String codeFor(Context c, String url) {
        if (url == null || url.isEmpty()) return null;
        List<Script> list = all(c);
        StringBuilder sb = new StringBuilder();
        for (Script s : list) {
            if (!s.enabled || s.code.isEmpty()) continue;
            boolean hit = false;
            for (String m : s.matches) {
                if (match(m, url)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) continue;
            sb.append(shim(s.name));
            sb.append("(function(){try{\n").append(s.code).append("\n}catch(e){console.log('脚本出错 '+e);}})();\n");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 支持 *://host/* 与 * 通配。 */
    static boolean match(String pattern, String url) {
        if (pattern == null || pattern.isEmpty()) return false;
        if ("*".equals(pattern) || "*://*/*".equals(pattern)) return true;
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        try {
            return url.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }
}
