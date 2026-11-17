package com.vane.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vane.browser.core.Actions;
import com.vane.browser.core.SearchEngine;
import com.vane.browser.core.SearchEngines;
import com.vane.browser.proxy.ProxyManager;
import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import java.util.ArrayList;
import java.util.List;

/** 所有设置项都在这里，改完立即生效（界面类改完回到主界面自动重建）。 */
public class SettingsActivity extends AppCompatActivity {

    private UiConfig ui;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ui = UiConfig.load();
        Theme.applyToWindow(this, ui);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Theme.bg(this, ui));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        setContentView(sc);
        build();
    }

    private void add(View v) {
        list.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void build() {
        if (Prefs.getBool("simple_mode", true)) buildSimple();
        else buildFull();
    }

    private void buildSimple() {
        list.removeAllViews();
        add(header("全部设置"));
        add(Widgets.row(this, ui, "← 回到精简设置", "只显示常用项", null, () -> {
            Prefs.putBool("simple_mode", true);
            build();
        }));

        add(Widgets.section(this, ui, "搜索"));
        add(Widgets.row(this, ui, "默认搜索引擎", SearchEngines.current().name, "长按地址栏也能切换", this::pickEngine));

        add(Widgets.section(this, ui, "外观"));
        add(Widgets.segment(this, ui, "主题", new String[]{"浅色", "深色", "纯黑", "自定义"},
                themeIndex(ui.theme), i -> {
                    String[] tv = {"light", "dark", "amoled", "custom"};
                    ui.theme = tv[i];
                    ui.save();
                    recreate();
                }));
        if ("custom".equals(ui.theme)) {
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "自定义背景色", ui.customBg, "点开调色板，滑条自己调", () ->
                    com.vane.browser.ui.ColorPicker.show(this, ui, ui.customBg, hex -> {
                        ui.customBg = hex;
                        ui.theme = "custom";
                        ui.save();
                        recreate();
                    })));
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "或者直接填色值", ui.customBg, "十六进制，如 #14202B", () ->
                    Widgets.input(this, ui, "背景色（如 #14202B）", ui.customBg, false, v -> {
                        String col = v.trim();
                        if (!col.startsWith("#")) col = "#" + col;
                        if (col.length() == 7) {
                            ui.customBg = col;
                            ui.theme = "custom";
                            ui.save();
                            recreate();
                        } else {
                            Widgets.toast(this, "格式不对，要像 #14202B 这样");
                        }
                    })));
            add(swatchRow());
        }
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "强调色", ui.accent, "点开调色板", () ->
                com.vane.browser.ui.ColorPicker.show(this, ui, ui.accent, hex -> {
                    ui.accent = hex;
                    ui.save();
                    recreate();
                })));
        add(Widgets.section(this, ui, "代理"));
        add(Widgets.row(this, ui, ProxyManager.get(this).status(), ProxyManager.get(this).kernelInfo(),
                "点进订阅与节点", () -> startActivity(new Intent(this, SubsActivity.class))));
        add(Widgets.divider(this, ui));
        add(Widgets.segment(this, ui, "分流模式", new String[]{"规则", "全局", "直连"},
                modeIndex(ProxyManager.get(this).rules.mode()), i -> {
                    String[] mv = {"rule", "global", "direct"};
                    ProxyManager.get(this).rules.setMode(mv[i]);
                    build();
                }));

        add(Widgets.section(this, ui, "深色白名单"));
        add(Widgets.row(this, ui, "添加当前网站到白名单",
                Prefs.get("dark_skip", "").isEmpty() ? "还没有白名单" : Prefs.get("dark_skip", ""),
                "白名单里的站不注入强制深色", () -> {
                    String cur = Prefs.get("dark_skip", "");
                    Widgets.input(this, ui, "域名（逗号分隔，如 weibo.com,taobao.com）", cur, true, v -> {
                        Prefs.put("dark_skip", v.trim());
                        build();
                    });
                }));
        add(Widgets.divider(this, ui));

        add(Widgets.section(this, ui, "标签与阅读"));
        add(Widgets.switchRow(this, ui, "工具栏放在地址栏下面", "变成两行工具栏（上：地址，下：按钮）",
                Prefs.getBool("bar_under_address", false), () -> {
                    Prefs.putBool("bar_under_address", !Prefs.getBool("bar_under_address", false));
                    build();
                    Widgets.toast(this, "重启界面后生效");
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "恢复上次未关闭的标签", "下次启动自动打开上次的页面",
                Prefs.getBool("restore_session", true), () -> {
                    Prefs.putBool("restore_session", !Prefs.getBool("restore_session", true));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "阅读模式二次确认", "点阅读模式时先问一次",
                Prefs.getBool("reader_confirm", false), () -> {
                    Prefs.putBool("reader_confirm", !Prefs.getBool("reader_confirm", false));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "翻译源",
                "baidu".equals(Prefs.get("translate_source", "baidu")) ? "百度翻译" : "谷歌翻译",
                "翻译网页时用哪个", () -> Widgets.pick(this, ui, "翻译源",
                java.util.Arrays.asList("谷歌翻译", "百度翻译"), i -> {
                    Prefs.put("translate_source", i == 1 ? "baidu" : "google");
                    build();
                })));
        add(Widgets.divider(this, ui));

        add(Widgets.section(this, ui, "高级行为"));
        add(Widgets.switchRow(this, ui, "按站点记忆字体", "调过字体的站点下次自动用同样大小",
                Prefs.getBool("site_memory", true), () -> {
                    Prefs.putBool("site_memory", !Prefs.getBool("site_memory", true));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "自定义字体", Prefs.getBool("font_custom", false) ? "已启用" : "未设置",
                "导入 ttf/otf，页面全部用它显示", () -> {
                    Intent i = new Intent(this, MainActivity.class);
                    i.putExtra("open_font", true);
                    startActivity(i);
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "阅读模式自定义 CSS",
                Prefs.get("reader_css", "").isEmpty() ? "未设置" : "已设置",
                "例如 body{max-width:720px;font-size:18px}", () ->
                Widgets.input(this, ui, "阅读模式追加 CSS", Prefs.get("reader_css", ""), true, v -> {
                    Prefs.put("reader_css", v);
                    build();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "顶部标签条", "在地址栏下面显示一排标签，跟电脑一样",
                Prefs.getBool("tab_strip", false), () -> {
                    Prefs.putBool("tab_strip", !Prefs.getBool("tab_strip", false));
                    build();
                    Widgets.toast(this, "重开界面后生效");
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "保存密码", "登录后询问是否保存，本机加密存放",
                Prefs.getBool("pw_save", true), () -> {
                    Prefs.putBool("pw_save", !Prefs.getBool("pw_save", true));
                    build();
                }));
        add(Widgets.divider(this, ui));
        for (String[] row : new String[][]{
                {"force_zoom", "强制缩放", "让页面按真实宽度渲染，字会变小"},
                {"block_third_cookie", "拦截第三方 Cookie", "减少跨站追踪，部分站可能登录异常"},
                {"block_popup", "拦截弹窗", "脚本弹出的新窗口一律拦下"},
                {"game_mode", "游戏模式", "连按两次返回键才退出"},
        }) {
            final String key = row[0];
            add(Widgets.switchRow(this, ui, row[1], row[2], Prefs.getBool(key, false), () -> {
                Prefs.putBool(key, !Prefs.getBool(key, false));
                build();
            }));
            add(Widgets.divider(this, ui));
        }

        add(Widgets.section(this, ui, "隐私"));
        add(Widgets.switchRow(this, ui, "隐身模式", "开启后浏览不留历史记录",
                Prefs.getBool("no_history", false), () -> {
                    Prefs.putBool("no_history", !Prefs.getBool("no_history", false));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "广告拦截", "本地规则，不联网",
                Prefs.getBool("adblock", true), () -> {
                    Prefs.putBool("adblock", !Prefs.getBool("adblock", true));
                    build();
                }));

        add(Widgets.section(this, ui, "高级"));
        add(Widgets.row(this, ui, "浏览器标识", uaName(), "影响网页排版和视频站播放器", () -> {
            String[] vals = {"default", "desktop", "iphone"};
            String cur = Prefs.get("ua_mode", "default");
            int idx = cur.equals("desktop") ? 1 : (cur.equals("iphone") ? 2 : 0);
            Widgets.pick(this, ui, "浏览器标识", java.util.Arrays.asList("默认", "桌面版", "iPhone"), i -> {
                Prefs.put("ua_mode", vals[i]);
                build();
            });
        }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "自定义按键与手势", "任何一个键都能改", null,
                () -> startActivity(new Intent(this, UiEditorActivity.class))));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "显示全部设置", "栏高、圆角、UA、规则、数据清除…", null, () -> {
            Prefs.putBool("simple_mode", false);
            build();
        }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "网页强制深色",
                "把白底网页也压成深色；会多一点渲染开销，觉得卡就关掉",
                Prefs.getBool("dark_web", false), () -> {
                    Prefs.putBool("dark_web", !Prefs.getBool("dark_web", false));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "默认浏览器",
                isDefaultBrowser() ? "已设为默认浏览器" : "未设 · 点这里去系统里选择",
                null, this::openDefaultBrowserSettings));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "隐私空间密码",
                Prefs.get("vault_pin_hash", "").isEmpty() ? "未设（用系统锁屏/指纹）" : "已设置（应用内 6 位密码）",
                null, this::setVaultPin));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "关于", "Vane " + appVersion() + "，无广告无统计", null, this::showAbout));

        View pad = new View(this);
        list.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 40)));
    }

    /** 当前是不是默认浏览器：拿一个 https 链接去问系统谁处理。 */
    private boolean isDefaultBrowser() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            android.content.pm.ResolveInfo ri = getPackageManager()
                    .resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null
                    && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /** 两条路：系统「默认应用」里选浏览器，或本应用的链接打开方式。App 自己不能直接改。 */
    private void openDefaultBrowserSettings() {
        Widgets.pick(this, ui, "设为默认浏览器", java.util.Arrays.asList(
                "去系统「默认应用」选浏览器（推荐）",
                "只改本应用的链接打开方式"), k -> {
            if (k == 0) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                    return;
                } catch (Exception ignored) {}
            }
            openAppLinkSettings();
        });
    }

    private void openAppLinkSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
            return;
        } catch (Exception ignored) {}
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            return;
        } catch (Exception ignored) {}
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Widgets.toast(this, "打不开系统设置，请到 设置 › 应用 › 默认应用 里改");
        }
    }

    /** 隐私空间密码：设一次以后进隐私空间就不用系统指纹了。 */
    private void setVaultPin() {
        boolean has = !Prefs.get("vault_pin_hash", "").isEmpty();
        Widgets.pick(this, ui, "隐私空间密码", java.util.Arrays.asList(
                has ? "改密码" : "设置密码", "清除密码（改回系统验证）"), i -> {
            if (i == 1) {
                Prefs.put("vault_pin_hash", "");
                Widgets.toast(this, "已清除，改回系统验证");
                build();
                return;
            }
            Widgets.input(this, ui, "输入 6 位数字密码", "", false, v -> {
                String pin = v == null ? "" : v.trim();
                if (pin.length() < 4) {
                    Widgets.toast(this, "至少 4 位");
                    return;
                }
                Prefs.put("vault_pin_hash",
                        com.vane.browser.VaultActivity.sha256(pin));
                Widgets.toast(this, "已设置");
                build();
            });
        });
    }

    /** 关于：应用自己的底部面板，不放调试数字，也不弹系统对话框。 */
    private void showAbout() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 20);
        box.setPadding(pad, 0, pad, Theme.dp(this, 18));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        android.widget.TextView title = Widgets.text(this, "关于Vane", 18, Theme.fg(this, ui));
        title.setPadding(0, 0, 0, Theme.dp(this, 14));
        box.addView(title);

        String[] lines = {
                "版本 " + appVersion(),
                "搜索引擎 " + SearchEngines.all().size() + " 个 · 可绑定动作 " + Actions.all().size() + " 个",
                "常见机场协议开箱可用（ss / vmess / vless / trojan / hysteria2 / tuic）",
                "无广告 · 无统计 · 数据都留在这台手机上",
        };
        for (String line : lines) {
            android.widget.TextView t = Widgets.text(this, line, 14, Theme.hint(this, ui));
            t.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
            box.addView(t);
        }

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        android.widget.TextView ok = Widgets.button(this, ui, "好", () -> {
            if (holder[0] != null) holder[0].dismiss();
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 18);
        box.addView(ok, lp);

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    private void buildFull() {
        list.removeAllViews();
        add(header("设置"));

        // 搜索
        add(Widgets.section(this, ui, "搜索"));
        add(Widgets.row(this, ui, "默认搜索引擎", SearchEngines.current().name, "长按地址栏也能切换", this::pickEngine));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "添加自定义搜索引擎", "名称 / 搜索地址模板", "模板里用 %s 代表关键词，例：https://example.com/s?q=%s", this::addEngine));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "新标签页地址", ui.homePage, null, () ->
                Widgets.input(this, ui, "新标签页地址", ui.homePage, false, s -> {
                    ui.homePage = s;
                    ui.save();
                    build();
                })));

        // 外观
        add(Widgets.section(this, ui, "外观"));
        add(Widgets.segment(this, ui, "主题", new String[]{"浅色", "深色", "纯黑", "自定义"},
                themeIndex(ui.theme), i -> {
                    String[] tv = {"light", "dark", "amoled", "custom"};
                    ui.theme = tv[i];
                    ui.save();
                    recreate();
                }));
        if ("custom".equals(ui.theme)) {
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "自定义背景色", ui.customBg, "点开调色板，滑条自己调", () ->
                    com.vane.browser.ui.ColorPicker.show(this, ui, ui.customBg, hex -> {
                        ui.customBg = hex;
                        ui.theme = "custom";
                        ui.save();
                        recreate();
                    })));
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "或者直接填色值", ui.customBg, "十六进制，如 #14202B", () ->
                    Widgets.input(this, ui, "背景色（如 #14202B）", ui.customBg, false, v -> {
                        String col = v.trim();
                        if (!col.startsWith("#")) col = "#" + col;
                        if (col.length() == 7) {
                            ui.customBg = col;
                            ui.theme = "custom";
                            ui.save();
                            recreate();
                        } else {
                            Widgets.toast(this, "格式不对，要像 #14202B 这样");
                        }
                    })));
            add(swatchRow());
        }
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "强调色", ui.accent, "点开调色板", () ->
                com.vane.browser.ui.ColorPicker.show(this, ui, ui.accent, hex -> {
                    ui.accent = hex;
                    ui.save();
                    recreate();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "顶栏高度", ui.topBarHeight + " dp", null, () ->
                Widgets.input(this, ui, "顶栏高度 dp", String.valueOf(ui.topBarHeight), false, s -> {
                    try { ui.topBarHeight = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
                    ui.save();
                    recreate();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "底栏高度", ui.bottomBarHeight + " dp", null, () ->
                Widgets.input(this, ui, "底栏高度 dp", String.valueOf(ui.bottomBarHeight), false, s -> {
                    try { ui.bottomBarHeight = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
                    ui.save();
                    recreate();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "地址栏圆角", ui.corner + " dp", null, () ->
                Widgets.input(this, ui, "圆角 dp", String.valueOf(ui.corner), false, s -> {
                    try { ui.corner = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
                    ui.save();
                    recreate();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "顶栏", "地址栏所在的那一条",
                ui.showTopBar, () -> {
            ui.showTopBar = !ui.showTopBar;
            ui.save();
            build();
        }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "底栏", "后退/前进/菜单那一条",
                ui.showBottomBar, () -> {
            ui.showBottomBar = !ui.showBottomBar;
            ui.save();
            build();
        }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "标签页上限", String.valueOf(ui.tabLimit), null, () ->
                Widgets.input(this, ui, "标签页上限", String.valueOf(ui.tabLimit), false, s -> {
                    try { ui.tabLimit = Math.max(1, Integer.parseInt(s.trim())); } catch (Exception ignored) {}
                    ui.save();
                    build();
                })));

        // 自定义界面
        add(Widgets.section(this, ui, "自定义（任何按键、任何手势）"));
        add(Widgets.row(this, ui, "按键与手势编辑器", Actions.all().size() + " 个可绑定动作",
                "增删按键、改图标、改绑定、排序、手势映射", () -> startActivity(new Intent(this, UiEditorActivity.class))));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "恢复默认界面", "会丢掉你现在的按键布局", null, () ->
                Widgets.confirm(this, ui, "恢复默认界面", "确定恢复？", () -> {
                    UiConfig d = UiConfig.defaults();
                    ui.items = d.items;
                    ui.gestures = d.gestures;
                    ui.save();
                    build();
                })));

        // 隐私与拦截
        add(Widgets.section(this, ui, "隐私与拦截"));
        add(Widgets.switchRow(this, ui, "隐身模式", "开启后浏览不留历史记录",
                Prefs.getBool("no_history", false), () -> {
                    Prefs.putBool("no_history", !Prefs.getBool("no_history", false));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "广告拦截", "本地规则，不联网、不上报",
                Prefs.getBool("adblock", true), () -> {
                    boolean b = !Prefs.getBool("adblock", true);
                    Prefs.putBool("adblock", b);
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.segment(this, ui, "应用跳转链接", new String[]{"忽略", "每次询问", "尝试打开"},
                appLinkIndex(), i -> {
                    String[] vals = {"ignore", "ask", "open"};
                    Prefs.put("app_link", vals[i]);
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "白名单域名", Prefs.get("adblock_white", ""), "逗号分隔，如 example.com", () ->
                Widgets.input(this, ui, "白名单域名", Prefs.get("adblock_white", ""), true, s -> {
                    Prefs.put("adblock_white", s);
                    build();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "清除浏览数据", "Cookie / 缓存 / 历史 / 书签", null, () ->
                Widgets.confirm(this, ui, "清除", "确定清除全部浏览数据？", () -> {
                    Prefs.putArray("history", new org.json.JSONArray());
                    Prefs.putArray("bookmarks", new org.json.JSONArray());
                    Widgets.toast(this, "已清除");
                })));

        // 代理
        add(Widgets.section(this, ui, "代理与分流"));
        add(Widgets.row(this, ui, "代理状态", ProxyManager.get(this).status(), "点开面板", () ->
                startActivity(new Intent(this, SubsActivity.class))));
        add(Widgets.divider(this, ui));
        add(Widgets.segment(this, ui, "分流模式", new String[]{"规则", "全局", "直连"},
                modeIndex(ProxyManager.get(this).rules.mode()), i -> {
                    String[] mv = {"rule", "global", "direct"};
                    ProxyManager.get(this).rules.setMode(mv[i]);
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "订阅与节点", "填机场订阅链接或手填节点", null,
                () -> startActivity(new Intent(this, SubsActivity.class))));

        // 播放器
        add(Widgets.section(this, ui, "播放器"));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "自动嗅探媒体",
                "默认关闭更稳；打开后每页都会注入脚本（费性能）",
                Prefs.getBool("auto_sniff", false), () -> {
                    Prefs.putBool("auto_sniff", !Prefs.getBool("auto_sniff", false));
                    build();
                }));

        add(Widgets.section(this, ui, "高级"));
        add(Widgets.segment(this, ui, "浏览器标识（影响网页排版与视频播放）",
                new String[]{"默认", "桌面版", "iPhone"}, uaIndex(), i -> {
                    String[] vals = {"default", "desktop", "iphone"};
                    Prefs.put("ua_mode", vals[i]);
                    build();
                }));
        add(Widgets.divider(this, ui));

        // 关于
        add(Widgets.section(this, ui, "主题与同步"));
        add(Widgets.row(this, ui, "导出位置",
                "app".equals(Prefs.get("export_target", "download")) ? "应用内部" : "/sdcard/Download/Vane/",
                "备份、书签、保存网页存到哪", () -> Widgets.pick(this, ui, "导出位置",
                java.util.Arrays.asList("/sdcard/Download/Vane/（推荐）", "应用内部"), i -> {
                    Prefs.put("export_target", i == 1 ? "app" : "download");
                    build();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.switchRow(this, ui, "自动同步", "启动时从 WebDAV 拉一次最新的",
                Prefs.getBool("sync_auto", false), () -> {
                    Prefs.putBool("sync_auto", !Prefs.getBool("sync_auto", false));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "导出主题包", "存成 json，可发给别人", null, () -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("do_theme_export", true);
            startActivity(i);
        }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "导入主题包", "从 json 文件恢复外观", null, () -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("do_theme_import", true);
            startActivity(i);
        }));

        add(Widgets.section(this, ui, "云同步"));
        add(Widgets.row(this, ui, "WebDAV 同步",
                Prefs.get("sync_url", "").isEmpty() ? "未设置" : "已配置",
                "填自己的服务器地址，跨设备同步书签/历史/设置", () -> {
                    Intent i = new Intent(this, MainActivity.class);
                    i.putExtra("open_sync", true);
                    startActivity(i);
                }));

        add(Widgets.section(this, ui, "反馈与联系"));
        add(Widgets.row(this, ui, "反馈邮箱", Prefs.get("feedback_mail", "").isEmpty()
                        ? "未设置" : Prefs.get("feedback_mail", ""),
                "「反馈」里的「用邮件发给我」发到这个地址", () ->
                Widgets.input(this, ui, "反馈邮箱", Prefs.get("feedback_mail", ""), false, v -> {
                    Prefs.put("feedback_mail", v.trim());
                    build();
                })));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "反馈网址", Prefs.get("feedback_url", "").isEmpty()
                        ? "未设置" : Prefs.get("feedback_url", ""),
                "有网页版反馈页就填这里", () ->
                Widgets.input(this, ui, "反馈网址", Prefs.get("feedback_url", ""), true, v -> {
                    Prefs.put("feedback_url", v.trim());
                    build();
                })));

        add(Widgets.section(this, ui, "代理与主页"));
        add(Widgets.switchRow(this, ui, "订阅自动更新", "启动时检查，超过一天就自动刷一次",
                Prefs.getBool("sub_auto", true), () -> {
                    Prefs.putBool("sub_auto", !Prefs.getBool("sub_auto", true));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "主页快捷入口",
                Prefs.getArray("home_cards").length() + " 个", "主页搜索框下面那几个方块", () -> {
                    Intent i = new Intent(this, MainActivity.class);
                    i.putExtra("edit_cards", true);
                    startActivity(i);
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "自定义拦截规则",
                Prefs.get("ad_custom", "").isEmpty() ? "未设置" : "已有规则",
                "一行一个域名，立即生效", () ->
                Widgets.input(this, ui, "要拦的域名（一行一个）", Prefs.get("ad_custom", ""), true, v -> {
                    Prefs.put("ad_custom", v);
                    build();
                })));

        add(Widgets.section(this, ui, "更新"));
        add(Widgets.switchRow(this, ui, "自动检查更新", "有新版本才提示，不会偷偷装",
                Prefs.getBool("update_auto", true), () -> {
                    Prefs.putBool("update_auto", !Prefs.getBool("update_auto", true));
                    build();
                }));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "更新地址", Prefs.get("update_manifest", "").isEmpty()
                        ? "未设置" : Prefs.get("update_manifest", ""),
                "一个 JSON 文件的网址，见 README", () ->
                Widgets.input(this, ui, "更新地址", Prefs.get("update_manifest", ""), true, v -> {
                    Prefs.put("update_manifest", v.trim());
                    build();
                })));

        add(Widgets.section(this, ui, "关于"));
        add(Widgets.row(this, ui, "默认浏览器",
                isDefaultBrowser() ? "已设为默认浏览器" : "未设 · 点这里去系统里选择",
                null, this::openDefaultBrowserSettings));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "隐私空间密码",
                Prefs.get("vault_pin_hash", "").isEmpty() ? "未设（用系统锁屏/指纹）" : "已设置（应用内密码）",
                null, this::setVaultPin));
        add(Widgets.divider(this, ui));
        add(Widgets.row(this, ui, "关于Vane", "版本 " + appVersion(), null, this::showAbout));

        View pad = new View(this);
        add(pad);
        ((LinearLayout.LayoutParams) pad.getLayoutParams()).height = Theme.dp(this, 40);
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private View header(String s) {
        TextView t = Widgets.text(this, s, 20, Theme.fg(this, ui));
        t.setPadding(Theme.dp(this, 16), Theme.dp(this, 18), Theme.dp(this, 16), Theme.dp(this, 4));
        return t;
    }

    private int themeIndex(String t) {
        switch (t) {
            case "dark": return 1;
            case "amoled": return 2;
            case "custom": return 3;
            default: return 0;
        }
    }

    /** 几个常用底色，点一下就用。 */
    private View swatchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 14));
        String[] colors = {"#101114", "#0E1B2A", "#14121F", "#0F1A14", "#1E1014", "#3B1C26", "#F3EFE7"};
        for (final String c : colors) {
            View v = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(android.graphics.Color.parseColor(c));
            g.setCornerRadius(Theme.dp(this, 10));
            g.setStroke(Theme.dp(this, 1), Theme.line(this, ui));
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(this, 40), 1f);
            lp.setMargins(0, 0, Theme.dp(this, 8), 0);
            v.setOnClickListener(x -> {
                ui.customBg = c;
                ui.theme = "custom";
                ui.save();
                recreate();
            });
            row.addView(v, lp);
        }
        return row;
    }

    private String themeName(String t) {
        switch (t) {
            case "light": return "浅色";
            case "dark": return "深色";
            case "amoled": return "纯黑";
            default: return "跟随系统";
        }
    }

    private int appLinkIndex() {
        String v = Prefs.get("app_link", "ignore");
        if (v.equals("ask")) return 1;
        if (v.equals("open")) return 2;
        return 0;
    }

    private int modeIndex(String mode) {
        if ("global".equals(mode)) return 1;
        if ("direct".equals(mode)) return 2;
        return 0;
    }

    private void pickTheme() {
        List<String> names = new ArrayList<>();
        names.add("跟随系统");
        names.add("浅色");
        names.add("深色");
        names.add("纯黑 (AMOLED)");
        String[] vals = {"auto", "light", "dark", "amoled"};
        Widgets.pick(this, ui, "主题", names, i -> {
            ui.theme = vals[i];
            ui.save();
            recreate();
        });
    }

    private void pickEngine() {
        final List<SearchEngine> all = SearchEngines.all();
        List<String> names = new ArrayList<>();
        for (SearchEngine e : all) names.add(e.group + " · " + e.name);
        Widgets.pick(this, ui, "默认搜索引擎", names, i -> {
            Prefs.put("engine", all.get(i).id);
            build();
        });
    }

    private String uaName() {
        String v = Prefs.get("ua_mode", "default");
        if (v.equals("desktop")) return "桌面版";
        if (v.equals("iphone")) return "iPhone";
        return "默认";
    }

    private int uaIndex() {
        String v = Prefs.get("ua_mode", "default");
        if (v.equals("desktop")) return 1;
        if (v.equals("iphone")) return 2;
        return 0;
    }

    private void addEngine() {
        Widgets.input(this, ui, "搜索引擎名称", "", false, name ->
                Widgets.input(this, ui, "搜索地址模板（含 %s）", "https://example.com/search?q=%s", false, tpl -> {
                    SearchEngines.addCustom(name, tpl);
                    Widgets.toast(this, "已添加：" + name);
                    build();
                }));
    }

    private void pickMode() {
        List<String> names = new ArrayList<>();
        names.add("规则分流（国内直连 / 境外代理）");
        names.add("全局代理");
        names.add("全局直连");
        String[] vals = {"rule", "global", "direct"};
        Widgets.pick(this, ui, "分流模式", names, i -> {
            ProxyManager.get(this).rules.setMode(vals[i]);
            build();
        });
    }
}
