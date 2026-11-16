package com.vane.browser.ui;

import com.vane.browser.R;

/** 动作 → 线性图标。菜单、编辑器、工具栏共用一套；这里逐条登记，不留“三个点”兜底。 */
public final class ActionIcons {

    private ActionIcons() {}

    public static int res(String actionId) {
        if (actionId == null) return R.drawable.ic_menu;
        switch (actionId) {
            // 导航
            case "back": case "backOrExit": return R.drawable.ic_back;
            case "forward": return R.drawable.ic_forward;
            case "home": return R.drawable.ic_home;
            case "reload": return R.drawable.ic_reload;
            case "stop": return R.drawable.ic_stop;
            case "scrollTop": return R.drawable.ic_top;
            case "scrollBottom": return R.drawable.ic_bottom;
            case "tabs": return R.drawable.ic_tabs;
            case "newTab": case "linkNewTab": return R.drawable.ic_new_tab;
            case "closeTab": return R.drawable.ic_close;
            case "tabLeft": return R.drawable.ic_tab_left;
            case "tabRight": return R.drawable.ic_tab_right;
            case "menu": return R.drawable.ic_menu;
            case "focusAddress": return R.drawable.ic_address;
            case "engineMenu": return R.drawable.ic_engine;
            case "settings": return R.drawable.ic_settings;
            case "uiEditor": return R.drawable.ic_sliders;
            case "toggleTopBar": return R.drawable.ic_bar_top;
            case "toggleBottomBar": return R.drawable.ic_bar_bottom;
            case "fullscreen": return R.drawable.ic_full;
            // 资料
            case "library": return R.drawable.ic_book;
            case "bookmarks": return R.drawable.ic_star;
            case "addBookmark": return R.drawable.ic_star_plus;
            case "history": return R.drawable.ic_clock;
            case "downloads": return R.drawable.ic_down;
            case "share": return R.drawable.ic_share_nodes;
            case "copyUrl": return R.drawable.ic_copy;
            // 页面
            case "find": return R.drawable.ic_search;
            case "desktop": return R.drawable.ic_monitor;
            case "night": return R.drawable.ic_night;
            case "reader": return R.drawable.ic_book;
            case "zoomIn": return R.drawable.ic_zoom_in;
            case "zoomOut": return R.drawable.ic_zoom_out;
            case "screenshot": case "longShot": return R.drawable.ic_camera;
            case "ua": return R.drawable.ic_user;
            case "clearData": return R.drawable.ic_trash;
            // 媒体
            case "video": return R.drawable.ic_sniff;
            case "openPlayer": return R.drawable.ic_play;
            case "linkPlay": return R.drawable.ic_play;
            case "playerHelp": return R.drawable.ic_help;
            case "speak": return R.drawable.ic_speak;
            case "stopSpeak": return R.drawable.ic_stop;
            // 网络
            case "proxy": return R.drawable.ic_proxy;
            case "proxyToggle": return R.drawable.ic_shield;
            case "subs": return R.drawable.ic_list;
            case "proxyMode": return R.drawable.ic_fork;
            case "proxyLog": return R.drawable.ic_log;
            case "adCheck": return R.drawable.ic_shield;
            case "adLevel": return R.drawable.ic_shield;
            case "adRuleUpdate": return R.drawable.ic_down;
            case "adStats": return R.drawable.ic_log;
            case "siteAdblock": return R.drawable.ic_adblock;
            case "adblock": return R.drawable.ic_adblock;
            case "markAd": case "markAdPick": case "linkMarkAd": return R.drawable.ic_flag;
            case "netlog": return R.drawable.ic_network;
            // 工具
            case "scanQr": return R.drawable.ic_scan;
            case "addToDesktop": return R.drawable.ic_add_home;
            case "ai": return R.drawable.ic_ai;
            case "rotate": return R.drawable.ic_rotate;
            case "fontSize": return R.drawable.ic_font;
            case "pickFont": return R.drawable.ic_font;
            case "shotViewport": return R.drawable.ic_camera;
            case "report": return R.drawable.ic_report;
            case "extPlayer": return R.drawable.ic_play;
            case "testTranslate": return R.drawable.ic_translate;
            case "autoTranslate": return R.drawable.ic_translate;
            case "pwSite": return R.drawable.ic_user;
            case "blockLinks": return R.drawable.ic_flag;
            case "predictiveBack": return R.drawable.ic_back;
            case "ongoingNotice": return R.drawable.ic_network;
            case "dlTasks": return R.drawable.ic_down;
            case "dlBuiltin": return R.drawable.ic_down;
            case "exports": return R.drawable.ic_up;
            case "printPage": return R.drawable.ic_log;
            case "openOther": return R.drawable.ic_external;
            case "siteSettings": return R.drawable.ic_settings;
            case "homeCard": return R.drawable.ic_star_plus;
            case "save": return R.drawable.ic_save;
            case "offlineSave": return R.drawable.ic_offline;
            case "offline": return R.drawable.ic_book;
            case "translate": return R.drawable.ic_translate;
            case "source": return R.drawable.ic_code;
            case "userscripts": return R.drawable.ic_code;
            case "images": return R.drawable.ic_image_off;
            case "toolbox": return R.drawable.ic_toolbox;
            case "sync": return R.drawable.ic_network;
            case "subDiag": return R.drawable.ic_log;
            case "subText": return R.drawable.ic_fork;
            case "installApkPick": return R.drawable.ic_up;
            case "exportTheme": return R.drawable.ic_up;
            case "importTheme": return R.drawable.ic_down;
            case "importPwCsv": return R.drawable.ic_down;
            case "backup": return R.drawable.ic_up;
            case "restore": return R.drawable.ic_down;
            case "checkUpdate": return R.drawable.ic_reload;
            case "installApk": return R.drawable.ic_save;
            case "linkExternal": return R.drawable.ic_external;
            case "linkDownload": return R.drawable.ic_down;
            // 隐私
            case "passwords": return R.drawable.ic_user;
            case "translateSource": return R.drawable.ic_translate;
            case "compat": return R.drawable.ic_help;
            case "closeOthers": return R.drawable.ic_close;
            case "closeLeft": return R.drawable.ic_tab_left;
            case "closeRight": return R.drawable.ic_tab_right;
            case "imageList": return R.drawable.ic_sniff;
            case "gameMode": return R.drawable.ic_game;
            case "linkMenu": return R.drawable.ic_list;
            case "exit": return R.drawable.ic_exit;
            case "battery": return R.drawable.ic_settings;
            case "checkWebView": return R.drawable.ic_help;
            case "crashLog": return R.drawable.ic_log;
            case "about": return R.drawable.ic_help;
            case "incognito": return R.drawable.ic_incognito;
            default: return R.drawable.ic_menu;
        }
    }
}
