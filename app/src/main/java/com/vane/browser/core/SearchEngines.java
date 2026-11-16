package com.vane.browser.core;

import com.vane.browser.settings.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置搜索引擎：一个品牌只留一个官网入口，不分地区版本。
 * 图标用各站自己的 favicon（assets/icons/<id>.png），取不到时用品牌色字母块兜底。
 */
public final class SearchEngines {

    private SearchEngines() {}

    private static List<SearchEngine> builtin;

    private static SearchEngine add(List<SearchEngine> l, String id, String name, String glyph,
                                    String url, String suggest, String color, String note) {
        SearchEngine e = new SearchEngine(id, name, "", glyph, url, suggest);
        e.icon = id + ".png";
        e.color = color;
        e.note = note == null ? "" : note;
        l.add(e);
        return e;
    }

    public static synchronized List<SearchEngine> builtin() {
        if (builtin != null) return builtin;
        List<SearchEngine> l = new ArrayList<>();

        add(l, "google", "Google", "G", "https://www.google.com/search?q=%s",
                "https://suggestqueries.google.com/complete/search?client=firefox&q=%s", "#4285F4", "");
        add(l, "baidu", "百度", "百", "https://www.baidu.com/s?wd=%s",
                "https://suggestion.baidu.com/su?wd=%s&p=3&cb=cb", "#2932E1", "");
        add(l, "bing", "Bing", "B", "https://www.bing.com/search?q=%s",
                "https://api.bing.osjson.aspx?query=%s", "#0F6CBD", "");
        add(l, "sogou", "搜狗", "搜", "https://www.sogou.com/web?query=%s",
                "https://www.sogou.com/suggnew/ajajjson?key=%s&type=web", "#FF6E00", "");
        add(l, "so360", "360 搜索", "3", "https://www.so.com/s?q=%s",
                null, "#00B358", "");
        add(l, "sm", "神马搜索", "神", "https://m.sm.cn/s?q=%s", null, "#FF5722", "阿里系，移动端");
        add(l, "quark", "夸克", "夸", "https://quark.sm.cn/s?q=%s", null, "#0A57FF", "界面干净");
        add(l, "toutiao", "头条搜索", "头", "https://so.toutiao.com/search?keyword=%s",
                null, "#F04142", "");
        add(l, "yandex", "Yandex", "Я", "https://yandex.com/search/?text=%s",
                "https://suggest.yandex.com/suggest-ff.cgi?part=%s", "#FC3F1D", "俄语区最强");
        add(l, "duckduckgo", "DuckDuckGo", "D", "https://duckduckgo.com/?q=%s&ia=web",
                "https://duckduckgo.com/ac/?q=%s&type=list", "#DE5833", "不追踪");
        add(l, "yahoo", "Yahoo", "Y", "https://search.yahoo.com/search?p=%s", null, "#6001D2", "");
        add(l, "brave", "Brave", "B", "https://search.brave.com/search?q=%s", null, "#FB542B", "独立索引");
        add(l, "startpage", "Startpage", "S", "https://www.startpage.com/sp/search?query=%s",
                null, "#6B7280", "用 Google 结果但不追踪");
        add(l, "ecosia", "Ecosia", "E", "https://www.ecosia.org/search?q=%s", null, "#1F8A4C", "种树");
        add(l, "wikipedia", "维基百科", "W", "https://zh.wikipedia.org/w/index.php?search=%s",
                null, "#54595D", "百科，不是全网搜索");
        add(l, "github", "GitHub", "{ }", "https://github.com/search?q=%s", null, "#24292F", "代码");
        add(l, "stackoverflow", "Stack Overflow", "SO", "https://stackoverflow.com/search?q=%s",
                null, "#F48024", "问答");
        add(l, "mdn", "MDN", "MDN", "https://developer.mozilla.org/zh-CN/search?q=%s",
                null, "#4B5563", "前端文档");
        add(l, "npm", "npm", "npm", "https://www.npmjs.com/search?q=%s", null, "#CB3837", "包");
        add(l, "pypi", "PyPI", "py", "https://pypi.org/search/?q=%s", null, "#3775A9", "包");
        add(l, "arxiv", "arXiv", "ax", "https://arxiv.org/abs/%s", null, "#B31B1B", "论文编号");
        add(l, "google_scholar", "Google 学术", "学", "https://scholar.google.com/scholar?q=%s",
                null, "#4285F4", "论文");

        java.util.Map<String, String> NOTES = new java.util.LinkedHashMap<>();
        NOTES.put("google", "结果最全，全球第一");
        NOTES.put("baidu", "中文内容最全");
        NOTES.put("bing", "微软家，英文结果好");
        NOTES.put("sogou", "能搜微信公众号和知乎");
        NOTES.put("so360", "国内老牌，带安全拦截");
        NOTES.put("sm", "阿里系，移动端为主");
        NOTES.put("quark", "无广告，界面最干净");
        NOTES.put("toutiao", "字节系，短视频结果多");
        NOTES.put("yandex", "俄语区最强");
        NOTES.put("duckduckgo", "不追踪、不记录");
        NOTES.put("yahoo", "老牌门户搜索");
        NOTES.put("brave", "独立索引，不带广告");
        NOTES.put("startpage", "用 Google 结果但不追踪");
        NOTES.put("ecosia", "搜一次种一棵树");
        NOTES.put("wikipedia", "百科，不是全网搜索");
        NOTES.put("github", "代码仓库搜索");
        NOTES.put("stackoverflow", "编程问答");
        NOTES.put("mdn", "前端文档");
        NOTES.put("npm", "JS 包");
        NOTES.put("pypi", "Python 包");
        NOTES.put("arxiv", "学术论文");
        NOTES.put("google_scholar", "学术文献，偏引用");
        for (SearchEngine e : l) {
            if (NOTES.containsKey(e.id)) e.note = NOTES.get(e.id);
        }
        // 用户指定顺序：Google / Bing / Yandex / DuckDuckGo / Brave / Yahoo / 百度 / 夸克，其余保持原顺序
        final java.util.List<String> ORDER = java.util.Arrays.asList(
                "google", "bing", "yandex", "duckduckgo", "brave", "yahoo", "baidu", "quark");
        l.sort((a, b) -> {
            int ra = ORDER.indexOf(a.id);
            int rb = ORDER.indexOf(b.id);
            if (ra < 0) ra = 1000;
            if (rb < 0) rb = 1000;
            return Integer.compare(ra, rb);
        });

        builtin = l;
        return builtin;
    }

    /** 内置 + 自定义（自定义存在 prefs 的 engines 数组里，排在最后）。 */
    public static List<SearchEngine> all() {
        List<SearchEngine> l = new ArrayList<>(builtin());
        JSONArray arr = Prefs.getArray("engines_custom");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) l.add(SearchEngine.fromJson(o));
        }
        return l;
    }

    public static Map<String, List<SearchEngine>> grouped() {
        Map<String, List<SearchEngine>> m = new LinkedHashMap<>();
        for (SearchEngine e : all()) {
            List<SearchEngine> g = m.get(e.group);
            if (g == null) {
                g = new ArrayList<>();
                m.put(e.group, g);
            }
            g.add(e);
        }
        return m;
    }

    public static SearchEngine byId(String id) {
        for (SearchEngine e : all()) if (e.id.equals(id)) return e;
        return builtin().get(0);
    }

    public static SearchEngine current() {
        return byId(Prefs.get("engine", "bing"));
    }

    /** 地址栏输入：网址就直接打开，否则交给当前引擎搜。 */
    public static String url(String input) {
        if (input == null) return current().buildUrl("");
        String s = input.trim();
        if (s.isEmpty()) return current().buildUrl("");
        if (s.matches("^(https?|file|ftp|vane)://.*")) return s;
        if (s.matches("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$") && !s.contains(" ")) return "https://" + s;
        if (s.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$")) return "http://" + s;
        return current().buildUrl(s);
    }

    public static void addCustom(String name, String urlTemplate) {
        JSONArray arr = Prefs.getArray("engines_custom");
        JSONObject o = new JSONObject();
        try {
            o.put("id", "u" + System.currentTimeMillis());
            o.put("name", name);
            o.put("group", "自定义");
            o.put("glyph", name.isEmpty() ? "S" : name.substring(0, 1));
            o.put("url", urlTemplate);
            o.put("suggest", "");
            arr.put(o);
        } catch (Exception ignored) {}
        Prefs.putArray("engines_custom", arr);
    }

    public static void removeCustom(String id) {
        JSONArray arr = Prefs.getArray("engines_custom");
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) out.put(o);
        }
        Prefs.putArray("engines_custom", out);
    }
}
