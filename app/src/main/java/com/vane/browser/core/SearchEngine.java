package com.vane.browser.core;

import org.json.JSONObject;

/** 一个搜索引擎的定义。urlTemplate 里的 %s 会被替换成 URL 编码后的关键词。 */
public class SearchEngine {
    public String id;
    public String name;
    public String group;      // 国内 / 国际 / 隐私 / 垂直
    public String urlTemplate;
    public String suggestTemplate; // 可为空
    public String glyph;      // 图标里的字
    public String color = "#5B8CFF";  // 品牌色（做 LOGO 底）
    public String note = "";          // 一句话说明差别，比如「地区结果」
    public int rank = 500;            // 越小越靠前（选单按这个平铺排序，不分分组）
    public String icon = "";          // assets/icons 里的文件名，空则用品牌色字母块
    public boolean builtin = true;

    public SearchEngine(String id, String name, String group, String glyph,
                        String urlTemplate, String suggestTemplate) {
        this.id = id;
        this.name = name;
        this.group = group;
        this.glyph = glyph;
        this.urlTemplate = urlTemplate;
        this.suggestTemplate = suggestTemplate;
    }

    public String buildUrl(String query) {
        String q = query == null ? "" : query;
        try {
            q = java.net.URLEncoder.encode(q, "UTF-8");
        } catch (Exception ignored) {}
        return urlTemplate.contains("%s") ? urlTemplate.replace("%s", q) : urlTemplate + q;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("group", group);
            o.put("glyph", glyph);
            o.put("url", urlTemplate);
            o.put("suggest", suggestTemplate == null ? "" : suggestTemplate);
            o.put("color", color);
            o.put("icon", icon);
            o.put("note", note);
        } catch (Exception ignored) {}
        return o;
    }

    public static SearchEngine fromJson(JSONObject o) {
        SearchEngine e = new SearchEngine(
                o.optString("id", "custom"),
                o.optString("name", "自定义"),
                o.optString("group", "自定义"),
                o.optString("glyph", "S"),
                o.optString("url", "https://www.google.com/search?q=%s"),
                o.optString("suggest", ""));
        e.builtin = false;
        e.color = o.optString("color", "#5B8CFF");
        e.icon = o.optString("icon", "");
        e.note = o.optString("note", "");
        return e;
    }
}
