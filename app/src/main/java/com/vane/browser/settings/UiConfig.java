package com.vane.browser.settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 界面配置：每一个按键、每一条手势都是数据，不是写死的布局。
 * 想改哪个键，就改 items / gestures 里的对应条目。
 */
public class UiConfig {

    /** 一个可自定义的按键。width<=0 表示自动宽度（地址栏这种）。 */
    public static class Item {
        public String id;          // 唯一标识
        public String glyph;       // 显示的文字/符号（没配矢量图时用它）
        public String icon;        // 矢量图名，如 "back" → res/drawable/ic_back.xml；空表示用 glyph
        public String action;      // 点击执行的动作 id（见 Actions）
        public String longAction;  // 长按执行的动作 id，可为空
        public boolean visible = true;
        public int width = 46;     // dp
        public boolean flex = false; // 撑满剩余宽度（地址栏、标题）

        public Item(String id, String glyph, String action, String longAction, int width, boolean flex) {
            this.id = id;
            this.glyph = glyph;
            this.action = action;
            this.longAction = longAction;
            this.width = width;
            this.flex = flex;
        }

        public Item icon(String iconName) {
            this.icon = iconName;
            return this;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("glyph", glyph);
                o.put("icon", icon == null ? "" : icon);
                o.put("action", action);
                o.put("long", longAction == null ? "" : longAction);
                o.put("visible", visible);
                o.put("width", width);
                o.put("flex", flex);
            } catch (Exception ignored) {}
            return o;
        }

        public static Item fromJson(JSONObject o) {
            Item i = new Item(
                    o.optString("id", "btn"),
                    o.optString("glyph", "·"),
                    o.optString("action", ""),
                    o.optString("long", ""),
                    o.optInt("width", 46),
                    o.optBoolean("flex", false));
            i.visible = o.optBoolean("visible", true);
            i.icon = o.optString("icon", "");
            return i;
        }
    }

    public String theme = "auto";      // auto | light | dark | amoled
    public String accent = "#5B8CFF";
    public String customBg = "#101114";        // 自定义主题的背景色
    public int topBarHeight = 46;
    public int bottomBarHeight = 46;
    public int corner = 16;            // 地址栏圆角
    public boolean showTopBar = true;
    public boolean showBottomBar = true;
    public boolean translucentBars = true;
    public String homePage = "file:///android_asset/home.html";
    public int tabLimit = 20;

    public List<Item> items = new ArrayList<>();
    /** ⋮ 主菜单里显示的按钮（存动作 id）。 */
    public List<String> menuItems = new ArrayList<>();
    /** 长按网页链接弹出的菜单项（存动作 id）。 */
    public List<String> linkMenu = new ArrayList<>();
    public Map<String, String> gestures = new LinkedHashMap<>();

    public static UiConfig defaults() {
        UiConfig c = new UiConfig();
        c.items.add(new Item("address", "", "focusAddress", "engineMenu", 0, true));
        c.items.add(new Item("back", "‹", "back", "history", 0, false).icon("back"));
        c.items.add(new Item("forward", "›", "forward", "", 0, false).icon("forward"));
        c.items.add(new Item("home", "⌂", "home", "engineMenu", 0, false).icon("home"));
        c.items.add(new Item("tabs", "▣", "tabs", "newTab", 0, false).icon("tabs"));
        c.items.add(new Item("menu", "⋮", "menu", "settings", 0, false).icon("menu"));
        c.menuItems.add("settings");
        c.menuItems.add("night");
        c.menuItems.add("library");
        c.menuItems.add("downloads");
        c.menuItems.add("incognito");
        c.menuItems.add("share");
        c.menuItems.add("addBookmark");
        c.menuItems.add("desktop");
        c.menuItems.add("toolbox");
        c.linkMenu.add("linkNewTab");
        c.linkMenu.add("copyUrl");
        c.linkMenu.add("openPlayer");
        c.linkMenu.add("linkDownload");
        c.linkMenu.add("linkExternal");
        c.gestures.put("edge_left_swipe", "back");
        c.gestures.put("edge_right_swipe", "forward");
        c.gestures.put("toolbar_long_press", "engineMenu");
        c.gestures.put("address_long_press", "engineMenu");
        c.gestures.put("page_long_press_link", "linkMenu");
        c.gestures.put("pull_down", "reload");
        c.gestures.put("double_tap_top", "scrollTop");
        return c;
    }

    public static UiConfig load() {
        JSONObject o = Prefs.getJson("ui");
        if (o == null) return defaults();
        UiConfig c = new UiConfig();
        c.theme = o.optString("theme", c.theme);
        c.accent = o.optString("accent", c.accent);
        c.customBg = o.optString("customBg", c.customBg);
        c.topBarHeight = o.optInt("topBarHeight", c.topBarHeight);
        c.bottomBarHeight = o.optInt("bottomBarHeight", c.bottomBarHeight);
        c.corner = o.optInt("corner", c.corner);
        c.showTopBar = o.optBoolean("showTopBar", true);
        c.showBottomBar = o.optBoolean("showBottomBar", true);
        c.translucentBars = o.optBoolean("translucentBars", true);
        c.homePage = o.optString("homePage", c.homePage);
        c.tabLimit = o.optInt("tabLimit", 20);
        if ("auto".equals(c.theme) || "pink".equals(c.theme)) c.theme = "light";

        JSONArray arr = o.optJSONArray("items");
        if (arr != null && arr.length() > 0) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it != null) c.items.add(Item.fromJson(it));
            }
        } else {
            c.items = defaults().items;
        }
        // 老版本存下来的按键是「文字图标 + 固定宽度」，自动升级成矢量图标 + 等分
        for (Item it : c.items) {
            if (it.icon == null || it.icon.isEmpty()) {
                if ("back".equals(it.id) || "forward".equals(it.id) || "home".equals(it.id)
                        || "tabs".equals(it.id) || "menu".equals(it.id)) {
                    it.icon = it.id;
                    it.width = 0;
                }
            }
        }

        if (c.menuItems.isEmpty()) c.menuItems = defaults().menuItems;
        if (c.linkMenu.isEmpty()) c.linkMenu = defaults().linkMenu;
        JSONArray mi = o.optJSONArray("menuItems");
        if (mi != null && mi.length() > 0) {
            c.menuItems = new ArrayList<>();
            for (int i = 0; i < mi.length(); i++) c.menuItems.add(mi.optString(i));
        }
        JSONArray lm = o.optJSONArray("linkMenu");
        if (lm != null && lm.length() > 0) {
            c.linkMenu = new ArrayList<>();
            for (int i = 0; i < lm.length(); i++) c.linkMenu.add(lm.optString(i));
        }
        // 一次性给老配置补上「下载链接」：之前链接菜单里没有下载入口，用户只能长按后干瞪眼。
        // 注意必须写回 prefs，否则只是内存里有、重启就丢（1.81.2 就是只改了内存）。
        if (!Prefs.getBool("ui_link_dl_v2", false)) {
            if (!c.linkMenu.contains("linkDownload")) {
                int at = c.linkMenu.indexOf("linkExternal");
                if (at < 0) c.linkMenu.add("linkDownload");
                else c.linkMenu.add(at, "linkDownload");
                c.save();
            }
            Prefs.putBool("ui_link_dl_v2", true);
        }

        JSONObject g = o.optJSONObject("gestures");
        if (g != null) {
            for (java.util.Iterator<String> it = g.keys(); it.hasNext(); ) {
                String k = it.next();
                c.gestures.put(k, g.optString(k, ""));
            }
        }
        return c;
    }

    public void save() {
        JSONObject o = new JSONObject();
        try {
            o.put("theme", theme);
            o.put("accent", accent);
            o.put("customBg", customBg);
            o.put("topBarHeight", topBarHeight);
            o.put("bottomBarHeight", bottomBarHeight);
            o.put("corner", corner);
            o.put("showTopBar", showTopBar);
            o.put("showBottomBar", showBottomBar);
            o.put("translucentBars", translucentBars);
            o.put("homePage", homePage);
            o.put("tabLimit", tabLimit);
            JSONArray arr = new JSONArray();
            for (Item i : items) arr.put(i.toJson());
            o.put("items", arr);
            JSONArray mi = new JSONArray();
            for (String id : menuItems) mi.put(id);
            o.put("menuItems", mi);
            JSONArray lm = new JSONArray();
            for (String id : linkMenu) lm.put(id);
            o.put("linkMenu", lm);
            JSONObject g = new JSONObject();
            for (Map.Entry<String, String> e : gestures.entrySet()) g.put(e.getKey(), e.getValue());
            o.put("gestures", g);
        } catch (Exception ignored) {}
        Prefs.putJson("ui", o);
        Prefs.putInt("ui_rev", Prefs.getInt("ui_rev", 0) + 1);
    }
}
