package com.vane.browser.core;

import com.vane.browser.MainActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作表：界面上任何一个按键、任何一条手势，填的都是这里的 id。
 * 想加一个新功能：在 register() 里加一行，界面里立刻可以绑定它。
 */
public final class Actions {

    public interface Handler { void run(MainActivity a); }

    public static class Def {
        public final String id, label, glyph, group;
        public final Handler handler;

        Def(String id, String label, String glyph, String group, Handler h) {
            this.id = id;
            this.label = label;
            this.glyph = glyph;
            this.group = group;
            this.handler = h;
        }
    }

    private static final Map<String, Def> MAP = new LinkedHashMap<>();

    private Actions() {}

    private static void reg(String id, String label, String glyph, String group, Handler h) {
        MAP.put(id, new Def(id, label, glyph, group, h));
    }

    static {
        reg("back", "后退", "‹", "导航", a -> a.goBack());
        reg("forward", "前进", "›", "导航", a -> a.goForward());
        reg("home", "主页", "⌂", "导航", a -> a.goHome());
        reg("reload", "刷新", "⟳", "导航", a -> a.reload());
        reg("stop", "停止", "×", "导航", a -> a.stopLoading());
        reg("scrollTop", "回到顶部", "⇡", "导航", a -> a.scrollTop());
        reg("scrollBottom", "回到底部", "⇣", "导航", a -> a.scrollBottom());
        reg("backOrExit", "后退/退出", "‹", "导航", a -> a.backOrExit());

        reg("tabs", "标签页", "▣", "标签", a -> a.showTabs());
        reg("newTab", "新标签页", "＋", "标签", a -> a.newTab(a.ui.homePage, true));
        reg("closeTab", "关闭当前标签", "⊗", "标签", a -> a.closeCurrentTab());
        reg("tabLeft", "上一个标签", "◀", "标签", a -> a.switchTabDelta(-1));
        reg("tabRight", "下一个标签", "▶", "标签", a -> a.switchTabDelta(1));

        reg("menu", "菜单", "⋮", "界面", a -> a.showMenu());
        reg("focusAddress", "聚焦地址栏", "⌨", "界面", a -> a.focusAddress());
        reg("engineMenu", "切换搜索引擎", "⌕", "搜索", a -> a.showEngineMenu());
        reg("settings", "设置", "⚙", "界面", a -> a.openSettings());
        reg("uiEditor", "自定义界面", "✎", "界面", a -> a.openUiEditor());
        reg("toggleTopBar", "显示/隐藏顶栏", "▤", "界面", a -> a.toggleTopBar());
        reg("toggleBottomBar", "显示/隐藏底栏", "▥", "界面", a -> a.toggleBottomBar());
        reg("fullscreen", "全屏阅读", "⛶", "界面", a -> a.toggleFullscreenBars());

        reg("subDiag", "订阅诊断", "≡", "网络", a -> a.diagnoseSubs());
        reg("subText", "粘贴订阅内容导入", "⤓", "网络", a -> a.importSubFromText());
        reg("exportTheme", "导出主题", "⤓", "界面", a -> a.exportTheme());
        reg("importTheme", "导入主题", "⤒", "界面", a -> a.importTheme());
        reg("importPwCsv", "从 CSV 导入密码", "⤒", "隐私", a -> a.importPasswordsCsv());
        reg("extPlayer", "外置播放器", "▶", "媒体", a -> a.pickExternalPlayer());
        reg("testTranslate", "翻译源连通性测试", "文", "工具", a -> a.testTranslateSource());
        reg("autoTranslate", "本站自动翻译", "文", "工具", a -> a.toggleAutoTranslate());
        reg("pwSite", "本站不提示保存密码", "⚿", "隐私", a -> a.togglePasswordSite());
        reg("blockLinks", "拦截链接规则", "⚑", "网络", a -> a.editBlockLinks());
        reg("predictiveBack", "预测返回手势（说明）", "↩", "界面", a -> a.openPredictiveBackSetting());
        reg("ongoingNotice", "运行状态通知", "◉", "系统", a -> a.showOngoingNotice());
        reg("dlTasks", "下载任务（暂停/继续）", "⤓", "网络", a -> a.showDownloadTasks());
        reg("dlBuiltin", "用内建下载器下载本页", "⤓", "网络", a -> a.downloadBuiltin());
        reg("exports", "导出的文件", "⤒", "资料", a -> a.showExports());
        reg("printPage", "打印 / 存 PDF", "▤", "工具", a -> a.printPage());
        reg("openOther", "用其它应用打开", "↗", "工具", a -> a.openInOtherApp());
        reg("siteSettings", "网站设定", "⚙", "界面", a -> a.showSiteSettings());
        reg("homeCard", "收藏到主页", "★", "资料", a -> a.addBookmark());
        reg("userscripts", "用户脚本", "</>", "工具", a -> a.showUserScripts());
        reg("sync", "云同步（WebDAV）", "⇅", "系统", a -> a.showSync());
        reg("passwords", "密码管理", "⚿", "隐私", a -> a.showPasswordsGated());
        reg("library", "书签与历史", "★", "资料", a -> a.showBookmarks());
        reg("bookmarks", "书签", "★", "资料", a -> a.showBookmarks());
        reg("addBookmark", "添加书签", "✚", "资料", a -> a.addBookmark());
        reg("history", "历史记录", "◷", "资料", a -> a.showHistory());
        reg("downloads", "下载内容", "⤓", "资料", a -> a.showDownloadsPage());

        reg("share", "分享当前页", "⇪", "工具", a -> a.shareCurrent());
        reg("copyUrl", "复制网址", "⧉", "工具", a -> a.copyUrl());
        reg("find", "页内查找", "⌕", "工具", a -> a.findInPage());
        reg("desktop", "桌面版网站", "▭", "工具", a -> a.toggleDesktop());
        reg("night", "夜间模式", "☾", "工具", a -> a.toggleNight());
        reg("reader", "阅读模式", "≡", "工具", a -> a.readerMode());
        reg("zoomIn", "放大", "＋", "工具", a -> a.zoom(1));
        reg("zoomOut", "缩小", "－", "工具", a -> a.zoom(-1));
        reg("screenshot", "网页截图", "◉", "工具", a -> a.screenshot());
        reg("ua", "UA", "UA", "工具", a -> a.showUaMenu());
        reg("clearData", "清除浏览数据", "⌫", "工具", a -> a.clearBrowsingData());

        reg("video", "嗅探视频", "▶", "媒体", a -> a.showVideoList());
        reg("openPlayer", "用内置播放器打开", "▷", "媒体", a -> a.openPlayerForCurrent());
        reg("playerHelp", "播放器说明", "?", "媒体", a -> a.playerHelp());

        reg("proxy", "代理面板", "⇄", "网络", a -> a.openProxyPanel());
        reg("proxyToggle", "开关代理", "⏻", "网络", a -> a.toggleProxy());
        reg("subs", "订阅与节点", "☁", "网络", a -> a.openSubs());
        reg("proxyMode", "分流模式", "⇋", "网络", a -> a.showProxyModeMenu());
        reg("proxyLog", "代理日志", "▦", "网络", a -> a.showProxyLog());
        reg("adLevel", "拦截强度", "≡", "网络", a -> a.toggleAdLevel());
        reg("adRuleUpdate", "更新广告规则", "⤓", "网络", a -> a.updateAdRules());
        reg("adCheck", "拦截自检", "≡", "网络", a -> a.checkAdblock());
        reg("adStats", "广告拦截统计", "≡", "网络", a -> a.showAdStats());
        reg("siteAdblock", "本站不拦广告", "⚑", "网络", a -> a.toggleSiteAdblock());
        reg("adblock", "广告拦截开关", "⊘", "网络", a -> a.toggleAdblock());

        reg("scanQr", "扫描二维码", "▣", "工具", a -> a.scanQr());
        reg("addToDesktop", "添加到桌面", "⌂", "工具", a -> a.addToDesktop());
        reg("stopSpeak", "停止朗读", "◼", "工具", a -> a.stopSpeaking());
        reg("speak", "朗读网页", "◔", "工具", a -> a.speakPage());
        reg("ai", "AI", "✦", "工具", a -> a.openAi());
        reg("rotate", "屏幕方向", "⟲", "工具", a -> a.toggleOrientation());
        reg("markAdPick", "点选隐藏广告", "⚑", "网络", a -> a.markAdPick());
        reg("markAd", "标记广告", "⚑", "网络", a -> a.markAsAd());
        reg("fontSize", "字体大小", "A", "工具", a -> a.changeFontSize());
        reg("report", "反馈 / 举报", "⚐", "工具", a -> a.openFeedback());
        reg("shotViewport", "截图（当前屏幕）", "▤", "工具", a -> a.captureViewport());
        reg("pickFont", "自定义字体", "A", "界面", a -> a.pickFont());
        reg("longShot", "整页长截图", "▤", "工具", a -> a.captureLongScreenshot());
        reg("save", "保存网页", "⤓", "工具", a -> a.savePage());
        reg("offlineSave", "离线保存", "◎", "工具", a -> a.saveOffline());
        reg("offline", "离线页面", "▤", "工具", a -> a.showOfflinePages());
        reg("translateSource", "翻译源设置", "文", "工具", a -> a.pickTranslateSource());
        reg("translate", "翻译网页", "文", "工具", a -> a.translatePage());
        reg("source", "查看源码", "</>", "工具", a -> a.viewSource());
        reg("images", "不显示图片（省流量）", "◨", "工具", a -> a.toggleImages());
        reg("netlog", "网络日志", "≡", "网络", a -> a.showNetworkLog());
        reg("toolbox", "工具箱", "▦", "界面", a -> a.showTools());
        reg("backup", "备份数据", "⤓", "系统", a -> a.backupToFile());
        reg("restore", "恢复备份", "⤒", "系统", a -> a.restoreFromFile());
        reg("compat", "兼容性自检", "◎", "系统", a -> a.compatCheck());
        reg("checkUpdate", "检查更新", "⤓", "系统", a -> a.checkUpdate(true));
        reg("installApkPick", "安装本地 APK（选文件）", "⤒", "系统", a -> a.pickApkToInstall());
        reg("installApk", "安装新版本", "⇪", "系统", a -> a.installUpdate());
        reg("linkPlay", "用内置播放器播放", "▶", "链接菜单", a -> a.linkPlayInternal());
        reg("linkNewTab", "新标签打开", "＋", "链接菜单", a -> a.linkNewTab());
        reg("linkExternal", "交给系统浏览器", "↗", "链接菜单", a -> a.linkOpenExternal());
        reg("linkDownload", "下载链接", "⤓", "链接菜单", a -> a.linkDownload());
        reg("linkMarkAd", "把这个域名标记为广告", "⚑", "链接菜单", a -> a.linkMarkAd());
        reg("closeOthers", "关闭其它标签", "✕", "标签", a -> a.closeOtherTabs());
        reg("closeLeft", "关闭左侧标签", "←", "标签", a -> a.closeLeftTabs());
        reg("closeRight", "关闭右侧标签", "→", "标签", a -> a.closeRightTabs());
        reg("imageList", "看图模式（本页图片）", "▤", "媒体", a -> a.showImageList());
        reg("gameMode", "游戏模式（连按两次返回退出）", "◐", "界面", a -> a.toggleGameMode());
        reg("incognito", "隐身模式", "◐", "隐私", a -> a.toggleIncognito());
        reg("linkMenu", "链接菜单（长按页面链接）", "⋯", "界面", a -> a.showLinkMenuForCurrent());
        reg("exit", "退出", "⏻", "系统", a -> a.finish());
        reg("battery", "允许后台运行（防被杀）", "⏻", "系统", a -> a.openBatterySettings());
        reg("checkWebView", "WebView 版本自检", "≡", "系统", a -> a.checkWebView());
        reg("crashLog", "复制崩溃日志", "≡", "系统", a -> a.exportCrashLog());
        reg("about", "关于Vane", "i", "系统", a -> a.showAbout());
    }

    public static Def get(String id) {
        return MAP.get(id);
    }

    /** 旧版本存下来的动作名做个映射，避免老配置点了报「未定义」。 */
    public static String alias(String id) {
        if (id == null) return null;
        switch (id) {
            case "refresh": return "reload";
            case "desktopMode": case "toggleDesktop": case "pc": return "desktop";
            case "nightMode": case "toggleNight": case "dark": return "night";
            case "sniff": case "videoList": return "video";
            case "player": return "openPlayer";
            case "proxyPanel": return "proxy";
            case "closeTabNow": return "closeTab";
            case "findInPage": return "find";
            case "sourceCode": return "source";
            case "toolboxMenu": return "toolbox";
            default: return null;
        }
    }

    public static List<Def> all() {
        return new ArrayList<>(MAP.values());
    }

    public static List<Def> byGroup(String group) {
        List<Def> l = new ArrayList<>();
        for (Def d : MAP.values()) if (d.group.equals(group)) l.add(d);
        return l;
    }

    public static List<String> groups() {
        List<String> g = new ArrayList<>();
        for (Def d : MAP.values()) if (!g.contains(d.group)) g.add(d.group);
        return g;
    }

    public static String label(String id) {
        Def d = MAP.get(id);
        return d == null ? ("（未绑定：" + id + "）") : (d.glyph + "  " + d.label);
    }

    /** 只要名字，不带前面的符号（编辑器列表里用）。 */
    public static String title(String id) {
        Def d = MAP.get(id);
        return d == null ? ("（未绑定：" + id + "）") : d.label;
    }

    public static String glyph(String id) {
        Def d = MAP.get(id);
        return d == null ? "" : d.glyph;
    }
}
