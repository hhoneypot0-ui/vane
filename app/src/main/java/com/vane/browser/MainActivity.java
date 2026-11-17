package com.vane.browser;

import android.content.ClipData;
import android.text.TextUtils;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.vane.browser.core.Actions;
import com.vane.browser.core.AdBlocker;
import com.vane.browser.core.BrowserTab;
import com.vane.browser.core.Downloads;
import com.vane.browser.core.JsBridge;
import com.vane.browser.core.SearchEngine;
import com.vane.browser.core.SearchEngines;
import com.vane.browser.core.VideoSniffer;
import com.vane.browser.proxy.ProxyManager;
import com.vane.browser.proxy.RuleEngine;
import com.vane.browser.proxy.WebViewProxyBridge;
import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_SETTINGS = 1002;
    private static final int REQ_SCAN = 1003;
    private static final int REQ_PICK_APK = 1004;
    private static final int REQ_TREE = 1005;
    private static final int REQ_RESTORE = 1006;
    private static final int REQ_FONT = 1007;
    private static final int REQ_UNLOCK = 1008;
    private static final int REQ_PWCSV = 1009;
    private static final int REQ_THEME = 1010;
    private static final int REQ_APK = 1011;
    private static final int REQ_INSTALL_PERM = 1012;

    public UiConfig ui;
    public AdBlocker blocker;
    public final List<BrowserTab> tabs = new ArrayList<>();
    public int current = -1;
    public boolean desktopMode = false;
    public boolean nightMode = false;

    /** 登录表单的提交事件与按钮点击事件会连着触发两次，用它去重，只弹一次保存框。 */
    private String lastPwSig = "";
    /** 从隐私空间点开的地址：这一条不进历史。 */
    private String privateUrl = null;
    private long lastPwAt = 0;

    private LinearLayout root;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private FrameLayout tabHost;
    private FrameLayout fullscreenHost;
    private TextView fsExit;
    private EditText address;
    private TextView tabCounter;
    private TextView engineIcon;
    private TextView backBtn, forwardBtn, cancelEdit;
    private android.widget.ImageView schemeIcon;
    private android.widget.ImageView backIcon, forwardIcon;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int uiRev = 0;
    private boolean addressEditing = false;
    private View undoBar;
    private android.widget.ImageView readerBtn;
    private LinearLayout tabStrip;
    /** 内置页每行对应的域名（用于加载图标，索引与页面 id 一致）。 */
    private final java.util.List<String> lastIconHosts = new ArrayList<>();
    /** 最近关掉的标签，最多留 10 个，用来「撤销关闭」。 */
    private final java.util.List<String[]> recentlyClosed = new ArrayList<>();
    private boolean updateCheckedOnce = false;
    private String lastLinkUrl = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        // 启动背景万一解析失败（自定义 ROM 会这样），立即回退到纯色，不让它在 onCreate 崩
        try {
            getWindow().getDecorView().setBackgroundColor(
                    android.graphics.Color.parseColor("#0E1013"));
        } catch (Throwable ignored) {}
        ui = UiConfig.load();
        uiRev = Prefs.getInt("ui_rev", 0);
        if (!Prefs.getBool("menu_v4", false)) {
            ui.menuItems = new ArrayList<>(java.util.Arrays.asList(
                    "night", "library", "downloads", "incognito", "share",
                    "addBookmark", "desktop", "toolbox", "settings",
                    "find", "save", "translate", "source", "fullscreen",
                    "images", "video", "reload", "printPage", "openOther",
                    "scanQr", "addToDesktop", "speak", "ai", "rotate",
                    "adblock", "markAd", "fontSize", "report"));
            ui.save();
            Prefs.putBool("menu_v4", true);
        }
        if (!Prefs.getBool("menu_v3", false)) {
            ui.menuItems = new ArrayList<>(java.util.Arrays.asList(
                    "night", "bookmarks", "history", "downloads", "incognito",
                    "share", "addBookmark", "desktop", "toolbox", "settings",
                    "find", "save", "translate", "source", "fullscreen",
                    "images", "video", "reload", "printPage", "openOther",
                    "scanQr", "addToDesktop", "speak", "ai", "rotate",
                    "adblock", "markAd", "fontSize", "report"));
            ui.save();
            Prefs.putBool("menu_v3", true);
        }
        if (!Prefs.getBool("menu_v2", false)) {
            ui.menuItems = new ArrayList<>(java.util.Arrays.asList(
                    "night", "bookmarks", "history", "downloads", "incognito",
                    "share", "addBookmark", "desktop", "toolbox", "settings",
                    "find", "save", "offline", "translate", "source",
                    "fullscreen", "images", "video", "ua", "netlog",
                    "scanQr", "addToDesktop", "speak", "ai", "rotate",
                    "adblock", "markAd", "fontSize", "report"));
            ui.save();
            Prefs.putBool("menu_v2", true);
        }
        ensureMenu();
        if (ui.menuItems.contains("bookmarks") || ui.menuItems.contains("history")) {
            int at = Math.min(ui.menuItems.indexOf("bookmarks"), ui.menuItems.indexOf("history"));
            ui.menuItems.remove("bookmarks");
            ui.menuItems.remove("history");
            if (at < 0) at = 0;
            ui.menuItems.add(Math.min(at, ui.menuItems.size()), "library");   // 书签和历史合成一格
            ui.save();
        }
        if (ui.menuItems.contains("home") || ui.menuItems.contains("homeCards")
                || ui.menuItems.indexOf("settings") != 0) {
            ui.menuItems.remove("home");
            ui.menuItems.remove("homeCards");                 // 主页方块改成书签渲染，这一格没用
            ui.menuItems.remove("settings");
            ui.menuItems.add(0, "settings");
            ui.save();
        }
        if (!ui.menuItems.contains("settings")) {
            ui.menuItems.add("settings");     // 设置放回主菜单，别埋进工具箱
            ui.save();
        }
        nightMode = Theme.isDark(this, ui);
        blocker = new AdBlocker(this);
        Theme.applyToWindow(this, ui);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.bg(this, ui));
        root.setFitsSystemWindows(true);

        tabHost = new FrameLayout(this);
        fullscreenHost = new FrameLayout(this);
        fullscreenHost.setVisibility(View.GONE);
        fullscreenHost.setBackgroundColor(Color.BLACK);

        buildChrome();
        setContentView(root);
        if (!ui.showTopBar) {
            showFsChip();                     // 上次停在全屏，给个退出口
            applyImmersive(true);
            addTopSwipe();
        } else {
            applyImmersive(false);            // 还原系统栏，避免留下黑边
            hideFsChip();
            removeTopSwipe();
        }
        newTab(ui.homePage, true);

        // 开了自动同步就先拉一次
        if (Prefs.getBool("sync_auto", false) && !Prefs.get("sync_url", "").isEmpty()) {
            root.postDelayed(() -> new Thread(() -> {
                // 启动自动同步只拉「服务器确实更新」的：本机较新就跳过，别把本机改动悄悄冲掉
                long remote = com.vane.browser.core.Sync.remoteTime(this);
                long local = Prefs.getLong("sync_local_time", 0);
                final String stamp = " · " + new java.text.SimpleDateFormat("HH:mm:ss",
                        java.util.Locale.CHINA).format(new java.util.Date());
                if (remote <= 0) {
                    Prefs.put("sync_last", "启动自动同步跳过（读不到服务器时间）" + stamp);
                    return;
                }
                if (remote <= local) {
                    Prefs.put("sync_last", "启动自动同步跳过（本机较新）" + stamp);
                    return;
                }
                com.vane.browser.core.Sync.download(this, (ok, msg) -> {
                    Prefs.put("sync_last", (ok ? "启动自动同步 OK " : "启动自动同步失败 ") + msg + stamp);
                    if (ok) runOnUiThread(this::rebuildChrome);
                });
            }, "sync-auto").start(), 1500);
        }

        // 默认搜索引擎：必应（一次性迁移）
        if (!Prefs.getBool("engine_default_bing", false)) {
            Prefs.put("engine", "bing");
            Prefs.putBool("engine_default_bing", true);
        }

        // 导出默认落到 手机存储/下载/Vane
        if (!Prefs.getBool("export_default_v1", false)) {
            Prefs.put("export_target", "download");
            Prefs.putBool("export_default_v1", true);
        }

        // 启动时后台预热最近访问过的域名（最多 3 个）
        new Thread(() -> {
            try {
                java.util.List<String> urls = new ArrayList<>();
                org.json.JSONArray hs = Prefs.getArray("history");
                for (int i = hs.length() - 1; i >= 0 && urls.size() < 3; i--) {
                    org.json.JSONObject o = hs.optJSONObject(i);
                    if (o != null) urls.add(o.optString("url", ""));
                }
                com.vane.browser.core.DnsWarm.warmUrls(urls);
            } catch (Throwable ignored) {}
        }, "dns-boot").start();
        // 预热延后，避免和首屏抢网络栈

        // 图片默认显示：之前被打开过的「无图模式」在这里复位一次
        if (!Prefs.getBool("img_fix_v1", false)) {
            Prefs.putBool("no_images", false);
            Prefs.putBool("img_fix_v1", true);
        }
        for (BrowserTab tb : tabs) tb.web.getSettings().setBlockNetworkImage(false);

        // 恢复上次未关闭的标签
        if (getIntent() != null && getIntent().getBooleanExtra("do_theme_export", false)) {
            getIntent().removeExtra("do_theme_export");
            root.postDelayed(this::exportTheme, 400);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("do_theme_import", false)) {
            getIntent().removeExtra("do_theme_import");
            root.postDelayed(this::importTheme, 400);
        }
        if (getIntent() != null && getIntent().getStringExtra("open_url") != null) {
            String u = getIntent().getStringExtra("open_url");
            if (getIntent().getBooleanExtra("private_url", false)) privateUrl = u;
            getIntent().removeExtra("private_url");
            getIntent().removeExtra("open_url");
            root.postDelayed(() -> openUrlOrSearch(u), 300);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_font", false)) {
            getIntent().removeExtra("open_font");
            root.postDelayed(this::pickFont, 500);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_sync", false)) {
            getIntent().removeExtra("open_sync");
            root.postDelayed(this::showSync, 500);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("edit_cards", false)) {
            getIntent().removeExtra("edit_cards");
            root.postDelayed(this::editHomeCards, 600);
        }
        root.postDelayed(() -> {
            try { restoreSession(); } catch (Throwable ignored) {}
        }, 1200);

        // 上次退出时代理是开着的就自动恢复
        // 上次开着代理就在后台恢复：绝不能在主线程等内核（否则启动界面卡死）
        final ProxyManager pm = ProxyManager.get(this);
        if (pm.enabled) {
            // 等首帧出来再拉内核：启动瞬间不抢 CPU
            root.postDelayed(() -> new Thread(() -> {
                try {
                    pm.start();
                } catch (Throwable ignored) {}
            }, "proxy-autostart").start(), 1600);
        }
    }

    // ================= 界面构建 =================

    private static void detach(View v) {
        if (v == null) return;
        ViewGroup p = (ViewGroup) v.getParent();
        if (p != null) p.removeView(v);
    }

    private android.widget.HorizontalScrollView stripScroll;

    /** 重画顶部标签条。 */
    private void rebuildTabStrip() {
        if (tabStrip == null) return;
        tabStrip.removeAllViews();
        if (!Prefs.getBool("tab_strip", false)) return;
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            BrowserTab t = tabs.get(i);
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(Theme.dp(this, 12), 0, Theme.dp(this, 6), 0);
            boolean on = i == current;
            chip.setBackground(Theme.rounded(on ? Theme.field(this, ui) : 0x00000000, Theme.dp(this, 9)));
            TextView title = Widgets.text(this, trim(t.title == null ? "新标签页" : t.title, 10),
                    12, on ? Theme.fg(this, ui) : Theme.hint(this, ui));
            chip.addView(title);
            TextView close = Widgets.text(this, "✕", 11, Theme.hint(this, ui));
            close.setPadding(Theme.dp(this, 8), Theme.dp(this, 6), Theme.dp(this, 6), Theme.dp(this, 6));
            close.setOnClickListener(v -> closeTab(idx));
            chip.addView(close);
            chip.setOnClickListener(v -> selectTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(Theme.dp(this, 4), Theme.dp(this, 4), 0, Theme.dp(this, 4));
            tabStrip.addView(chip, lp);
        }
    }

    private void buildChrome() {
        root.removeAllViews();
        topBar = buildTopBar();
        if (ui.showTopBar) root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, ui.topBarHeight)));

        bottomBar = buildBottomBar();
        boolean barUnderAddress = Prefs.getBool("bar_under_address", false);
        if (barUnderAddress && ui.showBottomBar) {
            root.addView(bottomBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, ui.bottomBarHeight)));
        }

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        if (progress.getProgressDrawable() != null) {
            progress.getProgressDrawable().setTint(Theme.accent(ui));
        }
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 2)));

        // 顶部标签条（可选）
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setBackgroundColor(Theme.bg(this, ui));
        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.addView(tabStrip);
        this.stripScroll = strip;
        if (Prefs.getBool("tab_strip", false)) {
            root.addView(strip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 38)));
        }
        rebuildTabStrip();

        FrameLayout pageHost = new FrameLayout(this);
        pageHost.setBackgroundColor(Theme.bg(this, ui));
        tabHost.setBackgroundColor(Theme.bg(this, ui));
        fullscreenHost.setBackgroundColor(Theme.bg(this, ui));
        // addView 不会自动把子 View 从旧父容器摘下来，重建界面时必须先 detach
        detach(tabHost);
        detach(fullscreenHost);
        pageHost.addView(tabHost, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageHost.addView(fullscreenHost, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!barUnderAddress && ui.showBottomBar) {
            if (bottomBar.getParent() != null) ((ViewGroup) bottomBar.getParent()).removeView(bottomBar);
            root.addView(bottomBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, ui.bottomBarHeight)));
        }

        // 工具栏长按 = 可配置手势
        View.OnLongClickListener toolbarLong = v -> {
            String act = ui.gestures.get("toolbar_long_press");
            if (act != null && !act.isEmpty()) {
                dispatch(act);
                return true;
            }
            return false;
        };
        topBar.setOnLongClickListener(toolbarLong);
        bottomBar.setOnLongClickListener(toolbarLong);
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Theme.bar(this, ui));
        bar.setPadding(Theme.dp(this, 8), 0, Theme.dp(this, 8), 0);

        // 左：网站信息（Via 的第一个按钮）
        schemeIcon = new android.widget.ImageView(this);
        schemeIcon.setImageResource(R.drawable.ic_info);
        schemeIcon.setColorFilter(Theme.hint(this, ui));
        schemeIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int ip = Theme.dp(this, 9);
        schemeIcon.setPadding(ip, ip, ip, ip);
        schemeIcon.setOnClickListener(v -> showSiteInfo());
        schemeIcon.setOnLongClickListener(v -> {
            reload();
            return true;
        });
        bar.addView(schemeIcon, new LinearLayout.LayoutParams(Theme.dp(this, 40), ViewGroup.LayoutParams.MATCH_PARENT));

        // 中：地址（平常是文字，点了才变成输入框）
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setGravity(Gravity.CENTER);
        address.setHint("天地之大，你到底在追寻什么");
        address.setTextColor(Theme.fg(this, ui));
        address.setHintTextColor(Theme.hint(this, ui));
        address.setBackground(null);
        address.setPadding(Theme.dp(this, 6), 0, Theme.dp(this, 6), 0);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setOnFocusChangeListener((v, has) -> {
            setAddressEditing(has);
            if (!has) {
                hideSuggest();
                onTabStateChanged();
            }
        });
        address.setOnClickListener(v -> address.selectAll());
        address.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence x, int a, int b, int c) {
                if (!addressEditing) return;
                fetchSuggest(x.toString());
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        address.setOnLongClickListener(v -> {
            if (Prefs.getBool("vault_long_press", true)) {
                try {
                    startActivity(new Intent(this, VaultActivity.class)
                            .putExtra("page_url", currentPageUrlForVault()));
                } catch (Exception ignored) {}
                return true;
            }
            String act = ui.gestures.get("address_long_press");
            if (act != null && !act.isEmpty()) {
                dispatch(act);
                return true;
            }
            return false;
        });
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                openUrlOrSearch(address.getText().toString());
                hideKeyboard();
                address.clearFocus();
                setAddressEditing(false);
                return true;
            }
            return false;
        });
        bar.addView(address, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // 右：不编辑时是「扫一扫」，编辑时是「搜索」
        engineIcon = new TextView(this);
        engineIcon.setTextSize(14);
        engineIcon.setGravity(Gravity.CENTER);
        engineIcon.setTextColor(Theme.fg(this, ui));
        showScanIcon(true);
        engineIcon.setOnClickListener(v -> {
            if (address != null && address.hasFocus()) {
                openUrlOrSearch(address.getText().toString());
                hideKeyboard();
                address.clearFocus();
            } else {
                scanQr();
            }
        });
        engineIcon.setOnLongClickListener(v -> {
            if (address != null && address.hasFocus()) return false;
            showEngineSheet();     // 长按还能换引擎
            return true;
        });
        bar.addView(engineIcon, new LinearLayout.LayoutParams(Theme.dp(this, 44), ViewGroup.LayoutParams.MATCH_PARENT));


        // 编辑时左侧的「取消」
        cancelEdit = new TextView(this);
        cancelEdit.setText("取消");
        cancelEdit.setTextSize(14);
        cancelEdit.setGravity(Gravity.CENTER);
        cancelEdit.setTextColor(Theme.fg(this, ui));
        cancelEdit.setVisibility(View.GONE);
        cancelEdit.setOnClickListener(v -> {
            address.clearFocus();
            hideKeyboard();
        });
        bar.addView(cancelEdit, new LinearLayout.LayoutParams(Theme.dp(this, 52), ViewGroup.LayoutParams.MATCH_PARENT));

        return bar;
    }

    private Runnable suggestRunnable;
    private LinearLayout suggestBox;

    /** 输入时抓联想词（250ms 防抖）。 */
    private void fetchSuggest(final String q) {
        if (suggestRunnable != null) address.removeCallbacks(suggestRunnable);
        if (q == null || q.trim().length() < 1) {
            hideSuggest();
            return;
        }
        // 输入时把可能的域名先解析掉
        String maybeHost = q.trim();
        if (maybeHost.contains(".") && !maybeHost.contains(" ")) {
            com.vane.browser.core.DnsWarm.warm(maybeHost.replaceAll("^https?://", "").split("/")[0]);
        }
        suggestRunnable = () -> new Thread(() -> {
            String body = null;
            try {
                String tpl = SearchEngines.current().suggestTemplate;
                if (tpl == null || tpl.isEmpty()) {
                    tpl = "https://suggestqueries.google.com/complete/search?client=firefox&q=%s";
                }
                String url = tpl.replace("%s", Uri.encode(q));
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(2500);
                c.setReadTimeout(2500);
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                body = b.toString("UTF-8");
            } catch (Exception ignored) {}
            final String raw = body;
            runOnUiThread(() -> showSuggest(raw));
        }, "suggest").start();
    }

    /** 建议面板：先「搜索 xxx」，再本地书签/历史，最后引擎联想（照 Chrome 的排序）。 */
    private void showSuggest(String raw) {
        hideSuggest();
        final String q = address.getText().toString().trim();
        if (q.isEmpty()) return;

        java.util.List<String[]> rows = new ArrayList<>();   // {类型, 标题, 副标题/网址}
        rows.add(new String[]{"s", "搜索“" + q + "”", SearchEngines.current().name});

        // 本地：书签优先，其次历史
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String key : new String[]{"bookmarks", "history"}) {
            org.json.JSONArray arr = Prefs.getArray(key);
            for (int i = 0; i < arr.length() && rows.size() < 9; i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                String title = o.optString("title", url);
                if (url.isEmpty() || seen.contains(url)) continue;
                String lq = q.toLowerCase();
                if (!title.toLowerCase().contains(lq) && !url.toLowerCase().contains(lq)) continue;
                seen.add(url);
                rows.add(new String[]{"l", title, url});
            }
        }

        // 远端联想
        if (raw != null && raw.trim().startsWith("[")) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                org.json.JSONArray list = arr.optJSONArray(1);
                if (list != null) {
                    for (int i = 0; i < list.length() && rows.size() < 12; i++) {
                        String v = list.optString(i, "");
                        if (v.isEmpty() || seen.contains(v)) continue;
                        seen.add(v);
                        rows.add(new String[]{"r", v, ""});
                    }
                }
            } catch (Exception ignored) {}
        }

        suggestBox = new LinearLayout(this);
        suggestBox.setOrientation(LinearLayout.VERTICAL);
        suggestBox.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
        suggestBox.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 14)));

        for (final String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Theme.dp(this, 14), Theme.dp(this, 10), Theme.dp(this, 14), Theme.dp(this, 10));

            android.widget.ImageView ic = new android.widget.ImageView(this);
            ic.setImageResource("s".equals(row[0]) ? R.drawable.ic_search
                    : "l".equals(row[0]) ? R.drawable.ic_clock : R.drawable.ic_search);
            ic.setColorFilter(Theme.hint(this, ui));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    Theme.dp(this, 18), Theme.dp(this, 18));
            ilp.setMargins(0, 0, Theme.dp(this, 12), 0);
            line.addView(ic, ilp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Widgets.text(this, row[1], 14, Theme.fg(this, ui)));
            if (!row[2].isEmpty()) {
                TextView sub = Widgets.text(this, trim(row[2], 46), 11, Theme.hint(this, ui));
                sub.setPadding(0, Theme.dp(this, 2), 0, 0);
                texts.addView(sub);
            }
            line.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            line.setOnClickListener(v -> {
                hideSuggest();
                String target = row[1];
                if ("s".equals(row[0]) || "r".equals(row[0])) {
                    address.setText(target);
                    address.setSelection(target.length());
                    openUrlOrSearch(target);
                } else {
                    openUrlOrSearch(row[2]);
                }
                hideKeyboard();
                address.clearFocus();
                setAddressEditing(false);
            });
            suggestBox.addView(line);
        }

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.topBarHeight == 0 ? Theme.dp(this, 50) : Theme.dp(this, ui.topBarHeight + 2);
        lp.leftMargin = Theme.dp(this, 8);
        lp.rightMargin = Theme.dp(this, 8);
        root.addView(suggestBox, lp);
    }

    private void hideSuggest() {
        if (suggestBox != null) {
            root.removeView(suggestBox);
            suggestBox = null;
        }
    }

    /** 顶栏右侧：平时显示扫码图标，编辑地址时变成「搜索」两个字。 */
    private void showScanIcon(boolean scan) {
        if (engineIcon == null) return;
        if (scan) {
            engineIcon.setText("");
            android.graphics.drawable.Drawable d =
                    androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_scan);
            if (d != null) {
                d = d.mutate();
                d.setColorFilter(Theme.fg(this, ui), android.graphics.PorterDuff.Mode.SRC_IN);
                int sz = Theme.dp(this, 22);
                d.setBounds(0, 0, sz, sz);
            }
            engineIcon.setCompoundDrawables(null, null, null, null);
            engineIcon.setCompoundDrawables(d, null, null, null);
        } else {
            engineIcon.setCompoundDrawables(null, null, null, null);
            engineIcon.setText("搜索");
        }
    }

    /** 进入/退出「地址栏编辑态」：左边变取消，右边变搜索。 */
    private void setAddressEditing(boolean editing) {
        addressEditing = editing;
        if (schemeIcon != null) schemeIcon.setVisibility(editing ? View.GONE : View.VISIBLE);
        if (cancelEdit != null) cancelEdit.setVisibility(editing ? View.VISIBLE : View.GONE);
        if (address != null) {
            address.setGravity(editing ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
            if (editing) address.selectAll();
        }
        showScanIcon(!editing);
    }

    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Theme.bar(this, ui));
        bar.setPadding(Theme.dp(this, 2), 0, Theme.dp(this, 2), 0);
        for (UiConfig.Item it : ui.items) {
            if (it.flex || !it.visible) continue;
            final String act = it.action;
            final String longAct = it.longAction;

            if ("tabs".equals(act)) {
                // 第 4 格：数字 13sp，外面一圈紧贴数字的细框
                android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
                TextView tv = new TextView(this);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(13);
                tv.setText(tabText());
                tv.setIncludeFontPadding(false);
                tv.setTextColor(Theme.fg(this, ui));
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);  // 笔画浓度对齐图标
                int strokeW = Math.max(2, Math.round(Theme.dp(this, 2) * 0.9f));
                tv.setBackground(Theme.roundedStrokeWidth(0x00000000, Theme.fg(this, ui),
                        Theme.dp(this, 5), strokeW));
                int ph = Theme.dp(this, 6), pv = Theme.dp(this, 2);
                tv.setPadding(ph, pv, ph, pv);
                android.widget.FrameLayout.LayoutParams flp2 = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                flp2.gravity = Gravity.CENTER;
                holder.addView(tv, flp2);
                holder.setOnClickListener(v -> dispatch(act));
                if (longAct != null && !longAct.isEmpty()) holder.setOnLongClickListener(v -> {
                    dispatch(longAct);
                    return true;
                });
                tv.setOnClickListener(v -> dispatch(act));
                tabCounter = tv;
                bar.addView(holder, weighted());
                continue;
            }

            if (it.icon != null && !it.icon.isEmpty()) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                int res = getResources().getIdentifier("ic_" + it.icon, "drawable", getPackageName());
                if (res != 0) iv.setImageResource(res);
                else iv.setImageResource(android.R.drawable.ic_menu_more);
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                int pad = Theme.dp(this, 9);
                iv.setPadding(pad, pad, pad, pad);
                iv.setColorFilter(Theme.fg(this, ui));
                if ("back".equals(act)) backBtn = null;
                iv.setOnClickListener(v -> dispatch(act));
                if (longAct != null && !longAct.isEmpty()) iv.setOnLongClickListener(v -> {
                    dispatch(longAct);
                    return true;
                });
                if ("back".equals(act)) backIcon = iv;
                if ("forward".equals(act)) forwardIcon = iv;
                bar.addView(iv, weighted());
                continue;
            }

            TextView b = new TextView(this);
            b.setText(it.glyph);
            b.setTextSize(17);
            b.setGravity(Gravity.CENTER);
            b.setTextColor(Theme.fg(this, ui));
            b.setOnClickListener(v -> dispatch(act));
            if (longAct != null && !longAct.isEmpty()) b.setOnLongClickListener(v -> {
                dispatch(longAct);
                return true;
            });
            bar.addView(b, weighted());
        }
        return bar;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        return lp;
    }

    private String tabText() {
        int n = tabs.size();
        return n > 99 ? "99+" : String.valueOf(Math.max(1, n));
    }

    /** 后退/前进不可用时置灰，让人一眼看出能不能点。 */
    private void updateNavState() {
        BrowserTab t = currentTab();
        boolean canBack = t != null && t.web.canGoBack();
        boolean canFwd = t != null && t.web.canGoForward();
        if (backBtn != null) backBtn.setAlpha(canBack ? 1f : 0.28f);
        if (forwardBtn != null) forwardBtn.setAlpha(canFwd ? 1f : 0.28f);
        if (backIcon != null) backIcon.setAlpha(canBack ? 1f : 0.22f);
        if (forwardIcon != null) forwardIcon.setAlpha(canFwd ? 1f : 0.22f);
    }

    private void rebuildChrome() {
        buildChrome();
        // 把已存在的 WebView 重新挂回容器
        tabHost.removeAllViews();
        for (BrowserTab t : tabs) {
            if (t.web.getParent() != null) ((ViewGroup) t.web.getParent()).removeView(t.web);
            tabHost.addView(t.web);
            t.web.setVisibility(View.GONE);
        }
        if (current >= 0 && current < tabs.size()) tabs.get(current).web.setVisibility(View.VISIBLE);
        onTabStateChanged();
    }

    // ================= 标签页 =================

    public BrowserTab currentTab() {
        if (tabs.isEmpty()) return null;
        if (current < 0 || current >= tabs.size()) current = 0;
        return tabs.get(current);
    }

    /**
     * 给 target=_blank 的弹窗用的标签：必须是一个从没加载过任何地址的 WebView，
     * 否则系统会抛 "New WebView for popup window must not have been previously navigated"。
     */
    public BrowserTab newTabForPopup() {
        BrowserTab t = new BrowserTab(this);
        t.web.setVisibility(View.GONE);
        tabHost.addView(t.web);
        tabs.add(t);
        selectTab(tabs.size() - 1);
        onTabStateChanged();
        return t;
    }

    public void newTab(String url, boolean foreground) {
        if (tabs.size() >= ui.tabLimit) {
            Widgets.toast(this, "标签已达上限（" + ui.tabLimit + "），先关掉一个");
            return;
        }
        BrowserTab t = new BrowserTab(this);
        t.web.setVisibility(View.GONE);
        tabHost.addView(t.web);
        tabs.add(t);
        if (foreground) selectTab(tabs.size() - 1);
        t.load(url == null || url.isEmpty() ? ui.homePage : url);
        onTabStateChanged();
    }

    public void selectTab(int i) {
        if (i < 0 || i >= tabs.size()) return;
        for (int k = 0; k < tabs.size(); k++) {
            tabs.get(k).web.setVisibility(k == i ? View.VISIBLE : View.GONE);
        }
        current = i;
        onTabStateChanged();
    }

    public void closeTab(int i) {
        if (i < 0 || i >= tabs.size()) return;
        BrowserTab t = tabs.remove(i);
        if (t.url != null && !t.url.isEmpty() && !"about:blank".equals(t.url)) {
            recentlyClosed.add(0, new String[]{t.url, t.title == null ? t.url : t.title});
            while (recentlyClosed.size() > 10) recentlyClosed.remove(recentlyClosed.size() - 1);
        }
        showUndoBar(t.url, t.title);
        tabHost.removeView(t.web);
        t.destroy();
        if (tabs.isEmpty()) {
            current = -1;
            newTab(ui.homePage, true);
        } else {
            selectTab(Math.min(i, tabs.size() - 1));
        }
        onTabStateChanged();
    }

    public void closeCurrentTab() {
        closeTab(current);
    }

    public void switchTabDelta(int d) {
        if (tabs.size() <= 1) return;
        selectTab((current + d + tabs.size()) % tabs.size());
    }

    public void onTabStateChanged() {
        try {
            onTabStateChangedInner();
        } catch (Throwable ignored) {}
    }

    private void onTabStateChangedInner() {
        BrowserTab t = currentTab();
        if (t == null) return;
        if (address != null && !addressEditing) {
            if (t.internalLabel != null && !t.internalLabel.isEmpty()) {
                address.setText(t.internalLabel);
                address.setHint("");
            } else {
                boolean atHome = t.isHome() || t.url == null
                        || t.url.equals(ui.homePage) || t.url.startsWith("about:") || t.url.startsWith("data:");
                address.setText(atHome ? "" : t.url);
            }
        }
        rebuildTabStrip();
        if (tabCounter != null) {
            tabCounter.setText(tabText());
            tabCounter.setTextColor(Theme.fg(this, ui));   // 一律黑色，不用蓝色
        }
        if (engineIcon != null && !addressEditing) showScanIcon(true);
        updateSchemeIcon(t);
        if (!t.isHome()) addHistory(t.url, t.title);
        updateNavState();
    }

    public void onProgress(int p) {
        if (progress == null) return;
        BrowserTab cur = currentTab();
        if (cur != null && cur.internalLabel != null) {   // 内置页不显示进度条（会一直抖）
            progress.setVisibility(View.GONE);
            return;
        }
        progress.setProgress(p);
        progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
        if (p >= 100) updateNavState();
    }

    public void onPageError(String msg) {
        BrowserTab t = currentTab();
        if (t == null || isFinishing()) return;
        // 用一个内联错误页替换白屏，并给「重试 / 回主页」，避免地址栏显示新网址但内容是旧页面
        String failed = t.url == null ? "" : t.url;
        String extra = "";
        try {
            String low = failed.toLowerCase(java.util.Locale.CHINA);
            boolean foreign = low.contains("google.") || low.contains("youtube.") || low.contains("twitter.")
                    || low.contains("x.com") || low.contains("facebook.") || low.contains("wikipedia.")
                    || low.contains("github.") || low.contains("duckduckgo.");
            String q = null;
            int qi = low.indexOf("q=");
            if (qi > 0) {
                String tail = failed.substring(qi + 2);
                int amp = tail.indexOf('&');
                q = amp > 0 ? tail.substring(0, amp) : tail;
            }
            StringBuilder b = new StringBuilder();
            if (foreign) {
                b.append("<div class='tip'>这个站点通常要开代理才能打开</div>")
                 .append("<a class='ghost' href='vane://action/proxyPanel'>去开代理</a>");
            }
            if (q != null && !q.isEmpty()) {
                b.append("<a class='ghost' href='https://www.bing.com/search?q=")
                 .append(htmlEscape(q)).append("'>用 Bing 搜同样的词</a>");
            }
            extra = b.toString();
        } catch (Throwable ignored) {}
        String html = "<!doctype html><html><head><meta name=viewport "
                + "content='width=device-width,initial-scale=1'><style>"
                + "body{margin:0;background:" + (Theme.isDark(this, ui) ? "#101114" : "#fff")
                + ";color:" + (Theme.isDark(this, ui) ? "#EDEEF0" : "#111")
                + ";font:15px/1.6 system-ui;display:flex;align-items:center;justify-content:center;"
                + "height:100vh;text-align:center}"
                + "h3{font-size:17px;margin:0 0 10px}p{color:" + (Theme.isDark(this, ui) ? "#9AA0A6" : "#6B7280")
                + ";font-size:13px;margin:0 0 18px;word-break:break-all}"
                + "a{display:inline-block;padding:10px 18px;border-radius:12px;text-decoration:none;"
                + "background:" + Theme.accent(ui) + ";color:#fff;font-size:14px;margin:4px}"
                + "a.ghost{background:transparent;border:1px solid " + Theme.line(this, ui)
                + ";color:" + (Theme.isDark(this, ui) ? "#EDEEF0" : "#111") + "}"
                + ".tip{color:" + (Theme.isDark(this, ui) ? "#9AA0A6" : "#6B7280") + ";font-size:12px;margin:6px 0 2px}"
                + "</style></head><body><div><h3>打不开这个页面</h3><p>" + htmlEscape(failed)
                + "<br>" + htmlEscape(msg == null ? "" : msg) + "</p>"
                + "<a href='" + htmlEscape(failed) + "'>重新加载</a>" + extra + "</div></body></html>";
        t.web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        Widgets.toast(this, "加载失败：" + msg);
    }

    private View sniffBar;

    /** 页面里发现视频时，底部弹一条提示。 */
    public void onMediaFound(String url, String type) {
        try {
            if (url == null || isFinishing()) return;
            final int count = JsBridge.MEDIA.size();
            if (count <= 0) return;
            if (sniffBar != null && sniffBar.getVisibility() == View.VISIBLE) return;
            LinearLayout bar2 = new LinearLayout(this);
            bar2.setOrientation(LinearLayout.HORIZONTAL);
            bar2.setGravity(Gravity.CENTER_VERTICAL);
            bar2.setPadding(Theme.dp(this, 14), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));
            bar2.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 18)));

            TextView label = Widgets.text(this, "发现 " + count + " 个视频", 13, Theme.fg(this, ui));
            bar2.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView play = Widgets.text(this, "播放", 13, Theme.accent(ui));
            play.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), Theme.dp(this, 10), Theme.dp(this, 6));
            play.setOnClickListener(v -> {
                hideSniffBar();
                showVideoList();
            });
            bar2.addView(play);

            TextView close = Widgets.text(this, "✕", 13, Theme.hint(this, ui));
            close.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), Theme.dp(this, 10), Theme.dp(this, 6));
            close.setOnClickListener(v -> hideSniffBar());
            bar2.addView(close);

            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Theme.dp(this, 12);
            lp.rightMargin = Theme.dp(this, 12);
            lp.bottomMargin = (ui.bottomBarHeight == 0 ? 46 : ui.bottomBarHeight) > 0
                    ? Theme.dp(this, (ui.bottomBarHeight == 0 ? 46 : ui.bottomBarHeight) + 10) : Theme.dp(this, 56);
            root.addView(bar2, lp);
            sniffBar = bar2;
        } catch (Throwable ignored) {}
    }

    private void hideSniffBar() {
        if (sniffBar != null) {
            root.removeView(sniffBar);
            sniffBar = null;
        }
    }

    /** 只截当前屏幕（视口）。 */
    public void captureViewport() {
        final BrowserTab t = currentTab();
        if (t == null) return;
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    t.web.getWidth(), t.web.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            t.web.draw(new android.graphics.Canvas(bmp));
            String name = "vane-shot-" + stamp() + ".png";
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Vane");
            android.net.Uri uri = getContentResolver()
                    .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            Widgets.toast(this, "已存到相册/Pictures/Vane");
        } catch (Exception e) {
            Widgets.toast(this, "截图失败：" + e.getMessage());
        }
    }

    /**
     * 整页长截图，存到相册。
     *
     * 踩过的三个坑，别再往回改：
     * 1) 把整页画进一张超长 Bitmap：WebView 的硬件图层不参与软件绘制，整张全黑；
     * 2) View.scrollTo 翻页：滚动在渲染进程里，View 层只是把画面推出可视区，抓到的还是第一屏；
     * 3) window.scrollTo 翻页 + View.draw：滚动生效了，但 draw 只出第一帧，后面全是背景。
     * 现在的做法：JS 的 window.scrollTo 真翻页 → 等渲染 → PixelCopy 抓「窗口上真正显示的那块」
     * → 按 window.scrollY 换算贴到大图上；PixelCopy 低于 API 26 时退回 View.draw。
     */
    public void captureLongScreenshot() {
        final BrowserTab t = currentTab();
        if (t == null) return;
        Widgets.toast(this, "正在拼接长图…");
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        t.web.evaluateJavascript(
                "(function(){var d=document.documentElement,b=document.body;"
                        + "return JSON.stringify({h:Math.max(b?b.scrollHeight:0,d?d.scrollHeight:0),"
                        + "y:window.scrollY||window.pageYOffset||0});})()",
                v -> {
                    int cssH = 0;
                    double cssY = 0;
                    try {
                        String raw = v == null ? "" : v;
                        if (raw.startsWith("\"")) {
                            raw = raw.substring(1, raw.length() - 1)
                                    .replace("\\\"", "\"").replace("\\\\", "\\");
                        }
                        org.json.JSONObject o = new org.json.JSONObject(raw);
                        cssH = o.optInt("h", 0);
                        cssY = o.optDouble("y", 0);
                    } catch (Exception ignored) {}
                    final int w = t.web.getWidth();
                    final int viewH = t.web.getHeight();
                    float sc = t.web.getScale();
                    if (!(sc > 0f)) sc = 1f;
                    final float scale = sc;
                    final int contentPx = cssH > 0 ? (int) (cssH * scale) : t.web.getContentHeight();
                    // 上限按可用内存算：一张 ARGB_8888 长图 = w × h × 4 字节，
                    // 只敢用最大堆的 1/5，再夹在 8000～48000 之间（老机型不至于 OOM，新机型能截更长）。
                    int cap = 16000;
                    try {
                        long heap = Runtime.getRuntime().maxMemory();
                        cap = (int) Math.min(48000L, Math.max(16000L, (heap / 5) / (4L * Math.max(1, w))));
                    } catch (Throwable ignored) {}
                    final int total = Math.min(Math.max(contentPx, viewH), cap);
                    if (w <= 0 || viewH <= 0 || total <= 0) {
                        Widgets.toast(this, "这页没法截图");
                        return;
                    }
                    final double oldY = cssY;
                    final android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                            w, total, android.graphics.Bitmap.Config.ARGB_8888);
                    final android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                    canvas.drawColor(com.vane.browser.ui.Theme.bg(this, ui));
                    final int[] y = {0};
                    final int[] guard = {0};
                    final Runnable[] step = new Runnable[1];
                    step[0] = () -> {
                        if (y[0] >= total || guard[0]++ > 60) {
                            t.web.evaluateJavascript("window.scrollTo(0," + (int) oldY + ")", null);
                            saveLongShot(bmp, contentPx > total ? contentPx : 0);
                            return;
                        }
                        final int want = y[0];
                        final int wantCss = Math.round(want / scale);
                        t.web.evaluateJavascript(
                                "window.scrollTo(0," + wantCss + ");"
                                        + "window.scrollY!==undefined?window.scrollY:(window.pageYOffset||0)",
                                vv -> {
                                    double rawY = 0;
                                    try {
                                        rawY = Double.parseDouble(vv == null ? "0"
                                                : vv.replace("\"", ""));
                                    } catch (Exception ignored) {}
                                    final int actual = (int) (rawY * scale);
                                    h.postDelayed(() -> captureChunk(t, w, viewH, chunk -> {
                                        try {
                                            int srcTop = Math.max(0, want - actual);
                                            int srcBottom = Math.min(viewH, total - actual);
                                            if (srcBottom > srcTop) {
                                                android.graphics.Rect src =
                                                        new android.graphics.Rect(0, srcTop, w, srcBottom);
                                                android.graphics.Rect dst =
                                                        new android.graphics.Rect(0, actual + srcTop,
                                                                w, actual + srcBottom);
                                                canvas.drawBitmap(chunk, src, dst, null);
                                            }
                                        } finally {
                                            chunk.recycle();
                                        }
                                        int next = actual + viewH;
                                        y[0] = Math.max(next, want + viewH);
                                        h.postDelayed(step[0], 40);
                                    }), 260);
                                });
                    };
                    h.post(step[0]);
                });
    }

    private interface ChunkCb { void onChunk(android.graphics.Bitmap bmp); }

    /** 抓当前窗口里 WebView 那一块的像素：PixelCopy 优先，老系统退回 View.draw。 */
    private void captureChunk(BrowserTab t, int w, int viewH, ChunkCb cb) {
        final android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                w, viewH, android.graphics.Bitmap.Config.ARGB_8888);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                int[] loc = new int[2];
                t.web.getLocationInWindow(loc);
                android.graphics.Rect rect = new android.graphics.Rect(
                        loc[0], loc[1], loc[0] + w, loc[1] + viewH);
                android.view.PixelCopy.request(getWindow(), rect, out,
                        result -> cb.onChunk(out), new android.os.Handler(android.os.Looper.getMainLooper()));
                return;
            } catch (Throwable ignored) {}
        }
        try {
            t.web.draw(new android.graphics.Canvas(out));
        } catch (Throwable ignored) {}
        cb.onChunk(out);
    }

    /** 长截图落盘：相册优先，失败退回应用私有目录。 */
    private void saveLongShot(android.graphics.Bitmap bmp, int contentPx) {
        String name = "vane-shot-" + stamp() + ".png";
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Vane");
            android.net.Uri uri = getContentResolver()
                    .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            String extra = contentPx > 0
                    ? "\n页面约 " + contentPx + "px，超过单张上限，已截到 " + bmp.getHeight() + "px"
                    : "";
            Widgets.info(this, ui, "长截图已存", "相册 / Pictures / Vane / " + name
                    + "\n尺寸 " + bmp.getWidth() + "×" + bmp.getHeight() + extra);
        } catch (Exception e) {
            Widgets.toast(this, "保存失败：" + e.getMessage());
        }
    }

    public void onMediaFoundOld(String url, String type) {
        // 视频地址先收进列表，用户点「嗅探视频」时统一展示
    }

    public void onLinkLongPress(String url, String title) {
        lastLinkUrl = url;
        showLinkMenu(url, title);
    }

    /** 地址栏左侧的小标识：家 / 锁 / 不安全。 */
    private void updateSchemeIcon(BrowserTab t) {
        if (schemeIcon == null) return;
        int color = Theme.hint(this, ui);
        if (t != null && t.url != null) {
            String u = t.url.toLowerCase();
            if (u.startsWith("https://")) color = Theme.accent(ui);
            else if (u.startsWith("http://")) color = 0xFFE0A32E;
        }
        schemeIcon.setColorFilter(color);
    }

    /** 页面信息：这个网址，以及它走直连还是走代理。就这两件事，别的不放。 */
    public void showSiteInfo() {
        if (isFinishing() || isDestroyed()) return;
        BrowserTab t = currentTab();
        if (t == null) return;

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        String url = t.isHome() ? "" : (t.url == null ? "" : t.url);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 18), 0, Theme.dp(this, 18), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        TextView head = Widgets.text(this, "页面信息", 12, Theme.hint(this, ui));
        box.addView(head);

        String shown = url;
        if (shown.isEmpty() || shown.startsWith("about:") || shown.startsWith("data:")) {
            shown = "天地之大，你到底在追寻什么";
        }
        TextView addr = Widgets.text(this, shown, 14, Theme.fg(this, ui));
        addr.setPadding(0, Theme.dp(this, 8), 0, 0);
        addr.setTextIsSelectable(true);
        box.addView(addr);

        String route;
        if (url.isEmpty()) {
            route = "";
        } else {
            String host = "";
            try {
                String h0 = Uri.parse(url).getHost();
                host = h0 == null ? "" : h0;
            } catch (Exception ignored) {}
            ProxyManager pm = ProxyManager.get(this);
            boolean proxyOn = pm.enabled();
            boolean viaProxy = !host.isEmpty() && pm.rules.proxyFor(host);
            if (!proxyOn) route = "代理未开启，直接连接";
            else if (viaProxy) route = "境外站点 · 走代理（" + pm.rules.mode() + "）";
            else route = "国内站点 · 直连";
        }
        if (!route.isEmpty()) {
            TextView r = Widgets.text(this, route, 12, Theme.accent(ui));
            r.setPadding(0, Theme.dp(this, 8), 0, 0);
            box.addView(r);
        }
        if (t.tFinish > 0) {
            String timing = "上次加载：首屏 " + (t.tCommit > 0 ? t.tCommit : t.tFinish) + " ms"
                    + " · 完成 " + t.tFinish + " ms";
            TextView tt = Widgets.text(this, timing, 12, Theme.hint(this, ui));
            tt.setPadding(0, Theme.dp(this, 6), 0, 0);
            box.addView(tt);
        }

        // 本页拦截了多少广告请求（换页归零，最直观地说明规则有没有用）
        if (!url.isEmpty() && blocker != null) {
            int n = blocker.pageBlocked;
            if (t.lastLoadMs > 0) {
                TextView tm = Widgets.text(this, "本页加载耗时 " + t.lastLoadMs + " ms", 12, Theme.hint(this, ui));
                tm.setPadding(0, Theme.dp(this, 6), 0, 0);
                box.addView(tm);
            }
            TextView ad = Widgets.text(this, n > 0
                    ? ("本页已拦 " + n + " 个广告/追踪请求")
                    : "本页暂未拦到广告请求", 12, Theme.hint(this, ui));
            ad.setPadding(0, Theme.dp(this, 6), 0, 0);
            box.addView(ad);
        }

        // 代理状态：已连接 / 未连接 · 地区 · 延迟
        final ProxyManager pm = ProxyManager.get(this);
        final TextView linkLine = Widgets.text(this, pm.linkLine(), 12,
                pm.enabled() ? Theme.accent(ui) : Theme.hint(this, ui));
        linkLine.setPadding(0, Theme.dp(this, 6), 0, 0);
        box.addView(linkLine);
        final TextView ipLine = Widgets.text(this, "", 11, Theme.hint(this, ui));
        ipLine.setPadding(0, Theme.dp(this, 3), 0, 0);
        ipLine.setVisibility(View.GONE);
        box.addView(ipLine);

        if (pm.enabled()) {
            pm.probeLatency(() -> runOnUiThread(() -> linkLine.setText(pm.linkLine())));
            pm.probeExitGeo(() -> runOnUiThread(() -> {
                linkLine.setText(pm.linkLine());
                if (pm.exitIp() != null && !pm.exitIp().isEmpty()) {
                    ipLine.setText("出口 IP：" + pm.exitIp());
                    ipLine.setVisibility(View.VISIBLE);
                }
            }));
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Theme.dp(this, 16), 0, 0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(0, 0, Theme.dp(this, 8), 0);

        if (!url.isEmpty()) {
            btns.addView(Widgets.ghostButton(this, ui, "复制网址", () -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Widgets.toast(this, "已复制");
                if (holder[0] != null) holder[0].dismiss();
            }), blp);
        }
        btns.addView(Widgets.ghostButton(this, ui, "刷新", () -> {
            if (holder[0] != null) holder[0].dismiss();
            reload();
        }), blp);
        btns.addView(Widgets.ghostButton(this, ui, "关闭", () -> {
            if (holder[0] != null) holder[0].dismiss();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    // ================= 导航 =================

    public void goBack() {
        BrowserTab t = currentTab();
        if (t != null && t.web.canGoBack()) t.web.goBack();
    }

    public void goForward() {
        BrowserTab t = currentTab();
        if (t != null && t.web.canGoForward()) t.web.goForward();
    }

    private long lastHomeAt;

    /**
     * 回主页。连点主页按钮以前每次都会重新 load 一次主页，
     * 结果是搜索框、地址栏反复重建，页面一直在抖。现在：
     * 1) 500ms 内重复点击直接忽略；2) 已经在主页就只回到顶部，不重载。
     */
    public void goHome() {
        long now = System.currentTimeMillis();
        if (now - lastHomeAt < 500) return;
        lastHomeAt = now;
        if (address != null) {
            address.clearFocus();
            address.setText("");
        }
        setAddressEditing(false);
        hideKeyboard();
        BrowserTab t = currentTab();
        if (t == null) return;
        if (t.isHome() || ui.homePage.equals(t.url)) {
            // 已经在主页：只回顶 + 重新推一次方块/引擎，避免白页或空方块
            try { pushHomeCards(); } catch (Throwable ignored) {}
            try { syncHomeEngine(t.web); } catch (Throwable ignored) {}
            t.web.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null);
            return;
        }
        t.load(ui.homePage);
    }

    public void reload() {
        BrowserTab t = currentTab();
        if (t != null) t.web.reload();
    }

    public void stopLoading() {
        BrowserTab t = currentTab();
        if (t != null) t.web.stopLoading();
    }

    public void scrollTop() {
        BrowserTab t = currentTab();
        if (t != null) t.web.scrollTo(0, 0);
    }

    public void scrollBottom() {
        BrowserTab t = currentTab();
        if (t != null) t.web.scrollTo(0, (int) (t.web.getContentHeight() * t.web.getScale()));
    }

    public void backOrExit() {
        BrowserTab t = currentTab();
        if (t != null && t.web.canGoBack()) t.web.goBack();
        else finish();
    }

    public void openUrlOrSearch(String input) {
        if (input == null || input.trim().isEmpty()) return;
        String url = SearchEngines.url(input);
        // 手输/粘贴的媒体直链也走「播放还是下载」，别让原生播放器独占
        try {
            String low = url.toLowerCase();
            if (low.matches("https?://.*\\.(m3u8|mp4|flv|mkv|webm|mpd|mov)(\\?.*)?$")) {
                askMediaOpen(url);
                return;
            }
        } catch (Throwable ignored) {}
        BrowserTab t = currentTab();
        if (t == null) newTab(url, true);
        else t.load(url);
    }

    public void dispatch(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        Actions.Def def = Actions.get(actionId);
        if (def == null) {
            String mapped = Actions.alias(actionId);
            if (mapped != null) def = Actions.get(mapped);
        }
        final Actions.Def d = def;
        if (d == null) {
            App.logEvent(this, "unknown-action", "id=" + actionId);
            Widgets.toast(this, "这个按钮还没绑定功能（" + actionId + "），可在「自定义」里重绑");
            return;
        }
        try {
            d.handler.run(this);
        } catch (IllegalStateException e) {
            getWindow().getDecorView().post(() -> {
                try {
                    d.handler.run(this);
                } catch (Throwable t) {
                    Widgets.toast(this, "动作异常：" + t.getMessage());
                }
            });
        } catch (Throwable e) {
            Widgets.toast(this, "动作异常：" + e.getMessage());
        }
    }

    // ================= 地址栏 / 搜索引擎 =================

    public void focusAddress() {
        if (address == null) return;
        address.requestFocus();
        address.selectAll();
        showKeyboard();
        setAddressEditing(true);
    }

    public void showEngineMenu() {
        showEngineSheet();
    }

    /** 全部搜索引擎的选单：筛选框 + 官方图标 + 平铺。 */
    public void showEngineSheet() {
        if (isFinishing() || isDestroyed()) return;
        final List<SearchEngine> all = SearchEngines.all();
        final String current = Prefs.get("engine", "bing");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 12), 0, Theme.dp(this, 12), Theme.dp(this, 12));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        EditText filter = new EditText(this);
        filter.setSingleLine(true);
        filter.setHint("筛选引擎");
        filter.setTextSize(14);
        filter.setTextColor(Theme.fg(this, ui));
        filter.setHintTextColor(Theme.hint(this, ui));
        filter.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 20)));
        filter.setPadding(Theme.dp(this, 16), Theme.dp(this, 10), Theme.dp(this, 16), Theme.dp(this, 10));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.setMargins(0, Theme.dp(this, 4), 0, Theme.dp(this, 8));
        box.addView(filter, flp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(list);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        final Runnable[] render = new Runnable[1];
        final String[] keyword = {""};

        render[0] = () -> {
            list.removeAllViews();
            String kw = keyword[0].trim().toLowerCase();
            int shown = 0;
            for (final SearchEngine e : all) {
                String hay = (e.name + " " + e.id + " " + (e.note == null ? "" : e.note)).toLowerCase();
                if (!kw.isEmpty() && !hay.contains(kw)) continue;
                shown++;
                boolean on = e.id.equals(current);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Theme.dp(this, 8), Theme.dp(this, 9), Theme.dp(this, 8), Theme.dp(this, 9));

                android.graphics.Bitmap icon = com.vane.browser.ui.EngineIcons.get(this, e.icon);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Theme.dp(this, 28), Theme.dp(this, 28));
                llp.setMargins(0, 0, Theme.dp(this, 12), 0);
                if (icon != null) {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setImageBitmap(icon);
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    row.addView(iv, llp);
                } else {
                    TextView logo = new TextView(this);
                    logo.setText(e.glyph);
                    logo.setTextSize(e.glyph.length() > 2 ? 9 : 12);
                    logo.setGravity(Gravity.CENTER);
                    logo.setTextColor(Color.WHITE);
                    int c;
                    try {
                        c = Color.parseColor(e.color);
                    } catch (Exception ex) {
                        c = Theme.accent(ui);
                    }
                    logo.setBackground(Theme.rounded(c, Theme.dp(this, 9)));
                    row.addView(logo, llp);
                }

                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.addView(Widgets.text(this, e.name, 15, on ? Theme.accent(ui) : Theme.fg(this, ui)));
                if (e.note != null && !e.note.isEmpty()) {
                    TextView note = Widgets.text(this, e.note, 11, Theme.hint(this, ui));
                    note.setPadding(0, Theme.dp(this, 1), 0, 0);
                    texts.addView(note);
                }
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (on) row.addView(Widgets.text(this, "✓", 14, Theme.accent(ui)));

                row.setOnClickListener(v -> {
                    Prefs.put("engine", e.id);
                    if (engineIcon != null && address != null && !address.hasFocus()) {
                        engineIcon.setText(e.glyph);
                    }
                    applyEngineToHome(e.name);
                    if (holder[0] != null) holder[0].dismiss();
                    BrowserTab t = currentTab();
                    if (t != null && t.isHome()) {
                        t.web.evaluateJavascript("(function(){var q=document.getElementById('q');if(q)q.focus();})()", null);
                    } else {
                        focusAddress();
                    }
                });
                list.addView(row);
            }
            if (shown == 0) {
                TextView empty = Widgets.text(this, "没有匹配的引擎", 13, Theme.hint(this, ui));
                empty.setPadding(Theme.dp(this, 8), Theme.dp(this, 20), 0, 0);
                list.addView(empty);
            }
        };

        filter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                keyword[0] = s.toString();
                render[0].run();
            }
        });

        render[0].run();
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    /** 让主页搜索框上的引擎名字跟着变。 */
    /** 给主页的 JS：当前引擎的名字 + 图标地址（没有图标就传空，页面用首字兜底）。 */
    /** 把引擎图标读成 data URI 传给网页：不受 file:// 访问限制。 */
    private String engineIconDataUri(SearchEngine e) {
        if (e == null || e.icon == null || e.icon.isEmpty()) return "";
        try (java.io.InputStream in = getAssets().open("icons/" + e.icon)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return "data:image/png;base64,"
                    + android.util.Base64.encodeToString(b.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception ex) {
            return "";
        }
    }

    private String engineJs() {
        SearchEngine e = SearchEngines.current();
        String icon = engineIconDataUri(e);
        boolean dark = com.vane.browser.ui.Theme.isDark(this, ui);
        return "window.setDark && window.setDark(" + dark + ");"
                + "window.setEngine && window.setEngine("
                + org.json.JSONObject.quote(e.name) + "," + org.json.JSONObject.quote(icon) + ")";
    }

    /** 主页加载完成时同步一次引擎名 + 图标（对外给 BrowserTab 用）。 */
    public void syncHomeCards(android.webkit.WebView v) {
        try {
            v.evaluateJavascript("window.setCards && window.setCards("
                    + cardsForHome() + ")", null);
        } catch (Throwable ignored) {}
    }

    public void syncHomeEngine(android.webkit.WebView v) {
        try {
            v.evaluateJavascript(engineJs(), null);
        } catch (Throwable ignored) {}
    }

    public void applyEngineToHome(String engineName) {
        BrowserTab t = currentTab();
        if (t == null) return;
        t.web.evaluateJavascript(engineJs(), null);
    }

    // ================= 菜单 =================

    /** 保证常用入口一定在菜单里，且顺序固定（编辑格是运行时追加，正好 10 个）。 */
    private void ensureMenu() {
        String[] must = {"settings", "night", "library", "downloads",
                "incognito", "share", "addBookmark", "desktop", "toolbox"};
        boolean changed = false;
        // 去重：同一个功能只留第一次出现的位置
        java.util.List<String> unique = new ArrayList<>();
        for (String id : ui.menuItems) {
            if (!unique.contains(id)) unique.add(id);
        }
        if (unique.size() != ui.menuItems.size()) {
            ui.menuItems = unique;
            changed = true;
        }
        for (String id : must) {
            if (!ui.menuItems.contains(id)) {
                ui.menuItems.add(id);
                changed = true;
            }
        }
        // 用户自己排的顺序要尊重：只在缺项时追加，不再强制重排

        if (changed) ui.save();
    }

    /** 菜单格子里用的短名（长名字塞不下）。 */
    private String shortLabel(String id) {
        switch (id) {
            case "night": return "夜间";
            case "bookmarks": return "书签";
            case "history": return "历史";
            case "downloads": return "下载";
            case "incognito": return "隐身";
            case "share": return "分享";
            case "addBookmark": return "收藏";
            case "desktop": return "电脑版";
            case "toolbox": return "工具箱";
            case "settings": return "设置";
            case "find": return "查找";
            case "save": return "保存";
            case "translate": return "翻译";
            case "source": return "源码";
            case "fullscreen": return "全屏";
            case "images": return "无图";
            case "video": return "嗅探";
            case "reload": return "刷新";
            case "printPage": return "打印";
            case "openOther": return "外部打开";
            case "scanQr": return "扫码";
            case "addToDesktop": return "桌面";
            case "speak": return "朗读";
            case "ai": return "AI";
            case "rotate": return "横屏";
            case "adblock": return "拦截";
            case "markAd": return "标记";
            case "fontSize": return "字体";
            case "report": return "反馈";
            case "offline": return "离线";
            case "ua": return "标识";
            case "netlog": return "日志";
            case "bookmarksMerged": return "书签";
            default: return Actions.title(id);
        }
    }

    /** 菜单里哪些功能现在是「开着」的：开着就显示蓝色。 */
    /** 能开关的动作：右侧显示「开启/关闭」按钮，而不是箭头。 */
    /** 字体大小：−  110%  + 的步进按钮，点一下立刻生效。 */
    private View fontStepper() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);

        final int[] zoom = {Prefs.getInt("font_zoom", 100)};
        final TextView minus = Widgets.ghostButton(this, ui, "−", () -> {
            zoom[0] = Math.max(60, zoom[0] - 10);
            applyFontZoom(zoom[0]);
        });
        final TextView value = Widgets.text(this, zoom[0] + "%", 14, Theme.fg(this, ui));
        value.setPadding(Theme.dp(this, 14), 0, Theme.dp(this, 14), 0);
        final TextView plus = Widgets.ghostButton(this, ui, "＋", () -> {
            zoom[0] = Math.min(200, zoom[0] + 10);
            applyFontZoom(zoom[0]);
        });
        // 数值实时刷新
        wrap.addView(minus);
        wrap.addView(value);
        wrap.addView(plus);
        fontValueView = value;

        value.postDelayed(new Runnable() {
            @Override public void run() {
                if (fontValueView != null) fontValueView.setText(Prefs.getInt("font_zoom", 100) + "%");
                value.postDelayed(this, 400);
            }
        }, 400);
        return wrap;
    }

    private TextView fontValueView;

    private void applyFontZoom(int zoom) {
        Prefs.putInt("font_zoom", zoom);
        for (BrowserTab tb : tabs) {
            tb.web.getSettings().setTextZoom(zoom);
            if (Prefs.getBool("site_memory", true)) tb.rememberSiteFont(zoom);
        }
        if (fontValueView != null) fontValueView.setText(zoom + "%");
    }

    private boolean isTogglable(String id) {
        switch (id) {
            case "images":
            case "adblock":
            case "incognito":
            case "desktop":
            case "night":
                return true;
            default:
                return false;
        }
    }

    private boolean isActionActive(String actionId) {
        switch (actionId) {
            case "night": return Theme.isDark(this, ui);
            case "incognito": return Prefs.getBool("no_history", false);
            case "desktop": return desktopMode;
            case "images": return Prefs.getBool("no_images", false);   // 蓝色 = 无图已开
            case "adblock": return blocker.isEnabled();
            case "proxy":
            case "proxyToggle": return ProxyManager.get(this).isRunning();
            default: return false;
        }
    }

    public void showMenu() {
        try {
            showMenuPager();
        } catch (Throwable t) {
            App.logEvent(this, "menu-fallback", String.valueOf(t));
            try { showMenuPlain(); } catch (Throwable ignored) {}
        }
    }

    /** 兜底版菜单：不分页，直接一屏网格可滚动。 */
    private void showMenuPlain() {
        final java.util.List<String> ids = new ArrayList<>(ui.menuItems);
        ids.add("editMenu");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 4), 0, Theme.dp(this, 4), Theme.dp(this, 12));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(5);
        for (final String id : ids) {
            boolean isEdit = "editMenu".equals(id);
            Actions.Def def = isEdit ? null : Actions.get(id);
            if (!isEdit && def == null) continue;
            View cell = com.vane.browser.ui.Sheet.cell(this, ui,
                    isEdit ? R.drawable.ic_edit : com.vane.browser.ui.ActionIcons.res(id),
                    isEdit ? "编辑" : def.label, !isEdit && isActionActive(id), () -> {
                        if (holder[0] != null) holder[0].dismiss();
                        if (isEdit) openUiEditor();
                        else dispatch(id);
                    });
            android.widget.GridLayout.LayoutParams p = new android.widget.GridLayout.LayoutParams();
            p.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            p.width = 0;
            p.height = Theme.dp(this, 76);
            grid.addView(cell, p);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(grid);
        box.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().heightPixels * 0.4)));
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
        heldMenu = holder[0];
    }

    private void showMenuPager() {
        if (isFinishing() || isDestroyed()) return;
        final java.util.List<String> ids = new ArrayList<>(ui.menuItems);
        int tb = ids.indexOf("toolbox");
        ids.add(tb >= 0 ? tb + 1 : ids.size(), "editMenu");   // 「编辑菜单」紧挨工具箱右边
        final int perPage = 10;                        // 5 列 × 2 行
        final int pages = Math.max(1, (ids.size() + perPage - 1) / perPage);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 4), 0, Theme.dp(this, 4), Theme.dp(this, 12));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        final android.app.Dialog[] holder = new android.app.Dialog[1];

        final androidx.viewpager2.widget.ViewPager2 pager = new androidx.viewpager2.widget.ViewPager2(this);
        final int cellH = Theme.dp(this, 76);
        pager.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cellH * 2));
        pager.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                android.widget.GridLayout g = new android.widget.GridLayout(MainActivity.this);
                g.setColumnCount(5);
                g.setRowCount(2);
                // ViewPager2 要求每页填满自己，否则直接抛异常
                g.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(g) {};
            }

            @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder h, int position) {
                android.widget.GridLayout g = (android.widget.GridLayout) h.itemView;
                g.removeAllViews();
                int from = position * perPage;
                int to = Math.min(ids.size(), from + perPage);
                for (int i = from; i < to; i++) {
                    final String id = ids.get(i);
                    boolean isEdit = "editMenu".equals(id);
                    Actions.Def def = isEdit ? null : Actions.get(id);
                    if (!isEdit && def == null) continue;
                    String label = isEdit ? "定制菜单" : def.label;
                    View cell = com.vane.browser.ui.Sheet.cell(MainActivity.this, ui,
                            isEdit ? R.drawable.ic_edit : com.vane.browser.ui.ActionIcons.res(id),
                            label, !isEdit && isActionActive(id), () -> {
                                if (holder[0] != null) holder[0].dismiss();
                                if (isEdit) openUiEditor();
                                else dispatch(id);
                            });
                    android.widget.GridLayout.LayoutParams p = new android.widget.GridLayout.LayoutParams();
                    p.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
                    p.width = 0;
                    p.height = cellH;
                    g.addView(cell, p);
                }
            }

            @Override public int getItemCount() { return pages; }
        });
        box.addView(pager);

        final LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, Theme.dp(this, 6), 0, 0);
        box.addView(dots);

        final Runnable paintDots = () -> {
            dots.removeAllViews();
            if (pages <= 1) return;
            int cur = pager.getCurrentItem();
            for (int k = 0; k < pages; k++) {
                View d = new View(this);
                d.setBackground(Theme.rounded(k == cur ? Theme.fg(this, ui) : Theme.line(this, ui),
                        Theme.dp(this, 3)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        k == cur ? Theme.dp(this, 16) : Theme.dp(this, 6), Theme.dp(this, 6));
                lp.setMargins(Theme.dp(this, 3), 0, Theme.dp(this, 3), 0);
                dots.addView(d, lp);
            }
        };
        pager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { paintDots.run(); }
        });
        paintDots.run();

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
        heldMenu = holder[0];
    }


    // ================= 备份 / 导入 =================

    /** 备份：可选自己的 JSON、通用书签 HTML、纯网址清单。 */
    public void backupToFile() {
        Widgets.pick(this, ui, "备份成什么格式", java.util.Arrays.asList(
                "Vane备份（json，含设置和代理）",
                "书签 HTML（Chrome/Edge/Firefox/Via 都能导入）",
                "纯网址清单（txt，一行一个）"), i -> {
            if (i == 0) backupJson();
            else if (i == 1) exportBookmarksHtml();
            else exportUrlsTxt();
        });
    }

    private void backupJson() {
        try {
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("app", "vane");
            root.put("version", appVersionName());
            root.put("time", System.currentTimeMillis());
            root.put("bookmarks", Prefs.getArray("bookmarks"));
            root.put("history", Prefs.getArray("history"));
            root.put("home_cards", Prefs.getArray("home_cards"));
            org.json.JSONObject prefs = new org.json.JSONObject();
            for (String k : new String[]{"engine", "ua_mode", "no_history", "adblock",
                    "font_zoom", "dark_skip", "feedback_mail", "feedback_url", "update_manifest",
                    "update_auto", "home_page", "simple_mode", "ad_custom", "sub_auto",
                    // 一起带走：导出位置、拦截规则、跳转策略、白名单、订阅地址
                    "export_target", "ad_level", "open_link_mode", "whitelist", "sub_url",
                    "search_custom", "ad_vip_hide", "reader_confirm", "media_sniff"}) {
                prefs.put(k, Prefs.get(k, ""));
            }
            root.put("prefs", prefs);
            org.json.JSONObject theme = new org.json.JSONObject();
            theme.put("name", ui.theme);
            theme.put("accent", ui.accent);
            theme.put("customBg", ui.customBg);
            theme.put("topBarHeight", ui.topBarHeight);
            theme.put("bottomBarHeight", ui.bottomBarHeight);
            theme.put("corner", ui.corner);
            root.put("theme", theme);
            org.json.JSONObject uiJson = new org.json.JSONObject();
            org.json.JSONArray itemIds = new org.json.JSONArray();
            for (com.vane.browser.settings.UiConfig.Item it : ui.items) itemIds.put(it.id);
            uiJson.put("items", itemIds);
            uiJson.put("menuItems", new org.json.JSONArray(ui.menuItems));
            uiJson.put("linkMenu", new org.json.JSONArray(ui.linkMenu));
            org.json.JSONObject g2 = new org.json.JSONObject();
            for (java.util.Map.Entry<String, String> e : ui.gestures.entrySet()) g2.put(e.getKey(), e.getValue());
            uiJson.put("gestures", g2);
            root.put("ui", uiJson);
            org.json.JSONObject proxy = new org.json.JSONObject();
            proxy.put("subs", Prefs.getArray("subs"));
            proxy.put("nodes", Prefs.getArray("nodes"));
            proxy.put("mode", Prefs.get("proxy_mode", "rule"));
            root.put("proxy", proxy);
            saveToDownload("vane-backup-" + stamp() + ".json", "application/json", root.toString(2));
        } catch (Exception e) {
            Widgets.toast(this, "备份失败：" + e.getMessage());
        }
    }

    /** 书签导出成 Netscape 格式，任何浏览器都能导入。 */
    private void exportBookmarksHtml() {
        try {
            JSONArray arr = Prefs.getArray("bookmarks");
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
            sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
            sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                String title = o.optString("title", url);
                if (url.isEmpty()) continue;
                sb.append("    <DT><A HREF=\"").append(htmlEscape(url)).append("\">")
                        .append(htmlEscape(title)).append("</A>\n");
            }
            sb.append("</DL><p>\n");
            saveToDownload("vane-bookmarks-" + stamp() + ".html", "text/html", sb.toString());
        } catch (Exception e) {
            Widgets.toast(this, "导出失败：" + e.getMessage());
        }
    }

    private void exportUrlsTxt() {
        try {
            StringBuilder sb = new StringBuilder();
            JSONArray bm = Prefs.getArray("bookmarks");
            for (int i = 0; i < bm.length(); i++) {
                JSONObject o = bm.optJSONObject(i);
                if (o != null && !o.optString("url", "").isEmpty()) sb.append(o.optString("url")).append('\n');
            }
            JSONArray hs = Prefs.getArray("history");
            for (int i = 0; i < hs.length(); i++) {
                JSONObject o = hs.optJSONObject(i);
                if (o != null && !o.optString("url", "").isEmpty()) sb.append(o.optString("url")).append('\n');
            }
            saveToDownload("vane-urls-" + stamp() + ".txt", "text/plain", sb.toString());
        } catch (Exception e) {
            Widgets.toast(this, "导出失败：" + e.getMessage());
        }
    }

    private String stamp() {
        return new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.CHINA)
                .format(new java.util.Date());
    }

    private String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 书签/历史/离线：三标签 + 搜索 + 日期分组 + 「图标 标题 / 网址」+ 底部操作。 */
    private String buildLibraryHtml(int tab) {
        boolean dark = Theme.isDark(this, ui);
        String bg    = dark ? "#101114" : "#FFFFFF";
        String fg    = dark ? "#EDEEF0" : "#111111";
        String sub   = dark ? "#9AA0A6" : "#6B7280";
        String field = dark ? "#23252A" : "#F1F3F4";
        String line  = dark ? "#23252A" : "#EDEFF2";

        StringBuilder rows = new StringBuilder();
        int iconIdx = 0;
        lastIconHosts.clear();

        if (tab == 2) {
            File dir = new File(getFilesDir(), "offline");
            File[] files = dir.listFiles();
            if (files != null) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    String when = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                            .format(new java.util.Date(f.lastModified())) + " · " + (f.length() / 1024) + " KB";
                    rows.append("<div class=box><a class=hit href=\"vane://offline?path=")
                        .append(Uri.encode(f.getAbsolutePath())).append("\"></a>")
                        .append("<div class=txt><p class=t1>").append(htmlEscape(f.getName()))
                        .append("</p><p class=t2>").append(htmlEscape(when)).append("</p></div></div>");
                }
            }
        } else {
            JSONArray arr = Prefs.getArray(tab == 0 ? "bookmarks" : "history");
            String lastDay = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                if (url.isEmpty()) continue;
                if (url.startsWith("about:") || url.startsWith("data:") || url.startsWith("vane:")
                        || url.startsWith("file:")) continue;
                String title = o.optString("title", url);
                if ("about:blank".equals(title) || "local".equals(title)) continue;

                String day = "";
                long ts = o.optLong("t", o.optLong("time", 0));
                if (tab == 1 && ts > 0) {
                    java.util.Calendar c0 = java.util.Calendar.getInstance();
                    java.util.Calendar c1 = java.util.Calendar.getInstance();
                    c1.setTimeInMillis(ts);
                    if (c0.get(java.util.Calendar.YEAR) == c1.get(java.util.Calendar.YEAR)
                            && c0.get(java.util.Calendar.DAY_OF_YEAR) == c1.get(java.util.Calendar.DAY_OF_YEAR)) {
                        day = "今天";
                    } else {
                        c0.add(java.util.Calendar.DAY_OF_YEAR, -1);
                        if (c0.get(java.util.Calendar.YEAR) == c1.get(java.util.Calendar.YEAR)
                                && c0.get(java.util.Calendar.DAY_OF_YEAR) == c1.get(java.util.Calendar.DAY_OF_YEAR)) {
                            day = "昨天";
                        } else {
                            day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                                    .format(new java.util.Date(ts));
                        }
                    }
                }
                if (!day.isEmpty() && !day.equals(lastDay)) {
                    rows.append("<p class=day>").append(htmlEscape(day)).append("</p>");
                    lastDay = day;
                }
                String hostX = "";
                try {
                    hostX = Uri.parse(url).getHost() == null ? "" : Uri.parse(url).getHost();
                } catch (Exception ignored) {}
                lastIconHosts.add(hostX);
                String cached = cachedIconPath(hostX);
                rows.append("<div class=box><a class=hit href=\"vane://open?url=").append(Uri.encode(url))
                    .append("\"></a><span class=ic>")
                    .append(cached != null
                            ? "<img src=\"" + cached + "\">"
                            : htmlEscape(title.isEmpty() ? "?" : title.substring(0, 1)))
                    .append("</span><div class=txt><p class=t1>").append(htmlEscape(title)).append("</p>")
                    .append("<p class=t2>").append(htmlEscape(url)).append("</p></div>")
                    .append("<span class=del onclick=\"del('").append(tab == 0 ? "b" : "h").append("','")
                    .append(htmlEscape(url)).append("')\">✕</span></div>");
                iconIdx++;
            }
        }
        String empty = rows.length() == 0
                ? "<div class=hint>" + (tab == 2 ? "还没有离线页面" : "还没有记录") + "</div>" : "";

        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box}"
                + "html,body{background:" + bg + ";color:" + fg + ";font:15px/1.45 system-ui,sans-serif;"
                + "-webkit-tap-highlight-color:transparent}"
                + "body{max-width:100%;width:600px;margin:auto;padding-bottom:64px}"
                + ".tabs{display:flex;gap:18px;padding:14px 14px 6px;font-size:14px}"
                + ".tabs a{color:" + sub + ";text-decoration:none;padding:4px 0}"
                + ".tabs a.on{color:" + fg + ";font-weight:600;border-bottom:2px solid " + fg + "}"
                + "#q{width:calc(100% - 28px);margin:8px 14px 6px;padding:10px 13px;border:0;border-radius:10px;"
                + "background:" + field + ";color:" + fg + ";font-size:14px;outline:none}"
                + ".day{color:" + sub + ";font-size:12px;padding:12px 14px 2px}"
                + ".box{position:relative;display:flex;align-items:center;padding:11px 14px}"
                + ".hit{position:absolute;left:0;top:0;width:100%;height:100%}"
                + ".ic{flex:0 0 22px;width:22px;height:22px;margin-right:11px;display:flex;align-items:center;"
                + "justify-content:center;color:" + sub + ";font-size:13px;overflow:hidden}"
                + ".ic img{width:20px;height:20px;object-fit:contain;display:block}"
                + ".txt{flex:1;min-width:0}"
                + ".t1{color:" + fg + ";font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
                + ".t2{color:" + sub + ";font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}"
                + ".del{position:relative;z-index:2;color:" + sub + ";padding:8px 0 8px 10px;font-size:13px}"
                + ".hint{color:" + sub + ";font-size:14px;padding:60px 10px;text-align:center}"
                + ".bar{position:fixed;left:0;right:0;bottom:0;display:flex;background:" + bg
                + ";border-top:1px solid " + line + "}"
                + ".bar a{flex:1;text-align:center;color:" + sub + ";text-decoration:none;font-size:13px;padding:13px 0}"
                + "</style></head><body>"
                + "<div class=tabs>"
                + "<a class='" + (tab == 0 ? "on" : "") + "' href='vane://lib?tab=0'>书签</a>"
                + "<a class='" + (tab == 1 ? "on" : "") + "' href='vane://lib?tab=1'>历史</a>"
                + "<a class='" + (tab == 2 ? "on" : "") + "' href='vane://lib?tab=2'>离线页面</a></div>"
                + "<input id=q placeholder='搜索' oninput=\"filt(this.value)\">"
                + rows + empty
                + "<div class=bar><a href='vane://lib?tab=" + tab + "'>刷新</a>"
                + "<a href='vane://clear?t=" + (tab == 0 ? "b" : "h") + "'>清空</a></div>"
                + "<script>function filt(v){v=v.toLowerCase();document.querySelectorAll('.box').forEach(function(b){"
                + "b.style.display=(!v||b.innerText.toLowerCase().indexOf(v)>=0)?'':'none';});}"
                + "function del(t,u){location.href='vane://del?t='+t+'&url='+encodeURIComponent(u);}"
                + "function setIcon(i,src){var e=document.getElementById('ic'+i);"
                + "if(e){e.innerHTML='<img src=\"'+src+'\">';}}</script>"
                + "</body></html>";
    }

    /** 用页面行序取图标：先本地缓存，再联网补，注入到对应 id。 */
    private void injectFavicons(final android.webkit.WebView web) {
        final java.util.List<String> hosts = new ArrayList<>(lastIconHosts);
        if (hosts.isEmpty()) return;
        new Thread(() -> {
            java.util.LinkedHashMap<Integer, String> pairs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < hosts.size() && i < 60; i++) {
                String host = hosts.get(i);
                if (host == null || host.isEmpty()) continue;
                String data = com.vane.browser.core.Favicons.toDataUri(
                        com.vane.browser.core.Favicons.get(this, host));
                if (data == null) {
                    data = fetchAsDataUri("https://" + host + "/favicon.ico");
                    if (data == null) data = fetchAsDataUri("http://" + host + "/favicon.ico");
                    if (data != null) {
                        try {
                            byte[] raw = android.util.Base64.decode(
                                    data.substring(data.indexOf(',') + 1), android.util.Base64.DEFAULT);
                            com.vane.browser.core.Favicons.save(this, host,
                                    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length));
                        } catch (Exception ignored) {}
                    }
                }
                if (data != null) pairs.put(i, data);
            }
            if (pairs.isEmpty()) return;
            StringBuilder js = new StringBuilder("(function(){");
            for (java.util.Map.Entry<Integer, String> e : pairs.entrySet()) {
                js.append("setIcon(").append(e.getKey()).append(",'").append(e.getValue()).append("');");
            }
            js.append("})();");
            try {
                web.evaluateJavascript(js.toString(), null);
            } catch (Throwable ignored) {}
        }, "favicons").start();
    }

    /** 已缓存的图标文件路径（给页面直接用，不再注入）。 */
    private String cachedIconPath(String host) {
        if (com.vane.browser.core.Favicons.get(this, host) == null) return null;
        File f = new File(new File(getFilesDir(), "favicons"),
                host.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png");
        return f.exists() ? "file://" + f.getAbsolutePath() : null;
    }

    /** 后台补齐缺失的图标（不阻塞界面，抓完下次刷新显示）。 */
    private void prefetchIcons() {
        final java.util.List<String> hosts = new ArrayList<>(lastIconHosts);
        new Thread(() -> {
            for (String host : hosts) {
                if (host == null || host.isEmpty()) continue;
                if (com.vane.browser.core.Favicons.get(this, host) != null) continue;
                String data = fetchAsDataUri("https://" + host + "/favicon.ico");
                if (data == null) data = fetchAsDataUri("http://" + host + "/favicon.ico");
                if (data == null) continue;
                try {
                    byte[] raw = android.util.Base64.decode(
                            data.substring(data.indexOf(',') + 1), android.util.Base64.DEFAULT);
                    com.vane.browser.core.Favicons.save(this, host,
                            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length));
                } catch (Exception ignored) {}
            }
        }, "prefetch-icons").start();
    }


    private String fetchAsDataUri(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setInstanceFollowRedirects(true);
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            byte[] data = b.toByteArray();
            if (data.length < 32 || data.length > 200000) return null;
            String mime = "image/png";
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) mime = "image/jpeg";
            return "data:" + mime + ";base64,"
                    + android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }


    /** 在当前标签里显示内置页（书签/历史）。 */
    private void showLibraryPage(int tab) {
        BrowserTab t = currentTab();
        if (t == null) return;
        t.internalLabel = tab == 0 ? "书签" : (tab == 1 ? "历史记录" : "离线页面");
        if (progress != null) progress.setVisibility(View.GONE);
        try {
            File f = new File(getCacheDir(), "library-" + tab + ".html");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(buildLibraryHtml(tab).getBytes("UTF-8"));
            fos.close();
            t.web.getSettings().setAllowFileAccess(true);
            t.web.getSettings().setAllowFileAccessFromFileURLs(true);
            t.web.loadUrl("file://" + f.getAbsolutePath());
        } catch (Exception e) {
            t.web.loadDataWithBaseURL(null, buildLibraryHtml(tab), "text/html", "utf-8", null);
        }
        prefetchIcons();
        if (address != null) {
            address.setText(t.internalLabel);
            address.clearFocus();
        }
        onTabStateChanged();
    }

    /** 保存到「下载/Vane」：Android 10+ 走 MediaStore，10 以下写真实路径。 */
    /** 应用内的导出文件列表：可以分享出去，也可以删。 */
    public void showExports() {
        final File dir = new File(getFilesDir(), "exports");
        File[] all = dir.listFiles();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        box.addView(Widgets.text(this, "导出的文件", 18, Theme.fg(this, ui)));
        TextView pathHint = Widgets.text(this,
                "app".equals(Prefs.get("export_target", "download"))
                        ? "位置：应用内部 files/exports/"
                        : "位置：手机存储/下载/Vane/",
                11, Theme.hint(this, ui));
        pathHint.setPadding(0, Theme.dp(this, 4), 0, Theme.dp(this, 8));
        box.addView(pathHint);
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        if (all == null || all.length == 0) {
            TextView e = Widgets.text(this, "还没有内容", 14, Theme.hint(this, ui));
            e.setPadding(0, Theme.dp(this, 20), 0, Theme.dp(this, 20));
            box.addView(e);
        } else {
            java.util.Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (final File f : all) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Theme.dp(this, 12), 0, Theme.dp(this, 12));
                LinearLayout t = new LinearLayout(this);
                t.setOrientation(LinearLayout.VERTICAL);
                t.addView(Widgets.text(this, trim(f.getName(), 30), 14, Theme.fg(this, ui)));
                t.addView(Widgets.text(this, (f.length() / 1024) + " KB", 11, Theme.hint(this, ui)));
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Widgets.text(this, "分享", 13, Theme.accent(ui)));
                row.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    shareFile(f);
                });
                row.setOnLongClickListener(v -> {
                    Widgets.confirm(this, ui, "删掉这个文件？", f.getName(), () -> {
                        f.delete();
                        if (holder[0] != null) holder[0].dismiss();
                    });
                    return true;
                });
                box.addView(row);
            }
        }
        if (all != null && all.length > 0) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Theme.dp(this, 12);
            box.addView(Widgets.ghostButton(this, ui, "清空全部", () -> {
                Widgets.confirm(this, ui, "删掉所有导出文件？", "共 " + all.length + " 个，删了就没了。", () -> {
                    for (File f : all) f.delete();
                    if (holder[0] != null) holder[0].dismiss();
                });
            }), blp);
        }
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    /** 通过安全 URI 分享应用内部文件。 */
    private void shareFile(File f) {
        try {
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(f.getName().endsWith(".json") ? "application/json"
                    : f.getName().endsWith(".html") ? "text/html" : "text/plain");
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "分享文件"));
        } catch (Exception e) {
            Widgets.toast(this, "分享失败：" + e.getMessage());
        }
    }

    /** 导出到应用内部（不写手机下载目录），随后可分享出去。 */
    private File saveToApp(String name, String mime, String content) throws Exception {
        if (!"app".equals(Prefs.get("export_target", "download"))) {
            File dl = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Vane");
            if (!dl.exists()) dl.mkdirs();
            File f = new File(dl, name);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(content.getBytes("UTF-8"));
            fo.close();
            return f;
        }
        File dir = new File(getFilesDir(), "exports");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, name);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
        fos.write(content.getBytes("UTF-8"));
        fos.close();
        App.logEvent(this, "export", f.getName());
        return f;
    }

    private void saveToDownload(String name, String mime, String content) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Vane");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            android.media.MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()},
                    new String[]{mime}, null);
            Widgets.info(this, ui, "已导出", f.getAbsolutePath());
            return;
        }
        saveByMediaStore(name, mime, content);
    }

    private void saveByMediaStore(String name, String mime, String content) throws Exception {
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
        cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/Vane");
        android.net.Uri uri = getContentResolver()
                .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) {
            Widgets.toast(this, "写不进去");
            return;
        }
        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
        os.write(content.getBytes("UTF-8"));
        os.close();
        Widgets.info(this, ui, "已导出", "下载/Vane/" + name);
    }

    public void restoreFromFile() {
        Widgets.pick(this, ui, "从哪里恢复", java.util.Arrays.asList(
                "本机备份列表（下载/Vane）", "系统文件选择器"), k -> {
            if (k == 0) pickBackupFromDownloads();
            else {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_RESTORE);
                } catch (Exception e) {
                    Widgets.toast(this, "打不开选择器");
                }
            }
        });
    }

    /** 把应用自己导出到 下载/Vane 的备份列出来选，绕开系统选择器只显示「最近」的毛病。 */
    private void pickBackupFromDownloads() {
        final java.util.List<String> names = new java.util.ArrayList<>();
        final java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.net.Uri base = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String[] proj = {android.provider.MediaStore.MediaColumns._ID,
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        android.provider.MediaStore.MediaColumns.SIZE};
                try (android.database.Cursor cur = getContentResolver().query(base, proj,
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'vane-backup-%' OR "
                                + android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'vane-bookmarks-%' OR "
                                + android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'vane-urls-%' OR "
                                + android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'vane-theme-%'",
                        null, android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                    while (cur != null && cur.moveToNext() && names.size() < 40) {
                        long id = cur.getLong(0);
                        names.add(cur.getString(1) + " · " + (cur.getLong(2) / 1024) + " KB");
                        uris.add(android.content.ContentUris.withAppendedId(base, id));
                    }
                }
            } catch (Exception ignored) {}
        }
        if (names.isEmpty()) {
            try {
                File dl = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), "Vane");
                File[] fs = dl.listFiles();
                if (fs != null) {
                    java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File f : fs) {
                        String nm = f.getName();
                        boolean pick = nm.startsWith("vane-backup-") || nm.startsWith("vane-bookmarks-")
                                || nm.startsWith("vane-urls-") || nm.startsWith("vane-theme-");
                        if (!pick || names.size() >= 40) continue;
                        names.add(f.getName() + " · " + (f.length() / 1024) + " KB");
                        uris.add(android.net.Uri.fromFile(f));
                    }
                }
            } catch (Exception ignored) {}
        }
        if (names.isEmpty()) {
            Widgets.toast(this, "下载/Vane 里还没有备份");
            return;
        }
        Widgets.pick(this, ui, "选一个备份/书签文件", names, i -> applyRestore(uris.get(i)));
    }

    private void applyRestore(android.net.Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            String text = b.toString("UTF-8");
            String t = text.trim();
            int added = 0;
            if (t.contains("NETSCAPE-Bookmark") || t.contains("<DT") || t.toLowerCase().contains("<a href")) {
                added = importHtmlBookmarks(text);
            } else if (t.startsWith("{")) {
                org.json.JSONObject root = new org.json.JSONObject(t);
                if ("vane".equals(root.optString("app"))) {
                    int addedBm = importVaneBackup(root);
                    rebuildChrome();
                    Widgets.info(this, ui, "备份已恢复",
                            "书签新增 " + addedBm + " 条\n历史、设置、主题、代理已按备份覆盖。");
                    return;
                }
                if (root.has("roots")) added = importChromeJson(root);
                else if (root.has("bookmarks")) added = importGenericJson(root.optJSONArray("bookmarks"));
                else added = importGenericJson(new org.json.JSONArray().put(root));
            } else {
                added = importPlainList(text);
            }
            if (added == 0) {
                Widgets.info(this, ui, "没有新增",
                        "这个文件解析成功，但里面的书签本机都有了（去重后 0 条新增）。");
                return;
            }
            Widgets.info(this, ui, "导入完成", "新增 " + added + " 条书签。");
            rebuildChrome();
        } catch (Exception e) {
            Widgets.toast(this, "导入失败：" + e.getMessage());
        }
    }

    private int mergeBookmarks(List<String[]> entries) {
        JSONArray arr = Prefs.getArray("bookmarks");
        java.util.Set<String> have = new java.util.HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) have.add(o.optString("url", ""));
        }
        int added = 0;
        for (String[] e : entries) {
            if (e == null || e[0] == null || e[0].isEmpty() || have.contains(e[0])) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("url", e[0]);
                o.put("title", e[1] == null || e[1].isEmpty() ? e[0] : e[1]);
                arr.put(o);
                have.add(e[0]);
                added++;
            } catch (Exception ignored) {}
        }
        Prefs.putArray("bookmarks", arr);
        return added;
    }

    private int importHtmlBookmarks(String html) {
        List<String[]> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<A[^>]*HREF=\"([^\"]+)\"[^>]*>(.*?)</A>",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(html);
        while (m.find()) {
            out.add(new String[]{m.group(1), m.group(2).replaceAll("<[^>]+>", "").trim()});
        }
        return mergeBookmarks(out);
    }

    private int importChromeJson(org.json.JSONObject root) {
        List<String[]> out = new ArrayList<>();
        walkChrome(root.optJSONObject("roots"), out);
        return mergeBookmarks(out);
    }

    private void walkChrome(org.json.JSONObject node, List<String[]> out) {
        if (node == null) return;
        java.util.Iterator<String> it = node.keys();
        while (it.hasNext()) {
            Object v = node.opt(it.next());
            if (v instanceof org.json.JSONObject) {
                org.json.JSONObject o = (org.json.JSONObject) v;
                if ("url".equals(o.optString("type"))) {
                    out.add(new String[]{o.optString("url"), o.optString("name")});
                }
                walkChrome(o, out);
            }
        }
    }

    private int importGenericJson(org.json.JSONArray arr) {
        List<String[]> out = new ArrayList<>();
        if (arr == null) return 0;
        for (int i = 0; i < arr.length(); i++) {
            Object v = arr.opt(i);
            if (v instanceof org.json.JSONObject) {
                org.json.JSONObject o = (org.json.JSONObject) v;
                out.add(new String[]{o.optString("url"), o.optString("title", o.optString("name"))});
            } else if (v instanceof String) {
                out.add(new String[]{(String) v, ""});
            }
        }
        return mergeBookmarks(out);
    }

    private int importPlainList(String text) {
        List<String[]> out = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            String u = line.trim();
            if (u.startsWith("http")) out.add(new String[]{u, ""});
        }
        return mergeBookmarks(out);
    }

    private int importVaneBackup(org.json.JSONObject root) {
        int added = mergeBookmarks(jsonToEntries(root.optJSONArray("bookmarks")));
        org.json.JSONArray hs = root.optJSONArray("history");
        if (hs != null) Prefs.putArray("history", hs);
        org.json.JSONArray hc = root.optJSONArray("home_cards");
        if (hc != null) {
            Prefs.putArray("home_cards", hc);
            try { pushHomeCards(); } catch (Throwable ignored) {}
        }
        org.json.JSONObject prefs = root.optJSONObject("prefs");
        if (prefs != null) {
            java.util.Iterator<String> it = prefs.keys();
            while (it.hasNext()) {
                String k = it.next();
                Prefs.put(k, prefs.optString(k, ""));
            }
        }
        org.json.JSONObject theme = root.optJSONObject("theme");
        if (theme != null) {
            ui.theme = theme.optString("name", ui.theme);
            ui.accent = theme.optString("accent", ui.accent);
            ui.customBg = theme.optString("customBg", ui.customBg);
            ui.topBarHeight = theme.optInt("topBarHeight", ui.topBarHeight);
            ui.bottomBarHeight = theme.optInt("bottomBarHeight", ui.bottomBarHeight);
            ui.corner = theme.optInt("corner", ui.corner);
            ui.save();
        }
        org.json.JSONObject uiRoot = root.optJSONObject("ui");
        if (uiRoot != null) {
            org.json.JSONArray mi2 = uiRoot.optJSONArray("menuItems");
            if (mi2 != null) {
                ui.menuItems = new ArrayList<>();
                for (int i = 0; i < mi2.length(); i++) ui.menuItems.add(mi2.optString(i));
            }
            org.json.JSONArray lm2 = uiRoot.optJSONArray("linkMenu");
            if (lm2 != null) {
                ui.linkMenu = new ArrayList<>();
                for (int i = 0; i < lm2.length(); i++) ui.linkMenu.add(lm2.optString(i));
            }
            org.json.JSONObject g3 = uiRoot.optJSONObject("gestures");
            if (g3 != null) {
                for (java.util.Iterator<String> it2 = g3.keys(); it2.hasNext(); ) {
                    String k2 = it2.next();
                    ui.gestures.put(k2, g3.optString(k2, ""));
                }
            }
            ui.save();
        }
        org.json.JSONObject proxy = root.optJSONObject("proxy");
        if (proxy != null) {
            org.json.JSONArray subs = proxy.optJSONArray("subs");
            if (subs != null) Prefs.putArray("subs", subs);
            org.json.JSONArray nodes = proxy.optJSONArray("nodes");
            if (nodes != null) Prefs.putArray("nodes", nodes);
            Prefs.put("proxy_mode", proxy.optString("mode", "rule"));
        }
        return added;
    }

    private List<String[]> jsonToEntries(org.json.JSONArray arr) {
        List<String[]> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(new String[]{o.optString("url"), o.optString("title")});
        }
        return out;
    }

    // ================= 主页快捷入口 =================

    /**
     * 拖动排序：长按某一行开始拖，落到别的行上就换位置。
     * 用系统 DragAndDrop（同一窗口内），松手即写回 Prefs 并刷新主页方块。
     */
    private void showCardReorder() {
        final JSONArray arr = Prefs.getArray("home_cards");
        if (arr.length() < 2) {
            Widgets.toast(this, "至少两个入口才能排序");
            return;
        }
        final List<JSONObject> cards = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) cards.add(o);
        }
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 8);
        box.setPadding(pad, pad, pad, pad);
        final android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("拖动排序（长按条目拖到新位置）")
                .setView(sv)
                .setPositiveButton("完成", (d, w) -> {
                    JSONArray fresh = new JSONArray();
                    for (JSONObject o : cards) fresh.put(o);
                    Prefs.putArray("home_cards", fresh);
                    pushHomeCards();
                    Widgets.toast(this, "顺序已保存");
                })
                .create();
        final int[] dragFrom = {-1};
        final Runnable[] build = new Runnable[1];
        build[0] = () -> {
            box.removeAllViews();
            for (int i = 0; i < cards.size(); i++) {
                final int idx = i;
                final JSONObject card = cards.get(i);
                TextView row = Widgets.text(this, "☰  " + card.optString("title"), 16, Theme.fg(this, ui));
                row.setPadding(Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16));
                // 点一下 = 上移一位（拖不动时的兜底，也方便点按操作）
                row.setOnClickListener(v -> {
                    if (idx == 0) { Widgets.toast(this, "已经在最上面"); return; }
                    JSONObject moved = cards.remove(idx);
                    cards.add(idx - 1, moved);
                    JSONArray fresh = new JSONArray();
                    for (JSONObject o : cards) fresh.put(o);
                    Prefs.putArray("home_cards", fresh);
                    build[0].run();
                    pushHomeCards();
                });
                row.setOnLongClickListener(v -> {
                    dragFrom[0] = idx;
                    v.setAlpha(0.4f);
                    android.content.ClipData cd =
                            android.content.ClipData.newPlainText("card", String.valueOf(idx));
                    v.startDragAndDrop(cd, new android.view.View.DragShadowBuilder(v), null, 0);
                    return true;
                });
                row.setOnDragListener((v, ev) -> {
                    if (dragFrom[0] < 0) return false;
                    if (ev.getAction() == android.view.DragEvent.ACTION_DROP) {
                        int from = dragFrom[0];
                        int to = idx > from ? idx - 1 : idx;     // 抽出后索引会前移
                        dragFrom[0] = -1;
                        if (to == from) { build[0].run(); return true; }
                        JSONObject moved = cards.remove(from);
                        cards.add(Math.max(0, Math.min(cards.size(), to)), moved);
                        JSONArray fresh = new JSONArray();
                        for (JSONObject o : cards) fresh.put(o);
                        Prefs.putArray("home_cards", fresh);
                        build[0].run();
                        pushHomeCards();
                        return true;
                    }
                    return true;
                });
                box.addView(row);
            }
        };
        build[0].run();
        dlg.show();
    }

    public void editHomeCards() {
        final JSONArray arr = Prefs.getArray("home_cards");
        List<String> items = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) items.add(o.optString("title", o.optString("url")));
        }
        items.add("☰ 拖动排序");
        items.add("＋ 添加一个入口");
        Widgets.pick(this, ui, "主页快捷入口", items, idx -> {
            if (idx == arr.length()) {
                showCardReorder();
                return;
            }
            if (idx == arr.length() + 1) {
                Widgets.input(this, ui, "名称（如 知乎）", "", false, name ->
                        Widgets.input(this, ui, "网址", "https://", false, url -> {
                            try {
                                String u = url == null ? "" : url.trim();
                                if (!u.contains("://")) u = "https://" + u;
                                String t = name == null ? "" : name.trim();
                                if (t.isEmpty()) {
                                    // 没填名字就用域名兜底，别出现空白格子
                                    try { t = android.net.Uri.parse(u).getHost(); } catch (Exception ignored) {}
                                    if (t == null || t.isEmpty()) t = "入口";
                                }
                                JSONObject o = new JSONObject();
                                o.put("title", t);
                                o.put("url", u);
                                arr.put(o);
                                Prefs.putArray("home_cards", arr);
                                pushHomeCards();
                                Widgets.toast(this, "已添加");
                            } catch (Exception ignored) {}
                        }));
                return;
            }
            JSONObject o = arr.optJSONObject(idx);
            if (o == null) return;
            Widgets.pick(this, ui, o.optString("title"), java.util.Arrays.asList(
                    "删除", "改名", "上移", "下移"), k -> {
                try {
                    if (k == 2 || k == 3) {
                        int to = k == 2 ? idx - 1 : idx + 1;
                        if (to >= 0 && to < arr.length()) {
                            JSONArray fresh = new JSONArray();
                            for (int i = 0; i < arr.length(); i++) {
                                if (i == idx) fresh.put(arr.optJSONObject(to));
                                else if (i == to) fresh.put(arr.optJSONObject(idx));
                                else fresh.put(arr.optJSONObject(i));
                            }
                            Prefs.putArray("home_cards", fresh);
                            pushHomeCards();
                            Widgets.toast(this, k == 2 ? "上移一位" : "下移一位");
                        } else {
                            Widgets.toast(this, k == 2 ? "已经是第一个" : "已经是最后一个");
                        }
                        return;
                    }
                    if (k == 0) {
                        // 删除要二次确认：误点一下卡片就没了，标题还容易点错
                        Widgets.confirm(this, ui, "删除这个入口？",
                                o.optString("title", "") + "\n" + o.optString("url", ""), () -> {
                                    JSONArray fresh = new JSONArray();
                                    for (int i = 0; i < arr.length(); i++)
                                        if (i != idx) fresh.put(arr.optJSONObject(i));
                                    Prefs.putArray("home_cards", fresh);
                                    pushHomeCards();
                                    Widgets.toast(this, "已删除");
                                });
                        return;
                    } else {
                        Widgets.input(this, ui, "新名字", o.optString("title"), false, v -> {
                            String nv = v == null ? "" : v.trim();
                            if (nv.isEmpty()) {
                                Widgets.toast(this, "名字不能为空");
                                return;
                            }
                            try {
                                o.put("title", nv);
                                Prefs.putArray("home_cards", arr);
                            } catch (Exception ignored) {}
                            pushHomeCards();
                        });
                    }
                    pushHomeCards();
                    Widgets.toast(this, "已更新");
                } catch (Exception ignored) {}
            });
        });
    }

    /** 主页方块 = 书签（Via 的做法）。 */
    /**
     * 主页方块：先放「主页快捷入口」里用户自己加的（home_cards），再用最近书签补满 12 个。
     * 以前这里只读书签，设置里加的入口根本不会出现在主页。
     */
    private String cardsForHome() {
        JSONArray out = new JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        JSONArray cards = Prefs.getArray("home_cards");
        for (int i = 0; i < cards.length() && out.length() < 12; i++) {
            JSONObject o = cards.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", "");
            if (url.isEmpty() || seen.contains(url)) continue;
            try {
                JSONObject c = new JSONObject();
                c.put("url", url);
                c.put("title", o.optString("title", url));
                out.put(c);
                seen.add(url);
            } catch (Exception ignored) {}
        }
        JSONArray arr = Prefs.getArray("bookmarks");
        for (int i = arr.length() - 1; i >= 0 && out.length() < 12; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", "");
            if (url.isEmpty() || seen.contains(url)) continue;
            try {
                JSONObject c = new JSONObject();
                c.put("url", url);
                c.put("title", o.optString("title", url));
                out.put(c);
                seen.add(url);
            } catch (Exception ignored) {}
        }
        return out.toString();
    }

    private String cardsFromBookmarks() {
        JSONArray arr = Prefs.getArray("bookmarks");
        JSONArray out = new JSONArray();
        for (int i = arr.length() - 1; i >= 0 && out.length() < 12; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", "");
            if (url.isEmpty()) continue;
            try {
                JSONObject c = new JSONObject();
                c.put("url", url);
                c.put("title", o.optString("title", url));
                out.put(c);
            } catch (Exception ignored) {}
        }
        return out.toString();
    }

    private void pushHomeCards() {
        BrowserTab t = currentTab();
        if (t == null) return;
        t.web.evaluateJavascript("window.setCards && window.setCards("
                + cardsForHome() + ")", null);
    }

    // ================= 订阅自动更新 =================

    private void autoRefreshSubs() {
        if (!Prefs.getBool("sub_auto", true)) return;
        JSONArray subs = Prefs.getArray("subs");
        if (subs.length() == 0) return;
        long last = 0;
        try {
            last = Long.parseLong(Prefs.get("sub_last_refresh", "0"));
        } catch (Exception ignored) {}
        if (System.currentTimeMillis() - last < 24L * 3600 * 1000) return;

        com.vane.browser.proxy.ProxyManager pm = com.vane.browser.proxy.ProxyManager.get(this);
        for (int i = 0; i < subs.length(); i++) {
            JSONObject o = subs.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", "");
            if (url.isEmpty()) continue;
            com.vane.browser.proxy.Subscription sub = new com.vane.browser.proxy.Subscription();
            sub.url = url;
            final boolean lastOne = i == subs.length() - 1;
            pm.refreshSubscription(sub, (ok, msg) -> {
                if (lastOne) {
                    Prefs.put("sub_last_refresh", String.valueOf(System.currentTimeMillis()));
                    runOnUiThread(() -> Widgets.toast(this, "订阅已自动更新"));
                }
            });
        }
    }

    private android.app.Dialog heldMenu;


    public void toggleIncognito() {
        boolean on = !Prefs.getBool("no_history", false);
        Prefs.putBool("no_history", on);
    }

    public void closeOtherTabs() {
        for (int i = tabs.size() - 1; i >= 0; i--) if (i != current) closeTab(i);
        selectTab(0);
    }

    public void closeLeftTabs() {
        for (int i = current - 1; i >= 0; i--) closeTab(i);
        selectTab(0);
    }

    public void closeRightTabs() {
        for (int i = tabs.size() - 1; i > current; i--) closeTab(i);
    }

    /** 看图模式：抓本页所有图片，网格展示。 */
    public void showImageList() {
        final BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        t.web.evaluateJavascript(
                "(function(){var out=[];document.querySelectorAll('img').forEach(function(i){"
                        + "var s=i.currentSrc||i.src;if(s&&s.indexOf('http')===0&&out.length<120)out.push(s);});"
                        + "return JSON.stringify(out);})()",
                value -> {
                    String raw = value;
                    try {
                        raw = new org.json.JSONArray("[" + value + "]").optString(0);
                    } catch (Exception ignored) {}
                    java.util.List<String> urls = new ArrayList<>();
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(raw);
                        for (int i = 0; i < arr.length(); i++) urls.add(arr.optString(i));
                    } catch (Exception ignored) {}
                    if (urls.isEmpty()) {
                        Widgets.toast(this, "本页没有图片");
                        return;
                    }
                    showImageGrid(urls);
                });
    }

    private void showImageGrid(final java.util.List<String> urls) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 14), 0, Theme.dp(this, 14), Theme.dp(this, 14));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        TextView title = Widgets.text(this, "本页图片 · " + urls.size() + " 张", 16, Theme.fg(this, ui));
        title.setPadding(0, 0, 0, Theme.dp(this, 10));
        box.addView(title);

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3);
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        for (final String u : urls) {
            android.widget.FrameLayout cell = new android.widget.FrameLayout(this);
            cell.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 10)));
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            com.vane.browser.ui.ImageLoader.load(iv, u, R.drawable.ic_image_off);
            cell.addView(iv, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            cell.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                Widgets.pick(this, ui, "图片", java.util.Arrays.asList("新标签打开", "保存图片", "复制地址"), k -> {
                    if (k == 0) newTab(u, true);
                    else if (k == 1) askDownloadTarget(u);
                    else copyText(u);
                });
            });
            android.widget.GridLayout.LayoutParams p = new android.widget.GridLayout.LayoutParams();
            p.width = (getResources().getDisplayMetrics().widthPixels - Theme.dp(this, 60)) / 3;
            p.height = p.width;
            p.setMargins(Theme.dp(this, 4), Theme.dp(this, 4), Theme.dp(this, 4), Theme.dp(this, 4));
            grid.addView(cell, p);
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(grid);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.6)));
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    /** 游戏模式：只能连按两次返回才退出。 */
    public void toggleGameMode() {
        boolean on = !Prefs.getBool("game_mode", false);
        Prefs.putBool("game_mode", on);
            }

    /** 撤销关闭：把最近关掉的标签开回来。 */
    public void reopenClosed() {
        if (recentlyClosed.isEmpty()) {
            Widgets.toast(this, "没有刚关掉的标签");
            return;
        }
        String[] last = recentlyClosed.remove(0);
        newTab(last[0], true);
    }

    /** 全部关闭，只留一个主页。 */
    public void closeAllTabs() {
        for (int i = tabs.size() - 1; i >= 0; i--) closeTab(i);
    }

    // ================= 工具箱 =================

    public void showTools() {
        if (isFinishing() || isDestroyed()) return;
        final String[][] groups = buildToolGroups();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        TextView title = Widgets.text(this, "工具箱", 18, Theme.fg(this, ui));
        title.setPadding(0, 0, 0, Theme.dp(this, 6));
        box.addView(title);
        TextView hint = Widgets.text(this, "先选一类，再选具体功能", 12, Theme.hint(this, ui));
        hint.setPadding(0, 0, 0, Theme.dp(this, 12));
        box.addView(hint);

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        LinearLayout groupBox = new LinearLayout(this);
        groupBox.setOrientation(LinearLayout.VERTICAL);
        for (final String[] g : groups) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(this, 14), 0, Theme.dp(this, 14));

            android.widget.ImageView iv = new android.widget.ImageView(this);
            int res = getResources().getIdentifier(g[0], "drawable", getPackageName());
            iv.setImageResource(res == 0 ? R.drawable.ic_menu : res);
            iv.setColorFilter(Theme.fg(this, ui));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Theme.dp(this, 24), Theme.dp(this, 24));
            ilp.setMargins(0, 0, Theme.dp(this, 16), 0);
            row.addView(iv, ilp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Widgets.text(this, g[1], 16, Theme.fg(this, ui)));
            TextView d = Widgets.text(this, g[2], 12, Theme.hint(this, ui));
            d.setPadding(0, Theme.dp(this, 2), 0, 0);
            texts.addView(d);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Widgets.text(this, "›", 16, Theme.hint(this, ui)));

            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                if ("@ADV@".equals(g[3])) {
                    showAdvCategories();
                    return;
                }
                if ("@MORE@".equals(g[3])) {
                    java.util.List<String> rest = new ArrayList<>();
                    java.util.Set<String> used = new java.util.HashSet<>();
                    for (String[] gg : groups) {
                        if (!"@MORE@".equals(gg[3])) java.util.Collections.addAll(used, gg[3].split(","));
                    }
                    for (com.vane.browser.core.Actions.Def it : com.vane.browser.core.Actions.all()) {
                        if (!used.contains(it.id)) rest.add(it.id);
                    }
                    if (rest.isEmpty()) return;
                    showToolsGroup(g[1], rest.toArray(new String[0]), true);
                } else {
                    showToolsGroup(g[1], g[3].split(","), true);
                }
            });
            groupBox.addView(row);
            View line = new View(this);
            line.setBackgroundColor(Theme.line(this, ui));
            groupBox.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(this, 1) / 2 + 1)));
        }
        ScrollView gsv = new ScrollView(this);
        gsv.setVerticalScrollBarEnabled(false);
        gsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gsv.addView(groupBox);
        int gh = (int) (getResources().getDisplayMetrics().heightPixels * 0.62);
        box.addView(gsv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gh));

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    /** 高级：按类别列出所有未上主栏的功能。 */
    private void showAdvCategories() {
        java.util.Set<String> primary = new java.util.HashSet<>(java.util.Arrays.asList(
                "proxy",
                "adblock", "adLevel", "adCheck", "adStats",
                "copyUrl", "translate", "save", "reader",
                "library", "downloads", "offline", "addBookmark",
                "scanQr", "speak", "imageList", "longShot",
                "settings", "backup", "restore", "checkUpdate"));

        String[][] cats = {
                {"ic_sliders", "界面", "主题、菜单、工具栏、全屏、字体"},
                {"ic_tabs", "标签", "新建、关闭、切换、标签条"},
                {"ic_back", "导航", "前后退、刷新、主页、查找"},
                {"ic_search", "搜索", "引擎、网址栏相关"},
                {"ic_toolbox", "工具", "截图、播放器、打印、导出"},
                {"ic_incognito", "隐私", "隐身、密码、清理、权限"},
                {"ic_log", "诊断", "日志、自检、调试"},
                {"ic_book", "资料", "备份、导出、导入"},
                {"ic_settings", "系统", "更新、安装、关于"},
                {"ic_list", "链接菜单", "长按链接时执行的动作"},
        };
        java.util.Map<String, java.util.List<String>> bucket = new java.util.LinkedHashMap<>();
        for (String[] c : cats) bucket.put(c[1], new ArrayList<>());
        for (com.vane.browser.core.Actions.Def d : com.vane.browser.core.Actions.all()) {
            if ("editMenu".equals(d.id) || primary.contains(d.id)) continue;
            String key = bucket.containsKey(d.group) ? d.group
                    : ("媒体".equals(d.group) ? "工具" : "工具");
            bucket.get(key).add(d.id);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        box.addView(Widgets.text(this, "高级", 18, Theme.fg(this, ui)));

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        for (String[] c : cats) {
            java.util.List<String> ids = bucket.get(c[1]);
            if (ids == null || ids.isEmpty()) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
            android.widget.ImageView iv = new android.widget.ImageView(this);
            int res = getResources().getIdentifier(c[0], "drawable", getPackageName());
            iv.setImageResource(res == 0 ? R.drawable.ic_menu : res);
            iv.setColorFilter(Theme.fg(this, ui));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Theme.dp(this, 22), Theme.dp(this, 22));
            ilp.setMargins(0, 0, Theme.dp(this, 14), 0);
            row.addView(iv, ilp);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Widgets.text(this, c[1] + "（" + ids.size() + "）", 15, Theme.fg(this, ui)));
            TextView sub = Widgets.text(this, c[2], 11, Theme.hint(this, ui));
            sub.setPadding(0, Theme.dp(this, 2), 0, 0);
            texts.addView(sub);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Widgets.text(this, "›", 16, Theme.hint(this, ui)));
            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                showToolsGroup(c[1], ids.toArray(new String[0]), false);
            });
            box.addView(row);
            View line = new View(this);
            line.setBackgroundColor(Theme.line(this, ui));
            box.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(this, 1) / 2)));
        }
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    /** 工具箱：只保留 7 个入口，其余全部收进「高级」。 */
    private String[][] buildToolGroups() {
        // 主入口（每个最多 4 项，日常够用）
        String[][] primary = {
                {"ic_proxy", "代理", "开关、订阅、分流", "proxy"},
                {"ic_shield", "广告", "开关、强度、自检",
                        "adblock,adLevel,adCheck,adStats"},
                {"ic_save", "这个网页", "复制、翻译、保存、看正文",
                        "copyUrl,translate,save,reader"},
                {"ic_book", "书签与下载", "书签历史、下载、离线",
                        "library,downloads,offline,addBookmark"},
                {"ic_toolbox", "工具", "扫码、朗读、看图、截图",
                        "scanQr,speak,imageList,longShot"},
                {"ic_settings", "设置", "设置、备份、更新、关于",
                        "settings,backup,restore,checkUpdate"},
        };
        java.util.Set<String> used = new java.util.HashSet<>();
        for (String[] g : primary) java.util.Collections.addAll(used, g[3].split(","));

        // 其余进「高级」
        java.util.List<String> rest = new ArrayList<>();
        for (com.vane.browser.core.Actions.Def d : com.vane.browser.core.Actions.all()) {
            if ("editMenu".equals(d.id) || used.contains(d.id)) continue;
            rest.add(d.id);
        }

        java.util.List<String[]> out = new ArrayList<>();
        for (String[] g : primary) out.add(g);
        if (!rest.isEmpty()) {
            out.add(new String[]{"ic_list", "高级", "按类别查看全部功能", "@ADV@"});
        }
        return out.toArray(new String[0][]);
    }

    private boolean forwardNav = false;

    private void showToolsGroup(final String groupName, final String[] ids, final boolean fromTools) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        TextView title = Widgets.text(this, groupName, 18, Theme.fg(this, ui));
        title.setPadding(0, 0, 0, Theme.dp(this, 10));
        box.addView(title);

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        final boolean[] goingDeeper = {false};
        LinearLayout itemBox = new LinearLayout(this);
        itemBox.setOrientation(LinearLayout.VERTICAL);
        for (final String id : ids) {
            Actions.Def def = Actions.get(id);
            String label = def == null ? id : def.label;
            String desc = TIPS.containsKey(id) ? TIPS.get(id) : "";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Widgets.text(this, label, 15, Theme.fg(this, ui)));
            if (!desc.isEmpty()) {
                TextView d = Widgets.text(this, desc, 12, Theme.hint(this, ui));
                d.setPadding(0, Theme.dp(this, 2), 0, 0);
                texts.addView(d);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if ("fontSize".equals(id)) {
                row.addView(fontStepper());
            } else if (isTogglable(id)) {
                row.addView(Widgets.pill(this, ui, isActionActive(id), "已开启", "已关闭"));
                row.setOnClickListener(v -> {
                    if (holder[0] != null) {
                        goingDeeper[0] = true;
                        holder[0].dismiss();
                    }
                    dispatch(id);
                    showToolsGroup(groupName, ids, false);
                });
            } else {
                row.addView(Widgets.text(this, "›", 16, Theme.hint(this, ui)));
                row.setOnClickListener(v -> dispatch(id));   // 面板保留在下面，关掉动作就回到分组
            }
            itemBox.addView(row);
            View line = new View(this);
            line.setBackgroundColor(Theme.line(this, ui));
            itemBox.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(this, 1) / 2 + 1)));
        }
        ScrollView isv = new ScrollView(this);
        isv.setVerticalScrollBarEnabled(false);
        isv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        isv.addView(itemBox);
        int ih = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
        box.addView(isv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ih));

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
        if (fromTools) {
            holder[0].setOnDismissListener(dia -> {
                if (!goingDeeper[0]) {
                    // 返回键 / 点空白关掉：回到工具箱主列表
                    root.post(this::showTools);
                }
            });
        }
    }

    /** 每个功能一句人话。 */
    private static final java.util.Map<String, String> TIPS = new java.util.HashMap<>();
    static {
        TIPS.put("copyUrl", "把这个页面的地址拷走");
        TIPS.put("find", "输入关键词，页内高亮");
        TIPS.put("translate", "整页交给在线翻译");
        TIPS.put("save", "存成文件留底");
        TIPS.put("longShot", "整页拼成一张长图存相册");
        TIPS.put("offlineSave", "断网也能再看一遍");
        TIPS.put("reader", "去掉广告和侧栏");
        TIPS.put("source", "开发者才用");
        TIPS.put("video", "抓这一页的 m3u8 / mp4");
        TIPS.put("openPlayer", "自动带 Referer，防盗链也能播");
        TIPS.put("scanQr", "拍一张照片来识别");
        TIPS.put("speak", "用系统语音念出来");
        TIPS.put("ai", "要你自己填接口和密钥");
        TIPS.put("fontSize", "点一下放大一档");
        TIPS.put("rotate", "强制转一下方向");
        TIPS.put("images", "点了就不加载图片，再点恢复");
        TIPS.put("addToDesktop", "以后从桌面一键打开");
        TIPS.put("report", "复制问题信息，发给我");
        TIPS.put("proxy", "连上才能上外网");
        TIPS.put("proxyMode", "国内直连 / 境外走代理");
        TIPS.put("markAd", "写进本地拦截规则");
        TIPS.put("netlog", "最近的请求记录");
        TIPS.put("settings", "所有开关都在这里");
        TIPS.put("backup", "书签历史设置打成一个文件");
        TIPS.put("restore", "从备份文件读回来");
        TIPS.put("checkUpdate", "联网看有没有新版本");
        TIPS.put("compat", "换手机前先看这几项");
        TIPS.put("installApk", "从下载目录选一个包装");
        TIPS.put("about", "版本、内核、规则数量");
    }

    public void showTabs() {
        if (isFinishing() || isDestroyed()) return;

        final android.app.Dialog[] holder = new android.app.Dialog[1];

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 10), 0, Theme.dp(this, 10), Theme.dp(this, 14));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        // 头部：标题 + 新建 + 全部关闭
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Theme.dp(this, 8), 0, Theme.dp(this, 4), Theme.dp(this, 6));

        TextView title = Widgets.text(this, "标签页 · " + tabs.size(), 13, Theme.hint(this, ui));
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView undo = Widgets.text(this, "撤销关闭", 14, Theme.fg(this, ui));
        undo.setPadding(Theme.dp(this, 10), Theme.dp(this, 8), Theme.dp(this, 10), Theme.dp(this, 8));
        undo.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            reopenClosed();
        });
        head.addView(undo);

        TextView add = Widgets.text(this, "＋ 新建", 14, Theme.accent(ui));
        add.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), Theme.dp(this, 12), Theme.dp(this, 8));
        add.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            newTab(ui.homePage, true);
            focusAddress();
        });
        head.addView(add);

        if (tabs.size() > 1) {
            TextView closeAll = Widgets.text(this, "全部关闭", 14, Theme.hint(this, ui));
            closeAll.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));
            closeAll.setOnClickListener(v -> {
                Widgets.confirm(this, ui, "关闭全部标签", "将关闭 " + tabs.size() + " 个标签页", () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    // 直接清空，避免 closeTab 在空列表时又自动开一个
                    for (int i = tabs.size() - 1; i >= 0; i--) {
                        BrowserTab dead = tabs.remove(i);
                        try {
                            tabHost.removeView(dead.web);
                            dead.destroy();
                        } catch (Throwable ignored) {}
                    }
                    current = -1;
                    newTab(ui.homePage, true);
                });
            });
            head.addView(closeAll);
        }
        box.addView(head);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            BrowserTab t = tabs.get(i);
            boolean on = i == current;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.dp(this, 10), Theme.dp(this, 11), Theme.dp(this, 4), Theme.dp(this, 11));
            if (on) row.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 12)));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            String name = t.title == null || t.title.isEmpty() ? "新标签页" : t.title;
            TextView tt = Widgets.text(this, trim(name, 30), 14, on ? Theme.accent(ui) : Theme.fg(this, ui));
            texts.addView(tt);
            TextView uu = Widgets.text(this, trim(t.isHome() ? "主页" : t.url, 46), 11, Theme.hint(this, ui));
            uu.setPadding(0, Theme.dp(this, 2), 0, 0);
            texts.addView(uu);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView close = Widgets.text(this, "✕", 14, Theme.hint(this, ui));
            close.setGravity(Gravity.CENTER);
            close.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), Theme.dp(this, 12), Theme.dp(this, 8));
            close.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                closeTab(idx);
            });
            row.addView(close);

            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                selectTab(idx);
            });
            list.addView(row);
        }

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(list);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    // ================= 工具类动作 =================

    /** 分享当前页：主页上分享的是应用本身，不进网页时不发空链接。 */
    public void shareCurrent() {
        BrowserTab t = currentTab();
        if (t == null) return;
        String text;
        if (t.isHome() || t.url == null || t.url.isEmpty()
                || t.url.startsWith("data:") || t.url.startsWith("about:")
                || t.url.startsWith("file:")) {
            text = "Vane · 无广告的极简浏览器";
        } else {
            text = (t.title == null || t.title.isEmpty() ? t.url : t.title) + "\n" + t.url;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "分享"));
    }

    public void copyUrl() {
        BrowserTab t = currentTab();
        if (t == null) return;
        if (t.isHome() || t.url == null || t.url.isEmpty()) {
            Widgets.toast(this, "主页没有网址可复制");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", t.url));
        Widgets.toast(this, "已复制：" + t.url);
    }

    public void findInPage() {
        Widgets.input(this, ui, "页内查找", "", false, text -> {
            BrowserTab t = currentTab();
            if (t != null && !text.isEmpty()) {
                t.web.findAllAsync(text);
                Widgets.toast(this, "查找：" + text);
            }
        });
    }

    public void toggleDesktop() {
        desktopMode = !desktopMode;
        Prefs.putBool("desktop", desktopMode);
        for (BrowserTab t : tabs) t.applyAll();
        BrowserTab t = currentTab();
        if (t != null) t.web.reload();
    }

    /** 夜间模式 = 直接把主题切到深色/浅色，不再叠滤镜。 */
    /** 主题切换后把内置页重画一遍（否则书签页还是旧配色）。 */
    private void reloadInternalAfterTheme() {
        BrowserTab t = currentTab();
        if (t == null || t.internalLabel == null) return;
        int idx = "书签".equals(t.internalLabel) ? 0
                : ("离线页面".equals(t.internalLabel) ? 2 : 1);
        showLibraryPage(idx);
    }

    public void toggleNight() {
        ui = UiConfig.load();
        boolean dark = Theme.isDark(this, ui);
        ui.theme = dark ? "light" : "dark";
        ui.save();
        nightMode = !dark;
        Theme.applyToWindow(this, ui);
        root.setBackgroundColor(Theme.bg(this, ui));
        rebuildChrome();                       // 顶栏底栏跟着换色
        for (BrowserTab tb : tabs) {
            tb.applyDarkening();
            tb.applyPageTheme();               // 当前页立刻变深/变浅
        }
        reloadInternalAfterTheme();            // 内置页（书签/历史/离线）跟着重画
    }

    public void readerMode() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;

        if (Prefs.getBool("reader_confirm", false)) {
            Widgets.choice(this, ui, "开启阅读模式？", "会去掉广告和侧栏，只留正文", "开启", () -> {
                t.web.evaluateJavascript(VideoSniffer.readerJs(Prefs.get("reader_css", "")), null);
            }, "取消", null);
            return;
        }

        // 先用精简版 Readability 提取正文
        final String css = Prefs.get("reader_css", "");
        final BrowserTab tb = t;
        try {
            String js = readAsset("readability.js")
                    + "(function(){try{var h=window.__vaneRead&&window.__vaneRead();return h||'';}catch(e){return '';}})();";
            t.web.evaluateJavascript(js, value -> {
                String html = value;
                try {
                    html = new org.json.JSONArray("[" + value + "]").optString(0);
                } catch (Exception ignored) {}
                if (html != null && html.replaceAll("<[^>]+>", "").trim().length() > 180) {
                    String page = readerPage(html, css);
                    java.io.File f = new java.io.File(getCacheDir(), "reader.html");
                    try {
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                        fos.write(page.getBytes("UTF-8"));
                        fos.close();
                        tb.web.getSettings().setAllowFileAccess(true);
                        tb.internalLabel = "阅读模式";
                        tb.web.loadUrl("file://" + f.getAbsolutePath());
                    } catch (Exception ignored) {}
                } else {
                    // 提取失败：退回旧的整体压暗模式
                    tb.web.evaluateJavascript(VideoSniffer.readerJs(css), null);
                }
            });
        } catch (Throwable th) {
            t.web.evaluateJavascript(VideoSniffer.readerJs(css), null);
        }
    }

    /** 阅读页外壳：标题、正文、返回按钮。 */
    private String readerPage(String inner, String extraCss) {
        boolean dark = Theme.isDark(this, ui);
        String bg = dark ? "#121417" : "#FFFFFF";
        String fg = dark ? "#E8EAED" : "#141414";
        String sub = dark ? "#9AA0A6" : "#6B7280";
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;background:" + bg + ";color:" + fg + ";"
                + "font:18px/1.85 system-ui,-apple-system,sans-serif}"
                + "#__vane_reader{max-width:720px;margin:0 auto;padding:20px 18px 80px}"
                + "#__vane_reader h1{font-size:23px;line-height:1.4;margin:0 0 16px}"
                + "#__vane_reader img{max-width:100%;height:auto;border-radius:8px;margin:12px 0}"
                + "#__vane_reader p{margin:0 0 14px}"
                + "#__vane_reader a{color:inherit;text-decoration:underline}"
                + "#__vane_reader blockquote{border-left:3px solid " + sub + ";margin:14px 0;padding:4px 0 4px 12px;color:" + sub + "}"
                + "#__vane_reader pre{overflow:auto;background:" + (dark ? "#1D2024" : "#F3F4F6") + ";padding:10px;border-radius:8px}"
                + extraCss
                + "</style></head><body>" + inner + "</body></html>";
    }

    private String readAsset(String name) {
        try {
            java.io.InputStream in = getAssets().open(name);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }


    public void zoom(int dir) {
        BrowserTab t = currentTab();
        if (t == null) return;
        float z = t.web.getScale();
        z = Math.max(0.5f, Math.min(3f, z + dir * 0.15f));
        t.web.setInitialScale(Math.round(z * 100));
    }

    public void showUaMenu() {
        List<String> items = new ArrayList<>();
        items.add("默认（Android Chrome）");
        items.add("桌面 Windows Chrome");
        items.add("iPhone Safari");
        items.add("自定义 UA…");
        Widgets.pick(this, ui, "User-Agent", items, idx -> {
            switch (idx) {
                case 0: Prefs.put("ua_mode", "default"); break;
                case 1: Prefs.put("ua_mode", "desktop"); break;
                case 2: Prefs.put("ua_mode", "iphone"); break;
                default:
                    Widgets.input(this, ui, "自定义 UA", Prefs.get("ua_custom", ""), true, s -> {
                        Prefs.put("ua_custom", s);
                        Prefs.put("ua_mode", "custom");
                        for (BrowserTab t : tabs) t.applyAll();
                    });
                    return;
            }
            for (BrowserTab t : tabs) t.applyAll();
            BrowserTab t = currentTab();
            if (t != null) t.web.reload();
        });
    }

    public String userAgent() {
        String mobile = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
        String desktop = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
        String mode = Prefs.get("ua_mode", "default");
        switch (mode) {
            case "desktop": return desktop;
            case "iphone": return iphone;
            case "custom": return Prefs.get("ua_custom", mobile);
            default: return desktopMode ? desktop : mobile;
        }
    }

    public void screenshot() {
        BrowserTab t = currentTab();
        if (t == null) return;
        try {
            Bitmap bmp = Bitmap.createBitmap(t.web.getWidth(), t.web.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            t.web.draw(c);
            String name = "vane_" + System.currentTimeMillis() + ".png";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vane");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    try (FileOutputStream out = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    Widgets.toast(this, "已保存到相册/Pictures/Vane");
                    return;
                }
            }
            File f = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Widgets.toast(this, "已保存：" + f.getAbsolutePath());
        } catch (Exception e) {
            Widgets.toast(this, "截图失败：" + e.getMessage());
        }
    }

    public void clearBrowsingData() {
        Widgets.confirm(this, ui, "清除浏览数据", "将清空 Cookie、缓存、历史记录与书签。", () -> {
            CookieManager.getInstance().removeAllCookies(null);
            for (BrowserTab t : tabs) {
                t.web.clearCache(true);
                t.web.clearHistory();
                t.web.clearFormData();
            }
            Prefs.putArray("history", new JSONArray());
            Prefs.putArray("bookmarks", new JSONArray());
            Widgets.toast(this, "已清除");
        });
    }

    // ================= 书签 / 历史 =================

    private void addHistory(String url, String title) {
        // 时间戳用于「今天/昨天」分组

        if (url == null || url.isEmpty()) return;
        if (url.startsWith("about:") || url.startsWith("data:") || url.startsWith("vane:")
                || url.startsWith("file:")) return;
        if (url == null || url.isEmpty() || url.startsWith("file://") || url.startsWith("about:")) return;
        if (Prefs.getBool("no_history", false)) return;
        if (privateUrl != null && url.startsWith(privateUrl)) return;   // 隐私空间的站不留痕
        // 内置页（书签/历史/下载/离线）不进历史：标题是内部名字或以 .html 结尾
        if (title != null) {
            String t = title.trim();
            if ("书签".equals(t) || "历史记录".equals(t) || "离线页面".equals(t)
                    || "下载".equals(t) || t.endsWith(".html")) return;
        }
        try {
            JSONArray arr = Prefs.getArray("history");
            JSONArray out = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("title", title == null ? url : title);
            o.put("t", System.currentTimeMillis());
            out.put(o);
            for (int i = 0; i < arr.length() && out.length() < 500; i++) {
                JSONObject x = arr.optJSONObject(i);
                if (x != null && !url.equals(x.optString("url"))) out.put(x);
            }
            Prefs.putArray("history", out);
        } catch (Exception ignored) {}
    }

    public void addBookmark() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            Widgets.toast(this, "当前页不能收藏");
            return;
        }
        try {
            JSONArray arr = Prefs.getArray("bookmarks");
            JSONObject o = new JSONObject();
            o.put("url", t.url);
            o.put("title", t.title);
            arr.put(o);
            Prefs.putArray("bookmarks", arr);
            Widgets.toast(this, "已收藏：" + trim(t.title, 20));
        } catch (Exception ignored) {}
    }

    public void showBookmarks() {
        showLibraryPage(0);
    }

    public void showHistory() {
        showLibraryPage(1);
    }

    public void showBookmarksMerged() {
        showLibrary(0);
    }

    /** 书签和历史放一个面板里：顶部搜索 + 两个分段按钮。 */
    private void showLibrary(final int startTab) {
        final JSONArray bookmarks = Prefs.getArray("bookmarks");
        final JSONArray history = Prefs.getArray("history");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        TextView title = Widgets.text(this, "书签与历史", 18, Theme.fg(this, ui));
        title.setPadding(0, 0, 0, Theme.dp(this, 8));
        box.addView(title);

        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索书签或历史");
        search.setTextSize(14);
        search.setTextColor(Theme.fg(this, ui));
        search.setHintTextColor(Theme.hint(this, ui));
        search.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 12)));
        int sp = Theme.dp(this, 12);
        search.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = Theme.dp(this, 10);
        box.addView(search, slp);

        final int[] tab = {startTab};
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final android.app.Dialog[] holder = new android.app.Dialog[1];

        final Runnable render = new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                JSONArray src = tab[0] == 0 ? bookmarks : history;
                String needle = search.getText().toString().trim().toLowerCase();
                int shown = 0;
                for (int i = 0; i < src.length() && shown < 200; i++) {
                    final JSONObject o = src.optJSONObject(i);
                    if (o == null) continue;
                    final String url = o.optString("url", "");
                    String t = o.optString("title", url);
                    if (url.isEmpty()) continue;
                    if (!needle.isEmpty() && !(t + " " + url).toLowerCase().contains(needle)) continue;
                    shown++;

                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, Theme.dp(MainActivity.this, 10), 0, Theme.dp(MainActivity.this, 10));

                    String host = url;
                    try {
                        host = android.net.Uri.parse(url).getHost();
                    } catch (Exception ignored) {}
                    if (host == null || host.isEmpty()) host = url;

                    android.widget.FrameLayout icon = new android.widget.FrameLayout(MainActivity.this);
                    icon.setBackground(Theme.rounded(Theme.field(MainActivity.this, ui), Theme.dp(MainActivity.this, 18)));
                    android.widget.ImageView iv = new android.widget.ImageView(MainActivity.this);
                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    int ip = Theme.dp(MainActivity.this, 7);
                    iv.setPadding(ip, ip, ip, ip);
                    com.vane.browser.ui.ImageLoader.load(iv,
                            com.vane.browser.ui.ImageLoader.favicon(host),
                            tab[0] == 0 ? R.drawable.ic_star : R.drawable.ic_history);
                    icon.addView(iv, new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                            Theme.dp(MainActivity.this, 36), Theme.dp(MainActivity.this, 36));
                    ilp.setMargins(0, 0, Theme.dp(MainActivity.this, 12), 0);
                    row.addView(icon, ilp);

                    LinearLayout texts = new LinearLayout(MainActivity.this);
                    texts.setOrientation(LinearLayout.VERTICAL);
                    texts.addView(Widgets.text(MainActivity.this, trim(t, 40), 14, Theme.fg(MainActivity.this, ui)));
                    TextView u = Widgets.text(MainActivity.this, host, 11, Theme.hint(MainActivity.this, ui));
                    u.setPadding(0, Theme.dp(MainActivity.this, 2), 0, 0);
                    texts.addView(u);
                    row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView del = Widgets.text(MainActivity.this, "✕", 14, Theme.hint(MainActivity.this, ui));
                    del.setPadding(Theme.dp(MainActivity.this, 10), Theme.dp(MainActivity.this, 6),
                            Theme.dp(MainActivity.this, 6), Theme.dp(MainActivity.this, 6));
                    del.setOnClickListener(v -> {
                        JSONArray fresh = new JSONArray();
                        for (int k = 0; k < src.length(); k++) {
                            JSONObject cur = src.optJSONObject(k);
                            if (cur != null && !url.equals(cur.optString("url", ""))) fresh.put(cur);
                        }
                        Prefs.putArray(tab[0] == 0 ? "bookmarks" : "history", fresh);
                        run();
                    });
                    row.addView(del);

                    row.setOnClickListener(v -> {
                        if (holder[0] != null) holder[0].dismiss();
                        newTab(url, true);
                    });
                    list.addView(row);
                    View line = new View(MainActivity.this);
                    line.setBackgroundColor(Theme.line(MainActivity.this, ui));
                    list.addView(line, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(MainActivity.this, 1) / 2)));
                }
                if (shown == 0) {
                    TextView empty = Widgets.text(MainActivity.this,
                            tab[0] == 0 ? "还没有书签" : "还没有历史记录", 13, Theme.hint(MainActivity.this, ui));
                    empty.setPadding(0, Theme.dp(MainActivity.this, 16), 0, 0);
                    list.addView(empty);
                }
            }
        };

        // 分段：书签 / 历史
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(0, 0, 0, Theme.dp(this, 10));
        final TextView[] tabs = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            String label = i == 0 ? "书签 " + bookmarks.length() : "历史 " + history.length();
            TextView b = Widgets.text(this, label, 13,
                    idx == tab[0] ? Theme.bg(this, ui) : Theme.hint(this, ui));
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Theme.dp(this, 8), 0, Theme.dp(this, 8));
            b.setBackground(Theme.rounded(idx == tab[0] ? Theme.fg(this, ui) : Theme.field(this, ui),
                    Theme.dp(this, 12)));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i == 1) blp.setMargins(Theme.dp(this, 8), 0, 0, 0);
            b.setOnClickListener(v -> {
                tab[0] = idx;
                for (int k = 0; k < 2; k++) {
                    tabs[k].setTextColor(k == tab[0] ? Theme.bg(MainActivity.this, ui) : Theme.hint(MainActivity.this, ui));
                    tabs[k].setBackground(Theme.rounded(k == tab[0] ? Theme.fg(MainActivity.this, ui)
                            : Theme.field(MainActivity.this, ui), Theme.dp(MainActivity.this, 12)));
                }
                render.run();
            });
            tabs[i] = b;
            seg.addView(b, blp);
        }
        box.addView(seg);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s1, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s1, int a, int b, int c) { render.run(); }
            @Override public void afterTextChanged(android.text.Editable s1) {}
        });
        render.run();
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }


    /** 下载页点条目：按文件名去 MediaStore 找，apk 交给安装器，其他交给对应应用。 */
    private void openDownloadedFile(String name) {
        if (name == null || name.isEmpty()) {
            Widgets.toast(this, "找不到文件名");
            return;
        }
        android.net.Uri found = null;
        try {
            android.net.Uri base = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            try (android.database.Cursor c = getContentResolver().query(base,
                    new String[]{android.provider.MediaStore.MediaColumns._ID},
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                    new String[]{name}, null)) {
                if (c != null && c.moveToFirst()) {
                    found = android.content.ContentUris.withAppendedId(base, c.getLong(0));
                }
            }
        } catch (Exception ignored) {}
        if (found == null) {
            Widgets.toast(this, "文件还没进媒体库，稍后再试或去下载目录");
            return;
        }
        String lower = name.toLowerCase();
        String mime = "application/octet-stream";
        if (lower.endsWith(".apk")) mime = "application/vnd.android.package-archive";
        else if (lower.endsWith(".mp4")) mime = "video/mp4";
        else if (lower.endsWith(".mp3")) mime = "audio/mpeg";
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mime = "image/jpeg";
        else if (lower.endsWith(".png")) mime = "image/png";
        else if (lower.endsWith(".pdf")) mime = "application/pdf";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(found, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Widgets.toast(this, "没有能打开它的应用");
        }
    }

    /** 下载页：跟书签/历史一样是内置 HTML 页，每秒刷新进度。 */
    public void showDownloadsPage() {
        BrowserTab t = currentTab();
        if (t == null) return;
        if (progress != null) progress.setVisibility(View.GONE);
        boolean dark = Theme.isDark(this, ui);
        String bg = dark ? "#101114" : "#FFFFFF";
        String fg = dark ? "#EDEEF0" : "#111111";
        String sub = dark ? "#9AA0A6" : "#6B7280";
        String field = dark ? "#23252A" : "#F1F3F4";
        String html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}"
                + "a{color:inherit;text-decoration:none}"
                + "body{background:" + bg + ";color:" + fg + ";font:15px/1.45 system-ui,sans-serif;"
                + "max-width:600px;margin:auto;padding-bottom:70px}"
                + "h1{font-size:18px;padding:16px 14px 8px}"
                + ".box{display:flex;align-items:center;gap:12px;padding:12px 14px;position:relative}"
                + ".ic{flex:0 0 22px;width:22px;height:22px;color:" + sub + "}"
                + ".t{flex:1;min-width:0}"
                + ".n{font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
                + ".s{font-size:11px;color:" + sub + ";margin-top:2px}"
                + ".bar{height:3px;background:" + field + ";border-radius:2px;margin-top:6px;overflow:hidden}"
                + ".bar i{display:block;height:3px;background:" + fg + "}"
                + ".hint{color:" + sub + ";padding:60px 14px;text-align:center;font-size:14px}"
                + ".del{color:" + sub + ";padding:6px 2px 6px 10px;font-size:13px}"
                + ".foot{position:fixed;left:0;right:0;bottom:0;display:flex;background:" + bg + "}"
                + ".foot a{flex:1;text-align:center;padding:13px 0;color:" + sub + ";text-decoration:none;font-size:13px}"
                + "</style></head><body><h1>下载</h1><div id=list></div>"
                + "<div class=foot><a href='vane://dl?op=refresh'>刷新</a>"
                + "<a href='vane://dl?op=clear'>清空</a>"
                + "<a href='vane://dl?op=dir'>下载目录</a></div>"
                + "<script>"
                + "function render(a){var l=document.getElementById('list');"
                + "if(!a||!a.length){l.innerHTML='<p class=hint>还没有内容</p>';return;}"
                + "var h='';for(var i=0;i<a.length;i++){var d=a[i];"
                + "h+='<div class=box><span class=ic>⤓</span>'"
                + "+'<a class=t href=\\'vane://dl?op=open&n='+encodeURIComponent(d.title)+'\\'>'"
                + "+'<div class=n>'+d.title.replace(/</g,'&lt;')+'</div>'"
                + "+'<div class=s>'+d.state+(d.pct>=0?' · '+d.pct+'%':'')+'</div>'"
                + "+((d.pct>=0&&d.state==='下载中')?'<div class=bar><i style=\\'width:'+d.pct+'%\\'></i></div>':'')"
                + "+'</a><a class=del href=\\'vane://dl?op=del&id='+d.id+'\\'>✕</a></div>';}"
                + "l.innerHTML=h;}"
                + "function tick(){try{render(JSON.parse(VaneBridge.dlList()));}catch(e){}}"
                + "tick();setInterval(tick,1000);"
                + "</script></body></html>";
        try {
            java.io.File f = new java.io.File(getCacheDir(), "downloads.html");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(html.getBytes("UTF-8"));
            fos.close();
            t.internalLabel = "下载";
            t.web.getSettings().setAllowFileAccess(true);
            t.web.loadUrl("file://" + f.getAbsolutePath());
        } catch (Exception e) {
            Widgets.toast(this, "打开下载页失败：" + e.getMessage());
        }
    }

    public void showDownloads() {
        if (isFinishing() || isDestroyed()) return;
        final android.app.DownloadManager dm =
                (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        final java.util.List<Long> ids = new ArrayList<>();
        final java.util.List<String> titles = new ArrayList<>();
        final java.util.List<String> states = new ArrayList<>();

        if (dm != null) {
            android.database.Cursor c = dm.query(new android.app.DownloadManager.Query());
            if (c != null) {
                int iId = c.getColumnIndex(android.app.DownloadManager.COLUMN_ID);
                int iTitle = c.getColumnIndex(android.app.DownloadManager.COLUMN_TITLE);
                int iStatus = c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int iDone = c.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int iTotal = c.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                while (c.moveToNext() && ids.size() < 60) {
                    long id = c.getLong(iId);
                    String title = c.getString(iTitle);
                    int st = c.getInt(iStatus);
                    long done = c.getLong(iDone), total = c.getInt(iTotal);
                    String state;
                    if (st == android.app.DownloadManager.STATUS_SUCCESSFUL) state = "已完成";
                    else if (st == android.app.DownloadManager.STATUS_FAILED) state = "失败";
                    else if (st == android.app.DownloadManager.STATUS_PAUSED) state = "已暂停";
                    else state = total > 0 ? ("下载中 " + (done * 100 / Math.max(1, total)) + "%") : "下载中";
                    ids.add(id);
                    titles.add(title == null ? "未命名" : title);
                    states.add(state);
                }
                c.close();
            }
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        TextView head = Widgets.text(this, "下载内容", 18, Theme.fg(this, ui));
        head.setPadding(0, 0, 0, Theme.dp(this, 10));
        box.addView(head);

        final android.app.Dialog[] holder = new android.app.Dialog[1];

        if (ids.isEmpty()) {
            TextView empty = Widgets.text(this, "还没有内容", 14, Theme.hint(this, ui));
            empty.setPadding(0, Theme.dp(this, 20), 0, Theme.dp(this, 20));
            box.addView(empty);
        } else {
            for (int i = 0; i < ids.size(); i++) {
                final long id = ids.get(i);
                final String name = titles.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Theme.dp(this, 12), 0, Theme.dp(this, 12));
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageResource(R.drawable.ic_down);
                iv.setColorFilter(Theme.fg(this, ui));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Theme.dp(this, 20), Theme.dp(this, 20));
                ilp.setMargins(0, 0, Theme.dp(this, 12), 0);
                row.addView(iv, ilp);
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.addView(Widgets.text(this, trim(name, 34), 14, Theme.fg(this, ui)));
                TextView st = Widgets.text(this, states.get(i), 11, Theme.hint(this, ui));
                st.setPadding(0, Theme.dp(this, 2), 0, 0);
                texts.addView(st);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Widgets.text(this, "›", 16, Theme.hint(this, ui)));
                row.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    Widgets.pick(this, ui, name, java.util.Arrays.asList("打开", "删除"), k -> {
                        if (k == 0) {
                            try {
                                android.net.Uri u = dm.getUriForDownloadedFile(id);
                                if (u == null) {
                                    Widgets.toast(this, "文件还没下载完");
                                    return;
                                }
                                startActivity(new Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(u, dm.getMimeTypeForDownloadedFile(id))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                            } catch (Exception e) {
                                Widgets.toast(this, "打不开：" + e.getMessage());
                            }
                        } else {
                            try { dm.remove(id); } catch (Exception ignored) {}
                        }
                    });
                });
                box.addView(row);
                View line = new View(this);
                line.setBackgroundColor(Theme.line(this, ui));
                box.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(this, 1) / 2)));
            }
            TextView open = Widgets.ghostButton(this, ui, "打开下载目录", () -> {
                if (holder[0] != null) holder[0].dismiss();
                try {
                    startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
                } catch (Exception ignored) {}
            });
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.topMargin = Theme.dp(this, 14);
            box.addView(open, olp);
        }

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    // ================= 视频 =================

    public void showVideoList() {
        if (isFinishing() || isDestroyed()) return;
        List<String> urls = JsBridge.mediaUrls();
        if (urls.isEmpty()) {
            scanMediaNow();      // 没嗅到就现场扫一遍 <video>/<source>/媒体链接
            return;
        }
        List<String> items = new ArrayList<>(urls);
        Widgets.menu(this, ui, "嗅探到的媒体（" + items.size() + "）", items,
                idx -> mediaItemChooser(items.get(idx)));
    }

    /** 嗅探结果里点一条：播放还是下载（下载再选本地/隐私空间）。 */
    private void mediaItemChooser(final String u) {
        Widgets.pick(this, ui, "这条媒体", java.util.Arrays.asList(
                "用内置播放器播放", "下载（可选存本地或隐私空间）",
                "复制地址", "交给外部播放器"), i -> {
            if (i == 0) openPlayer(u, "视频 1");
            else if (i == 1) askDownloadTarget(u);
            else if (i == 2) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("url", u));
                Widgets.toast(this, "已复制");
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Widgets.toast(this, "没有能播这个地址的应用");
                }
            }
        });
    }

    /**
     * 现场扫当前页，只挑「主视频」：
     * 先按可见面积/时长/是否在播给每个候选打分，再把广告与埋点地址剔掉，最后只把最高分那条摆在前面。
     * 其它候选收在「其它 N 个候选」里，避免像以前那样把广告、封面预览、埋点全列出来。
     */
    private void scanMediaNow() {
        final BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            Widgets.toast(this, "先打开一个网页再嗅探");
            return;
        }
        t.web.evaluateJavascript("(function(){var out=[],seen={};"
                + "function push(u,sc,k){if(!u)return;if(u.slice(0,5)==='blob:'||u.slice(0,5)==='data:')return;"
                + "if(seen[u])return;seen[u]=1;out.push({u:u,s:sc,k:k});}"
                + "document.querySelectorAll('video,audio').forEach(function(v){"
                + "var r=v.getBoundingClientRect();var area=(r.width*r.height)||0;"
                + "var vis=v.offsetParent!==null&&r.width>80&&r.height>60;"
                + "var dur=isFinite(v.duration)?v.duration:0;var sc=0;"
                + "if(vis)sc+=40;if(area>200000)sc+=40;else if(area>60000)sc+=25;else if(area<20000)sc-=25;"
                + "if(dur>60)sc+=30;else if(dur>5)sc+=15;else if(dur>0&&dur<3)sc-=20;"
                + "if(!v.paused)sc+=20;"
                + "if(v.currentSrc)push(v.currentSrc,sc+10,'video');"
                + "v.querySelectorAll('source').forEach(function(o){if(o.src)push(o.src,sc,'source');});});"
                + "document.querySelectorAll('a[href]').forEach(function(a){"
                + "var h=a.href||'';if(!/\\.(mp4|m3u8|webm|flv|mkv)(\\?|$)/i.test(h))return;push(h,5,'link');});"
                + "out.sort(function(x,y){return y.s-x.s;});return JSON.stringify(out.slice(0,20));})()", v -> {
            final List<String> ranked = new ArrayList<>();
            try {
                String raw = v == null ? "" : v;
                if (raw.startsWith("\"")) {
                    raw = raw.substring(1, raw.length() - 1)
                            .replace("\\\"", "\"").replace("\\\\", "\\");
                }
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String u = o.optString("u", "");
                    int sc = o.optInt("s", 0);
                    if (u.isEmpty() || sc < 10 || looksLikeAd(u)) continue;   // 广告/埋点/小封面直接丢
                    ranked.add(u);
                }
            } catch (Exception ignored) {}
            if (ranked.isEmpty()) {
                Widgets.toast(this, "这页没找到可播的主视频");
                return;
            }
            if (ranked.size() == 1) {
                mediaItemChooser(ranked.get(0));
                return;
            }
            final List<String> shown = new ArrayList<>();
            shown.add("▶ " + ranked.get(0));
            shown.add("其它 " + (ranked.size() - 1) + " 个候选");
            Widgets.menu(this, ui, "这页的主视频", shown, idx -> {
                if (idx == 0) mediaItemChooser(ranked.get(0));
                else Widgets.menu(this, ui, "全部候选", ranked,
                        k -> mediaItemChooser(ranked.get(k)));
            });
        });
    }

    /** 广告 / 埋点 / 播放器装饰用的地址，嗅探时直接排除。 */
    private static boolean looksLikeAd(String u) {
        String s = u.toLowerCase();
        for (String bad : new String[]{"/ad/", "/ads/", "adsystem", "doubleclick", "googlesyndication",
                "googletag", "preroll", "midroll", "vast", "/track", "tracking", "analytics",
                "beacon", "pixel", "/log?", "impression", "1x1"}) {
            if (s.contains(bad)) return true;
        }
        return false;
    }

    public void openPlayerForCurrent() {
        List<String> urls = JsBridge.mediaUrls();
        if (urls.isEmpty()) {
            Widgets.toast(this, "没有可播放的地址");
            return;
        }
        openPlayer(urls.get(0), "内置播放器");
    }

    public void openPlayer(String url, String title) {
        BrowserTab t = currentTab();
        String referer = t == null ? "" : t.url;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", url);
        i.putExtra("title", title);
        i.putExtra("referer", referer);
        startActivity(i);
    }

    public void playerHelp() {
        Widgets.info(this, ui, "播放器说明",
                "支持 m3u8(HLS)、mp4、webm 等直链；带防盗链的地址会自动带上当前页的 Referer 和 UA，"
                        + "所以大多数站的视频可以直接播。\n\nDRM 加密流（Widevine 那一类）不在可播范围，"
                        + "这类站点仍由网页自带的播放器处理。");
    }

    // ================= 代理 =================

    public void toggleProxy() {
        final ProxyManager pm = ProxyManager.get(this);
        final boolean turningOn = !pm.enabled;
        Widgets.toast(this, turningOn ? "正在启动代理…" : "正在关闭代理…");
        new Thread(() -> {
            if (turningOn) pm.start();
            else pm.stop();
            final String msg = turningOn
                    ? (pm.enabled ? "代理已开启" : (pm.lastError.isEmpty() ? "代理启动失败" : pm.lastError))
                    : "代理已关闭";
            runOnUiThread(() -> Widgets.toast(this, msg));
        }, "proxy-toggle").start();
    }

    /** 冷启动时盖一层：图标 + 一句话，首帧出来后淡出。 */
    private void showSplashOverlay() {
        try {
            final android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
            overlay.setBackgroundColor(Theme.isDark(this, ui) ? 0xFF0E1013 : 0xFFFFFFFF);
            android.widget.ImageView logo = new android.widget.ImageView(this);
            logo.setImageResource(R.drawable.ic_launcher);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    Theme.dp(this, 96), Theme.dp(this, 96));
            lp.gravity = Gravity.CENTER;
            overlay.addView(logo, lp);
            TextView phrase = Widgets.text(this, "天地之大，你到底在追寻什么", 13,
                    Theme.hint(this, ui));
            android.widget.FrameLayout.LayoutParams tp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.gravity = Gravity.CENTER;
            tp.topMargin = Theme.dp(this, 140);
            overlay.addView(phrase, tp);
            root.addView(overlay, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.postDelayed(() -> overlay.animate().alpha(0f).setDuration(320)
                    .withEndAction(() -> {
                        try { root.removeView(overlay); } catch (Throwable ignored) {}
                    }).start(), 420);
        } catch (Throwable ignored) {}
    }

    /** 崩溃/事件日志：一键复制，方便反馈问题。 */
    public void exportCrashLog() {
        try {
            File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Vane");
            File[] all = dir.listFiles((d, n) -> n.startsWith("vane-crash-") || n.startsWith("vane-event-"));
            if (all == null || all.length == 0) {
                Widgets.info(this, ui, "日志", "暂时没有崩溃记录。");
                return;
            }
            java.util.Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (File f : all) {
                if (n++ >= 3) break;
                sb.append("=== ").append(f.getName()).append(" ===\n");
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                    String line;
                    int lines = 0;
                    while ((line = r.readLine()) != null && lines++ < 40) sb.append(line).append('\n');
                }
                sb.append('\n');
            }
            copyText(sb.toString());
            Widgets.info(this, ui, "日志已复制",
                    "最近 " + Math.min(3, all.length) + " 份日志已放进剪贴板，粘贴给开发者即可。");
        } catch (Exception e) {
            Widgets.toast(this, "读取失败：" + e.getMessage());
        }
    }

    /** 引导用户关掉电池优化，否则代理会被系统杀。 */
    public void openBatterySettings() {
        try {
            android.content.Intent i = new android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new android.content.Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                Widgets.info(this, ui, "后台运行",
                        "请到 系统设置 → 电池 → 应用 → Vane，选择「不限制」/「允许后台活动」。");
            }
        }
    }

    /** WebView 版本自检：太老会导致注入/代理失效。 */
    public void checkWebView() {
        String pkg = null, ver = "未知";
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                pkg = android.webkit.WebView.getCurrentWebViewPackage() == null ? null
                        : android.webkit.WebView.getCurrentWebViewPackage().packageName;
            }
            if (pkg != null) {
                ver = getPackageManager().getPackageInfo(pkg, 0).versionName;
            }
        } catch (Exception ignored) {}
        int major = 0;
        try {
            major = Integer.parseInt(ver.split("\\.")[0].trim());
        } catch (Exception ignored) {}
        String tail = major >= 100
                ? "\n\n版本正常（>=100），注入与代理特性都能用。"
                : "\n\n版本偏低（<100），建议更新系统 WebView（应用商店搜 Android System WebView）。";
        String msg = "内核：" + (pkg == null ? "系统默认" : pkg) + "\n版本：" + ver + tail;
        Widgets.info(this, ui, "WebView 自检", msg);
    }

    /** 隐私空间里「存当前页」用：当前标签的地址，首页/内置页给空串。 */
    private String currentPageUrlForVault() {
        try {
            BrowserTab t = currentTab();
            if (t == null || t.isHome()) return "";
            String u = t.url == null ? "" : t.url;
            return u.startsWith("http") ? u : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public void openVault() {
        try {
            startActivity(new Intent(this, VaultActivity.class)
                    .putExtra("page_url", currentPageUrlForVault()));
        } catch (Exception e) {
            Widgets.toast(this, "打不开隐私空间：" + e.getMessage());
        }
    }

    private boolean reopenProxyTools = false;

    public void openProxyPanel() {
        reopenProxyTools = true;      // 从工具箱进来的，回来时要回工具箱
        openSubs();
        if (true) return;
        if (isFinishing() || isDestroyed()) return;
        final ProxyManager pm = ProxyManager.get(this);
        pm.probeKernelAsync();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));

        final android.app.Dialog[] holder = new android.app.Dialog[1];

        // 第一行：状态 + 开关
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 14));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Widgets.text(this, "代理", 18, Theme.fg(this, ui)));
        TextView sub = Widgets.text(this, pm.status(), 12, Theme.hint(this, ui));
        sub.setPadding(0, Theme.dp(this, 3), 0, 0);
        texts.addView(sub);
        head.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final boolean running = pm.isRunning();
        TextView sw = Widgets.pill(this, ui, running, "已开启", "已关闭");
        sw.setPadding(Theme.dp(this, 18), Theme.dp(this, 8), Theme.dp(this, 18), Theme.dp(this, 8));
        sw.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            toggleProxy();
            root.postDelayed(this::openProxyPanel, 900);   // 回来看到新状态
        });
        head.addView(sw);
        box.addView(head);
        box.addView(lineView());

        addPanelRow(box, "订阅与节点", pm.subs.isEmpty() ? "还没添加" : (pm.subs.size() + " 个订阅"),
                "›", () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    openSubs();
                });
        addPanelRow(box, "分流模式", ProxyManager.modeName(pm.rules.mode()), "›", () -> {
            if (holder[0] != null) holder[0].dismiss();
            showProxyModeMenu();
        });
        addPanelRow(box, "测速", pm.enabled() ? "点一下测延迟" : "开了代理才能测", "›", () -> {
            pm.probeLatency(null);
            Widgets.toast(this, "正在测速…");
        });
        addPanelRow(box, "日志", pm.kernelInfo(), "›", () -> {
            if (holder[0] != null) holder[0].dismiss();
            showProxyLog();
        });

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    private View lineView() {
        View v = new View(this);
        v.setBackgroundColor(Theme.line(this, ui));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(this, 1) / 2)));
        return v;
    }

    private void addPanelRow(LinearLayout box, String title, String desc, String arrow, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(this, 14), 0, Theme.dp(this, 14));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Widgets.text(this, title, 15, Theme.fg(this, ui)));
        if (desc != null && !desc.isEmpty()) {
            TextView d = Widgets.text(this, desc, 11, Theme.hint(this, ui));
            d.setPadding(0, Theme.dp(this, 2), 0, 0);
            texts.addView(d);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Widgets.text(this, arrow, 16, Theme.hint(this, ui)));
        row.setOnClickListener(v -> onClick.run());
        box.addView(row);
        box.addView(lineView());
    }

    public void showProxyModeMenu() {
        ProxyManager pm = ProxyManager.get(this);
        String[] names = {"规则分流（国内直连 / 境外代理）", "全局代理", "全局直连"};
        String[] modes = {RuleEngine.MODE_RULE, RuleEngine.MODE_GLOBAL, RuleEngine.MODE_DIRECT};
        Widgets.pick(this, ui, "分流模式", java.util.Arrays.asList(names), idx -> {
            pm.rules.setMode(modes[idx]);
            if (pm.enabled) pm.start();
        });
    }

    public void showProxyLog() {
        ProxyManager pm = ProxyManager.get(this);
        StringBuilder sb = new StringBuilder();
        synchronized (pm.logs) {
            for (String s : pm.logs) sb.append(s).append('\n');
        }
        if (sb.length() == 0) sb.append("还没有日志。开启代理后这里会显示每条请求走了直连还是代理。");
        Widgets.info(this, ui, "代理日志", sb.toString());
    }

    public void toggleAdblock() {
        boolean b = !blocker.isEnabled();
        blocker.setEnabled(b);
        BrowserTab t = currentTab();
        if (t != null) t.web.reload();
    }

    public void openSubs() {
        startActivity(new Intent(this, SubsActivity.class));
    }

    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void openUiEditor() {
        startActivity(new Intent(this, UiEditorActivity.class));
    }

    public void toggleTopBar() {
        ui.showTopBar = !ui.showTopBar;
        ui.save();
        rebuildChrome();
    }

    public void toggleBottomBar() {
        ui.showBottomBar = !ui.showBottomBar;
        ui.save();
        rebuildChrome();
    }

    /** 全屏阅读：照 Via 的做法连系统栏一起隐藏，顶部下滑恢复。 */
    public void toggleFullscreenBars() {
        ui.showTopBar = !ui.showTopBar;
        ui.showBottomBar = ui.showTopBar;
        ui.save();
        buildChrome();
        applyImmersive(!ui.showTopBar);
        if (ui.showTopBar) {
            hideFsChip();
            removeTopSwipe();
        } else {
            showFsChip();
            addTopSwipe();
        }
    }

    /** 系统栏隐藏/恢复（API30+ 用 insets controller，低版本用 flags）。 */
    private void applyImmersive(boolean on) {
        try {
            if (!on) {
                getWindow().setStatusBarColor(Theme.bg(this, ui));
                getWindow().setNavigationBarColor(Theme.bg(this, ui));
            }
            android.view.Window w = getWindow();
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // 只有全屏时才让内容铺到系统栏下面；退出必须还原，否则地址栏会被状态栏盖住
                w.setDecorFitsSystemWindows(!on);
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    if (on) {
                        c.setSystemBarsBehavior(
                                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        c.hide(android.view.WindowInsets.Type.systemBars());
                    } else {
                        c.show(android.view.WindowInsets.Type.systemBars());
                    }
                }
            } else {
                android.view.View dv = w.getDecorView();
                if (on) {
                    dv.setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                } else {
                    dv.setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }

    private View topSwipe;

    /** 顶部 14dp 的滑条：向下滑一下恢复工具栏（Via 也是这个交互）。 */
    private void addTopSwipe() {
        if (topSwipe == null) {
            topSwipe = new View(this);
            topSwipe.setBackgroundColor(0x00000000);
            topSwipe.setOnTouchListener(new View.OnTouchListener() {
                float y0 = 0;
                @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                    switch (e.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            y0 = e.getY();
                            return true;
                        case android.view.MotionEvent.ACTION_UP:
                            if (e.getY() - y0 > Theme.dp(MainActivity.this, 18)) {
                                toggleFullscreenBars();
                            }
                            return true;
                        default:
                            return true;
                    }
                }
            });
        }
        if (topSwipe.getParent() != null) ((ViewGroup) topSwipe.getParent()).removeView(topSwipe);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 14));
        lp.gravity = Gravity.TOP;
        root.addView(topSwipe, lp);
    }

    private void removeTopSwipe() {
        if (topSwipe != null && topSwipe.getParent() != null) {
            ((ViewGroup) topSwipe.getParent()).removeView(topSwipe);
        }
    }

    private TextView fsChip;

    private void showFsChip() {
        if (fsChip == null) {
            fsChip = new TextView(this);
            fsChip.setText("退出全屏");
            fsChip.setTextSize(12);
            fsChip.setTextColor(0xCCFFFFFF);
            fsChip.setPadding(Theme.dp(this, 12), Theme.dp(this, 8),
                    Theme.dp(this, 12), Theme.dp(this, 8));
            fsChip.setBackground(Theme.rounded(0x99000000, Theme.dp(this, 16)));
            fsChip.setOnClickListener(v -> toggleFullscreenBars());
        }
        if (fsChip.getParent() != null) ((ViewGroup) fsChip.getParent()).removeView(fsChip);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = Theme.dp(this, 14);
        lp.rightMargin = Theme.dp(this, 10);
        root.addView(fsChip, lp);
        fsChip.setAlpha(1f);
        fsChip.animate().alpha(0.35f).setStartDelay(2500).setDuration(400).start();
    }

    private void hideFsChip() {
        if (fsChip != null && fsChip.getParent() != null) {
            ((ViewGroup) fsChip.getParent()).removeView(fsChip);
        }
    }

    public void showLinkMenuForCurrent() {
        showLinkMenu(lastLinkUrl, "");
    }

    public void linkPlayInternal() {
        if (lastLinkUrl == null || lastLinkUrl.isEmpty()) return;
        openPlayer(lastLinkUrl, "内置播放器");
    }

    public void linkNewTab() {
        if (lastLinkUrl != null && !lastLinkUrl.isEmpty()) newTab(lastLinkUrl, true);
    }

    public void linkOpenExternal() {
        if (lastLinkUrl != null && !lastLinkUrl.isEmpty()) openExternal(Uri.parse(lastLinkUrl));
    }

    public void linkDownload() {
        if (lastLinkUrl == null || lastLinkUrl.isEmpty()) return;
        askDownloadTarget(lastLinkUrl);
    }

    /** 点到媒体直链（m3u8/mp4/flv…）：让用户选用内置播放器还是下载。 */
    public void askMediaOpen(final String url) {
        Widgets.pick(this, ui, "这是个媒体地址", java.util.Arrays.asList(
                "用内置播放器播放（可倍速/画中画）",
                "下载到本地（下载/Vane）",
                "存进隐私空间"), i -> {
            if (i == 0) openPlayer(url, "内置播放器");
            else if (i == 1) askDownloadTarget(url);
            else {
                String name = "v-" + System.currentTimeMillis() + ".mp4";
                try {
                    String seg = Uri.parse(url).getLastPathSegment();
                    if (seg != null && seg.length() > 3) name = seg;
                } catch (Exception ignored) {}
                final String fname = name;
                com.vane.browser.core.Dl.start(this, url, fname, true,
                        new com.vane.browser.core.Dl.Cb() {
                            @Override public void progress(long d, long t) {}
                            @Override public void done(boolean ok, String path, String msg) {
                                runOnUiThread(() -> Widgets.info(MainActivity.this, ui,
                                        ok ? "已存入隐私空间" : "失败", ok ? path : msg));
                            }
                        });
            }
        });
    }

    /** 下载视频/文件时先问存哪：普通下载目录，还是隐私空间。 */
    public void askDownloadTarget(final String url) {
        Widgets.pick(this, ui, "下载到哪", java.util.Arrays.asList(
                "下载到本地（下载/Vane）",
                "下载到隐私空间（需验证才能看到）"), i -> {
            final boolean toVault = i == 1;
            String name = "vane-" + System.currentTimeMillis();
            try {
                String seg = Uri.parse(url).getLastPathSegment();
                if (seg != null && seg.length() > 3) name = seg;
            } catch (Exception ignored) {}
            final String fname = name;
            Widgets.toast(this, toVault ? "正在存入隐私空间…" : "开始下载…");
            com.vane.browser.core.Dl.start(this, url, fname, toVault,
                    new com.vane.browser.core.Dl.Cb() {
                        long last = 0;
                        @Override public void progress(long done, long total) {
                            long now = System.currentTimeMillis();
                            if (now - last < 500) return;
                            last = now;
                        }
                        @Override public void done(boolean ok, String path, String msg) {
                            runOnUiThread(() -> Widgets.info(MainActivity.this, ui,
                                    ok ? (toVault ? "已存入隐私空间" : "下载完成") : "下载失败",
                                    ok ? path : msg));
                        }
                    });
        });
    }

    public void linkMarkAd() {
        if (lastLinkUrl == null || lastLinkUrl.isEmpty()) return;
        String host = "";
        try {
            host = Uri.parse(lastLinkUrl).getHost();
        } catch (Exception ignored) {}
        if (host == null || host.isEmpty()) return;
        try {
            File dir = new File(getFilesDir(), "rules");
            if (!dir.exists()) dir.mkdirs();
            try (java.io.FileWriter w = new java.io.FileWriter(new File(dir, "hosts.txt"), true)) {
                w.write(host + System.lineSeparator());
            }
            Widgets.toast(this, "已标记 " + host);
        } catch (Exception ignored) {}
    }

    /** 长按页面元素 → 生成隐藏规则（广告拦截增强）。 */
    public void onElementLongPress(final String selector, final String label) {
        if (selector == null || selector.isEmpty()) return;
        BrowserTab t = currentTab();
        final String host = t == null || t.url == null ? "" : Uri.parse(t.url).getHost();
        Widgets.pick(this, ui, "这个元素：" + (label == null ? "" : trim(label, 20)),
                java.util.Arrays.asList("隐藏这个元素（本站生效）", "复制选择器"), i -> {
                    if (i == 1) {
                        copyText(selector);
                        Widgets.toast(this, "已复制");
                        return;
                    }
                    try {
                        if (host != null && !host.isEmpty()) {
                            com.vane.browser.core.Cosmetic.addCustom(host, selector);
                        }
                    } catch (Throwable ignored) {}
                    String key = "hide_" + (host == null ? "" : host);
                    String cur = Prefs.get(key, "");
                    Prefs.put(key, (cur.isEmpty() ? selector : cur + ", " + selector));
                    if (t != null) t.applyHiddenElements(host);
                    Widgets.toast(this, "已隐藏，可再长按恢复");
                });
    }

    /** 网页提交登录 → 询问是否保存密码。 */
    public void onPasswordCaptured(String pageUrl, String user, String pass) {
        if (!Prefs.getBool("pw_save", true)) return;
        String sig = (pageUrl == null ? "" : pageUrl) + "|" + (user == null ? "" : user)
                + "|" + (pass == null ? "" : pass);
        long now = System.currentTimeMillis();
        if (sig.equals(lastPwSig) && now - lastPwAt < 4000) return;   // 同一次登录只问一次
        lastPwSig = sig;
        lastPwAt = now;
        String host = "";
        try {
            host = Uri.parse(pageUrl == null ? "" : pageUrl).getHost();
        } catch (Exception ignored) {}
        if (host == null || host.isEmpty()) return;
        final String h = host;
        for (String x : Prefs.get("pw_no_hosts", "").split(",")) {
            if (x.trim().equalsIgnoreCase(h)) return;      // 本站不提示
        }
        String[] old = com.vane.browser.core.Passwords.get(this, h);
        if (old != null && pass.equals(old[1])) return;          // 已经存过同样的
        Widgets.choice(this, ui, "保存密码？",
                h + (user == null || user.isEmpty() ? "" : "\n账号：" + user),
                "保存", () -> {
                    com.vane.browser.core.Passwords.save(this, h, user, pass);
                },
                "不保存", null);
    }

    /** 自定义字体：选一个字体文件，页面用它显示。 */
    public void pickFont() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_FONT);
        } catch (Exception e) {
            Widgets.toast(this, "打不开选择器");
        }
    }

    private void applyPickedFont(android.net.Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            File dir = new File(getFilesDir(), "fonts");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "custom-font");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 6L * 1024 * 1024) { out.close(); in.close(); f.delete(); 
                    Widgets.toast(this, "字体太大（>6MB），换个小点的"); return; }
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            Prefs.put("font_file", f.getAbsolutePath());
            Prefs.putBool("font_custom", true);
            Widgets.toast(this, "字体已启用，刷新页面生效");
            for (BrowserTab t : tabs) t.web.reload();
        } catch (Exception e) {
            Widgets.toast(this, "导入失败：" + e.getMessage());
        }
    }

    /** 本站自动翻译开关。 */
    public void toggleAutoTranslate() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        String h = "";
        try { h = Uri.parse(t.url).getHost(); } catch (Exception ignored) {}
        if (h == null || h.isEmpty()) return;
        java.util.List<String> list = new ArrayList<>();
        for (String x : Prefs.get("auto_translate", "").split(",")) if (!x.trim().isEmpty()) list.add(x.trim());
        boolean on;
        if (list.contains(h)) { list.remove(h); on = false; }
        else { list.add(h); on = true; }
        Prefs.put("auto_translate", TextUtils.join(",", list));
        Widgets.toast(this, on ? "本站以后自动翻译" : "已关闭本站自动翻译");
    }

    /** 本站不再提示保存密码。 */
    public void togglePasswordSite() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        String h = "";
        try { h = Uri.parse(t.url).getHost(); } catch (Exception ignored) {}
        if (h == null || h.isEmpty()) return;
        java.util.List<String> list = new ArrayList<>();
        for (String x : Prefs.get("pw_no_hosts", "").split(",")) if (!x.trim().isEmpty()) list.add(x.trim());
        boolean off;
        if (list.contains(h)) { list.remove(h); off = false; }
        else { list.add(h); off = true; }
        Prefs.put("pw_no_hosts", TextUtils.join(",", list));
        Widgets.toast(this, off ? "本站不再提示保存密码" : "本站恢复提示");
    }

    /** 拦截链接规则（每行一个包含匹配的片段）。 */
    public void editBlockLinks() {
        Widgets.input(this, ui, "拦截链接：一行一个关键词或域名片段", Prefs.get("block_links", ""), true, v -> {
            Prefs.put("block_links", v);
            Widgets.toast(this, "已保存，刷新页面生效");
        });
    }

    /** 预测返回手势是系统设置，这里只能带你过去。 */
    public void openPredictiveBackSetting() {
        Widgets.info(this, ui, "预测返回手势",
                "这是 Android 14 的系统功能，应用不能自己关。\n"
                        + "路径：设置 → 系统 → 手势 → 系统导航 → 关闭「预测性返回」。");
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        } catch (Exception ignored) {}
    }

    /** 代理/下载时的常驻通知（前台提示）。 */
    public void showOngoingNotice() {
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String ch = "vane_status";
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel c = new android.app.NotificationChannel(
                        ch, "运行状态", android.app.NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(c);
            }
            android.app.Notification n = new android.app.Notification.Builder(this, ch)
                    .setContentTitle("Vane正在运行")
                    .setContentText("标签 " + tabs.size() + " 个 · 代理" +
                            (ProxyManager.get(this).isRunning() ? "已连接" : "未开启"))
                    .setSmallIcon(R.drawable.ic_settings)
                    .setOngoing(true)
                    .build();
            nm.notify(1001, n);
        } catch (Throwable ignored) {}
    }
    /** 内建下载器：分片并发下载当前页地址。 */
    /** 内建下载任务：暂停、继续、取消。 */
    public void showDownloadTasks() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        box.addView(Widgets.text(this, "下载任务", 18, Theme.fg(this, ui)));
        final android.app.Dialog[] holder = new android.app.Dialog[1];

        java.util.List<com.vane.browser.core.Dl.Task> tasks =
                new ArrayList<>(com.vane.browser.core.Dl.TASKS);
        if (tasks.isEmpty()) {
            TextView e = Widgets.text(this, "还没有任务", 13, Theme.hint(this, ui));
            e.setPadding(0, Theme.dp(this, 20), 0, Theme.dp(this, 20));
            box.addView(e);
        }
        for (final com.vane.browser.core.Dl.Task t : tasks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(this, 12), 0, Theme.dp(this, 12));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Widgets.text(this, trim(t.name, 26), 14, Theme.fg(this, ui)));
            String pct = t.total > 0 ? (t.done * 100 / t.total) + "%" : "";
            String st = t.state == 1 ? ("下载中 " + pct) : t.state == 2 ? "已暂停"
                    : t.state == 3 ? "已完成" : t.state == 5 ? "已取消" : ("失败 " + (t.msg == null ? "" : t.msg));
            texts.addView(Widgets.text(this, st, 11, Theme.hint(this, ui)));
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            // 暂停中的任务同时给「继续」和「删除」：删除会把断点文件一起清掉
            if (t.state == 2) {
                TextView del = Widgets.text(this, "删除", 13, Theme.hint(this, ui));
                del.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), Theme.dp(this, 4), Theme.dp(this, 6));
                del.setOnClickListener(v -> {
                    com.vane.browser.core.Dl.remove(this, t);
                    if (holder[0] != null) holder[0].dismiss();
                    root.postDelayed(this::showDownloadTasks, 250);
                });
                row.addView(del);
            }
            TextView btn = Widgets.text(this,
                    t.state == 1 ? "暂停" : (t.state == 2 ? "继续" : "删除"),
                    13, Theme.accent(ui));
            btn.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), Theme.dp(this, 4), Theme.dp(this, 6));
            btn.setOnClickListener(v -> {
                if (t.state == 1) com.vane.browser.core.Dl.pause(t);
                else if (t.state == 2) com.vane.browser.core.Dl.resume(t, this, null);
                else com.vane.browser.core.Dl.remove(this, t);   // 连断点文件一起清
                if (holder[0] != null) holder[0].dismiss();
                root.postDelayed(this::showDownloadTasks, 250);
            });
            row.addView(btn);
            box.addView(row);
        }
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    public void downloadBuiltin() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        final String url = t.url;
        String name = "vane-" + System.currentTimeMillis() + ".bin";
        try {
            String last = Uri.parse(url).getLastPathSegment();
            if (last != null && last.contains(".")) name = last;
        } catch (Exception ignored) {}
        final String finalName = name;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 18), 0, Theme.dp(this, 18), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        box.addView(Widgets.text(this, "内建下载器", 16, Theme.fg(this, ui)));
        final TextView line = Widgets.text(this, "准备中…", 13, Theme.hint(this, ui));
        line.setPadding(0, Theme.dp(this, 10), 0, 0);
        box.addView(line);
        final android.app.Dialog d = com.vane.browser.ui.Sheet.show(this, ui, box, true);

        com.vane.browser.core.Dl.start(this, url, finalName, new com.vane.browser.core.Dl.Cb() {
            private long lastTick = 0;
            @Override public void progress(long done, long total) {
                long now = System.currentTimeMillis();
                if (now - lastTick < 200) return;
                lastTick = now;
                final String txt = total > 0
                        ? (done * 100 / total) + "%  " + (done / 1024) + " / " + (total / 1024) + " KB"
                        : (done / 1024) + " KB";
                runOnUiThread(() -> line.setText(txt));
            }
            @Override public void done(boolean ok, String path, String msg) {
                runOnUiThread(() -> {
                    if (d != null) d.dismiss();
                    Widgets.info(MainActivity.this, ui, ok ? "下载完成" : "下载失败", ok ? path : msg);
                });
            }
        });
    }



    /** 选一个外置播放器（对应 Via 的「外置视频播放器」）。 */
    public void pickExternalPlayer() {
        try {
            Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(
                    Uri.parse("https://example.com/a.mp4"), "video/*");
            java.util.List<android.content.pm.ResolveInfo> list =
                    getPackageManager().queryIntentActivities(probe, 0);
            List<String> names = new ArrayList<>();
            final List<String> pkgs = new ArrayList<>();
            for (android.content.pm.ResolveInfo ri : list) {
                names.add(ri.loadLabel(getPackageManager()).toString());
                pkgs.add(ri.activityInfo.packageName);
            }
            if (names.isEmpty()) {
                Widgets.toast(this, "没有找到能播视频的应用");
                return;
            }
            names.add("用系统默认（每次询问）");
            Widgets.pick(this, ui, "外置播放器", names, i -> {
                if (i == names.size() - 1) {
                    Prefs.put("player_pkg", "");
                    Widgets.toast(this, "已恢复系统默认");
                } else {
                    Prefs.put("player_pkg", pkgs.get(i));
                    Widgets.toast(this, "已选择：" + names.get(i));
                }
            });
        } catch (Exception e) {
            Widgets.toast(this, "读取失败：" + e.getMessage());
        }
    }

    /** 用选定的外置播放器打开当前页里的视频。 */
    public void playExternal(String url) {
        String pkg = Prefs.get("player_pkg", "");
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*");
            if (!pkg.isEmpty()) i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Widgets.toast(this, "打不开：" + e.getMessage());
        }
    }

    /** 翻译源连通性测试。 */
    public void testTranslateSource() {
        final String[] targets = {
                "https://translate.google.com/",
                "https://fanyi.baidu.com/"
        };
        final String[] labels = {"谷歌翻译", "百度翻译"};
        Widgets.toast(this, "正在测试…");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < targets.length; i++) {
                boolean ok = false;
                try {
                    java.net.HttpURLConnection c =
                            (java.net.HttpURLConnection) new java.net.URL(targets[i]).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    c.setRequestMethod("HEAD");
                    int code = c.getResponseCode();
                    ok = code >= 200 && code < 500;
                } catch (Exception ignored) {}
                sb.append(labels[i]).append("：").append(ok ? "可用" : "不可用").append('\n');
            }
            runOnUiThread(() -> Widgets.info(this, ui, "翻译源连通性", sb.toString()));
        }, "translate-test").start();
    }

    /** 打印 / 存 PDF（对应 Via 的「打印/PDF」）。 */
    public void printPage() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        try {
            android.print.PrintManager pm =
                    (android.print.PrintManager) getSystemService(Context.PRINT_SERVICE);
            String job = (t.title == null ? "Vane网页" : t.title);
            pm.print(job, t.web.createPrintDocumentAdapter(job),
                    new android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .build());
        } catch (Exception e) {
            Widgets.toast(this, "打印不可用：" + e.getMessage());
        }
    }

    /** 用其它应用打开当前页。 */
    public void openInOtherApp() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        openExternal(Uri.parse(t.url));
    }

    /** 网站设定：把本站的几个开关集中在一个面板里。 */
    public void showSiteSettings() {
        final BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            Widgets.toast(this, "先进一个网页");
            return;
        }
        String host = "";
        try {
            host = Uri.parse(t.url).getHost();
        } catch (Exception ignored) {}
        final String h = host == null ? "" : host;
        boolean noad = false;
        for (String x : Prefs.get("site_noad", "").split(",")) {
            if (x.trim().equalsIgnoreCase(h)) noad = true;
        }
        String zoom = Prefs.get("site_font:" + h, "");
        List<String> rows = new ArrayList<>();
        rows.add("字体大小：" + (zoom.isEmpty() ? "跟随全局(" + Prefs.getInt("font_zoom", 100) + "%)" : zoom + "%"));
        rows.add("电脑模式：" + (desktopMode ? "开" : "关"));
        rows.add("本站广告拦截：" + (noad ? "关" : "开"));
        rows.add("浏览器标识：" + ("desktop".equals(Prefs.get("ua_mode", "default")) ? "桌面版"
                : ("iphone".equals(Prefs.get("ua_mode", "default")) ? "iPhone" : "默认")));
        Widgets.pick(this, ui, "网站设定 · " + h, rows, i -> {
            switch (i) {
                case 0: {
                    int cur = Prefs.getInt("site_font:" + h, 0);
                    if (cur == 0) cur = Prefs.getInt("font_zoom", 100);
                    cur += 10;
                    if (cur > 200) {
                        Prefs.put("site_font:" + h, "");
                        t.web.getSettings().setTextZoom(Prefs.getInt("font_zoom", 100));
                        Widgets.toast(this, "本站恢复跟随全局");
                    } else {
                        Prefs.put("site_font:" + h, String.valueOf(cur));
                        t.web.getSettings().setTextZoom(cur);
                        Widgets.toast(this, "本站字体 " + cur + "%");
                    }
                    break;
                }
                case 1:
                    dispatch("desktop");
                    break;
                case 2:
                    toggleSiteAdblock();
                    break;
                default:
                    dispatch("ua");
                    break;
            }
        });
    }

    /** 订阅诊断：把每个订阅的 HTTP 状态码 + 返回开头几行打出来，判断是链接问题还是软件问题。 */
    public void diagnoseSubs() {
        final ProxyManager pm = ProxyManager.get(this);
        if (pm.subscriptions() == null || pm.subscriptions().isEmpty()) {
            Widgets.info(this, ui, "订阅诊断", "还没有订阅，先加一个链接。");
            return;
        }
        Widgets.toast(this, "正在诊断…");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (com.vane.browser.proxy.Subscription sub : pm.subscriptions()) {
                sb.append("链接：").append(trim(sub.url, 60)).append('\n');
                try {
                    String body = ProxyManager.fetchRaw(sub.url);
                    int nodes = 0;
                    try { nodes = com.vane.browser.proxy.SubscriptionParser.parse(body).size(); } catch (Exception ignored) {}
                    sb.append("HTTP：200\n");
                    sb.append("字节：").append(body.length()).append('\n');
                    sb.append("解析节点：").append(nodes).append('\n');
                    sb.append("开头：").append(trim(body.replaceAll("\\s+", " "), 110)).append('\n');
                } catch (Exception e) {
                    sb.append("错误：").append(e.getMessage()).append('\n');
                }
                sb.append("\n");
            }
            runOnUiThread(() -> Widgets.info(this, ui, "订阅诊断", sb.toString()));
        }, "sub-diag").start();
    }

    /** 订阅拉不到时：直接粘贴内容导入（机场页面复制或另存的文件）。 */
    public void importSubFromText() {
        Widgets.input(this, ui, "粘贴订阅内容（base64 或 ss:// vmess:// 列表）", "", true, text -> {
            if (text == null || text.trim().isEmpty()) return;
            Widgets.input(this, ui, "给这个订阅起个名字", "手动订阅", false, name -> {
                ProxyManager pm = ProxyManager.get(this);
                pm.addSubscriptionFromText(name == null || name.trim().isEmpty() ? "手动订阅" : name.trim(),
                        text.trim());
                Widgets.toast(this, "已导入");
            });
        });
    }

    /** 广告拦截强度：标准 / 严格。 */
    public void toggleAdLevel() {
        Widgets.pick(this, ui, "拦截强度", java.util.Arrays.asList(
                "标准（只拦广告域名，几乎不误伤）",
                "严格（再加 URL 规则，能拦广告接口/脚本）"), i -> {
            boolean strict = i == 1;
            Prefs.putBool("ad_strict", strict);
            try { blocker.setStrict(strict); blocker.reload(); } catch (Throwable ignored) {}
            BrowserTab t = currentTab();
            if (t != null) t.web.reload();
        });
    }

    /** 更新广告规则：从你给的地址拉 remote-host.txt 与 remote-url.txt。 */
    public void updateAdRules() {
        final String base = Prefs.get("ad_rules_url", "");
        if (base.isEmpty()) {
            Widgets.input(this, ui, "规则地址（目录 URL，需含 remote-host.txt 与 remote-url.txt）",
                    "https://example.com/vane-rules/", true, v -> {
                        Prefs.put("ad_rules_url", v.trim());
                        Widgets.toast(this, "已保存，再点一次「更新广告规则」");
                    });
            return;
        }
        Widgets.toast(this, "正在更新规则…");
        new Thread(() -> {
            int okFiles = 0;
            for (String name : new String[]{"remote-host.txt", "remote-url.txt", "remote-filters.txt"}) {
                try {
                    String url = base.endsWith("/") ? base + name : base + "/" + name;
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(url).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "Vane/1.0");
                    if (c.getResponseCode() / 100 != 2) continue;
                    java.io.File dir = new java.io.File(getFilesDir(), "rules");
                    if (!dir.exists()) dir.mkdirs();
                    java.io.File f = new java.io.File(dir, name);
                    java.io.InputStream in = c.getInputStream();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                    okFiles++;
                } catch (Exception ignored) {}
            }
            final int ok = okFiles;
            runOnUiThread(() -> {
                if (ok == 0) {
                    Widgets.info(this, ui, "更新失败", "没拉到任何规则文件。检查地址是否可访问。");
                } else {
                    try { blocker.reload(); } catch (Throwable ignored) {}
                    Widgets.info(this, ui, "规则已更新",
                            "成功拉取 " + ok + " 个文件。\n当前规则总数：" + blocker.ruleCount());
                }
            });
        }, "ad-rules").start();
    }

    /** 拦截自检：拿内置清单算拦截率，并列出漏掉的。 */
    public void checkAdblock() {
        try {
            com.vane.browser.core.FilterEngine.load(this);
            blocker.reload();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    getAssets().open("adtest.txt")));
            String line;
            int total = 0, hit = 0;
            StringBuilder miss = new StringBuilder();
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                total++;
                boolean blocked = blocker.blockedByRules(line)
                        || com.vane.browser.core.FilterEngine.blocked("https://" + line + "/");
                if (blocked) hit++;
                else miss.append("· ").append(line).append('\n');
            }
            r.close();
            int pct = total == 0 ? 0 : hit * 100 / total;
            String body = "拦截 " + hit + " / " + total + "  =  " + pct + "%\n\n";
            body += "域名规则 " + blocker.ruleCount() + " 条\n"
                    + "网络规则 " + com.vane.browser.core.FilterEngine.size() + " 条\n"
                    + "元素规则 " + com.vane.browser.core.Cosmetic.ruleCount() + " 条\n"
                    + (Prefs.getBool("ad_strict", false) ? "当前：严格模式\n" : "当前：标准模式（启用严格可再提高）\n");
            if (miss.length() > 0) body += "\n漏掉：\n" + miss;
            Widgets.info(this, ui, "拦截自检", body);
        } catch (Exception e) {
            Widgets.info(this, ui, "拦截自检", "读不到清单：" + e.getMessage());
        }
    }

    /** 广告拦截统计。 */
    public void showAdStats() {
        long n = blocker.totalBlocked;
        long mb = blocker.bytesSaved / (1024 * 1024);
        Widgets.info(this, ui, "广告拦截统计",
                "本次运行拦截 " + n + " 个请求\n约省流量 " + mb + " MB（按每个 30KB 估算）\n\n"
                        + "域名规则：" + blocker.ruleCount() + " 条\n"
                        + "网络规则：" + com.vane.browser.core.FilterEngine.size() + " 条"
                        + (Prefs.getBool("ad_strict", false) ? "（严格模式生效）" : "（标准模式未启用）") + "\n"
                        + "元素规则：" + com.vane.browser.core.Cosmetic.ruleCount() + " 条");
    }

    /** 本站不拦广告（按站点记忆）。 */
    public void toggleSiteAdblock() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        String h = "";
        try {
            h = Uri.parse(t.url).getHost();
        } catch (Exception ignored) {}
        if (h == null || h.isEmpty()) return;
        String cur = Prefs.get("site_noad", "");
        java.util.List<String> list = new ArrayList<>();
        for (String x : cur.split(",")) if (!x.trim().isEmpty()) list.add(x.trim());
        boolean off;
        if (list.contains(h)) {
            list.remove(h);
            off = false;
        } else {
            list.add(h);
            off = true;
        }
        Prefs.put("site_noad", TextUtils.join(",", list));
        t.web.reload();
    }


    /** 广告点选模式：点页面上哪块，就把那块隐藏掉。 */
    public void markAdPick() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            if (!isFinishing()) Widgets.toast(this, "先进一个网页再点选");
            return;
        }
        t.web.evaluateJavascript(VideoSniffer.PICKER_JS, null);
        Widgets.toast(this, "点一下要隐藏的广告区域");
    }

    /** 从网址安装用户脚本（.user.js）。 */
    public void installScriptFromUrl() {
        Widgets.input(this, ui, "脚本地址（.user.js 链接）", "", true, url -> {
            final String u = url.trim();
            if (!u.startsWith("http")) return;
            Widgets.toast(this, "正在下载脚本…");
            new Thread(() -> {
                String body = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                    in.close();
                    body = b.toString("UTF-8");
                } catch (Exception ignored) {}
                final String code = body;
                runOnUiThread(() -> {
                    if (code == null) {
                        Widgets.toast(this, "下载失败");
                        return;
                    }
                    java.util.List<com.vane.browser.core.UserScripts.Script> list =
                            com.vane.browser.core.UserScripts.all(this);
                    com.vane.browser.core.UserScripts.Script sc =
                            com.vane.browser.core.UserScripts.parse(code);
                    sc.src = u;
                    list.add(sc);
                    com.vane.browser.core.UserScripts.save(this, list);
                    Widgets.toast(this, "已安装：" + sc.name);
                });
            }, "script-dl").start();
        });
    }

    /** 脚本仓库：从一个 JSON 索引里浏览并安装。 */
    public void scriptRepo() {
        String index = Prefs.get("script_index", "");
        if (index.isEmpty()) {
            Widgets.input(this, ui, "脚本仓库索引地址（JSON）",
                    "https://example.com/vane-scripts.json", true, v -> {
                        Prefs.put("script_index", v.trim());
                        Widgets.toast(this, "已保存，再点一次「脚本仓库」");
                    });
            return;
        }
        Widgets.toast(this, "正在读取仓库…");
        new Thread(() -> {
            java.util.List<String> names = new java.util.ArrayList<>();
            java.util.List<String> urls = new java.util.ArrayList<>();
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(index).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                org.json.JSONArray arr = new org.json.JSONArray(b.toString("UTF-8"));
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    names.add(o.optString("name", "脚本"));
                    urls.add(o.optString("url", ""));
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                if (names.isEmpty()) {
                    Widgets.toast(this, "仓库读取失败或为空");
                    return;
                }
                Widgets.pick(this, ui, "脚本仓库 · " + names.size() + " 个", names, idx -> {
                    final String u = urls.get(idx);
                    if (!u.startsWith("http")) return;
                    installScriptAt(u);
                });
            });
        }, "script-repo").start();
    }

    private void installScriptAt(final String u) {
        Widgets.toast(this, "正在安装…");
        new Thread(() -> {
            String code = null;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                code = b.toString("UTF-8");
            } catch (Exception ignored) {}
            final String body = code;
            runOnUiThread(() -> {
                if (body == null) {
                    Widgets.toast(this, "下载失败");
                    return;
                }
                java.util.List<com.vane.browser.core.UserScripts.Script> list =
                        com.vane.browser.core.UserScripts.all(this);
                com.vane.browser.core.UserScripts.Script sc =
                        com.vane.browser.core.UserScripts.parse(body);
                sc.src = u;
                list.add(sc);
                com.vane.browser.core.UserScripts.save(this, list);
                Widgets.toast(this, "已安装：" + sc.name);
            });
        }, "script-install").start();
    }

    /** 检查脚本更新：重新下载来源，内容不同就替换。 */
    public void updateScripts() {
        final java.util.List<com.vane.browser.core.UserScripts.Script> list =
                com.vane.browser.core.UserScripts.all(this);
        if (list.isEmpty()) {
            Widgets.toast(this, "还没有脚本");
            return;
        }
        Widgets.toast(this, "正在检查更新…");
        new Thread(() -> {
            int updated = 0;
            for (com.vane.browser.core.UserScripts.Script sc : list) {
                if (sc.src == null || sc.src.isEmpty()) continue;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(sc.src).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                    in.close();
                    String code = b.toString("UTF-8");
                    if (code != null && !code.equals(sc.code)) {
                        sc.code = code;
                        updated++;
                    }
                } catch (Exception ignored) {}
            }
            final int up = updated;
            com.vane.browser.core.UserScripts.save(this, list);
            runOnUiThread(() -> Widgets.toast(this, up == 0 ? "脚本都是最新的" : ("已更新 " + up + " 个脚本")));
        }, "script-up").start();
    }

    /** 用户脚本管理：添加 / 启停 / 查看 / 删除。 */
    public void showUserScripts() {
        final java.util.List<com.vane.browser.core.UserScripts.Script> list =
                com.vane.browser.core.UserScripts.all(this);
        List<String> rows = new ArrayList<>();
        for (com.vane.browser.core.UserScripts.Script sc : list) {
            rows.add((sc.enabled ? "● " : "○ ") + sc.name + "  ·  " + sc.matches.get(0));
        }
        rows.add("＋ 添加脚本（粘贴源码）");
        rows.add("⇣ 从网址安装脚本");
        rows.add("⟳ 检查脚本更新");
        rows.add("☰ 脚本仓库");
        Widgets.pick(this, ui, "用户脚本 · " + list.size() + " 个", rows, idx -> {
            if (idx == list.size() + 1) {
                installScriptFromUrl();
                return;
            }
            if (idx == list.size() + 2) {
                updateScripts();
                return;
            }
            if (idx == list.size() + 3) {
                scriptRepo();
                return;
            }
            if (idx == list.size()) {
                Widgets.input(this, ui, "粘贴用户脚本源码（含 @name / @match）", "", true, src -> {
                    if (src == null || src.trim().isEmpty()) return;
                    com.vane.browser.core.UserScripts.Script sc =
                            com.vane.browser.core.UserScripts.parse(src);
                    list.add(sc);
                    com.vane.browser.core.UserScripts.save(this, list);
                    Widgets.toast(this, "已添加：" + sc.name);
                });
                return;
            }
            final com.vane.browser.core.UserScripts.Script sc = list.get(idx);
            Widgets.pick(this, ui, sc.name, java.util.Arrays.asList(
                    sc.enabled ? "停用" : "启用", "查看代码", "删除"), k -> {
                if (k == 0) {
                    sc.enabled = !sc.enabled;
                    com.vane.browser.core.UserScripts.save(this, list);
                    Widgets.toast(this, sc.enabled ? "已启用" : "已停用");
                } else if (k == 1) {
                    Widgets.info(this, ui, sc.name, sc.code.length() > 4000
                            ? sc.code.substring(0, 4000) + "…" : sc.code);
                } else {
                    list.remove(sc);
                    com.vane.browser.core.UserScripts.save(this, list);
                    Widgets.toast(this, "已删除");
                }
            });
        });
    }

    /** 云同步面板：填 WebDAV 地址后上传/下载。 */
    public void showSync() {
        String url = Prefs.get("sync_url", "");
        List<String> ops = new ArrayList<>();
        ops.add("同步地址" + (url.isEmpty() ? "（未填）" : "：" + trim(url, 28)));
        ops.add("账号" + (Prefs.get("sync_user", "").isEmpty() ? "（未填）" : "：" + Prefs.get("sync_user", "")));
        ops.add("密码" + (Prefs.get("sync_pass", "").isEmpty() ? "（未填）" : "：已设置"));
        String last = Prefs.get("sync_last", "");
        if (!last.isEmpty()) ops.add("上次结果：" + last);
        ops.add("立即上传");
        ops.add("立即下载");
        final int off = last.isEmpty() ? 0 : 1;
        Widgets.pick(this, ui, "云同步（WebDAV）", ops, i -> {
            if (off == 1 && i == 3) {           // 「上次结果」那一行点了就清掉
                Prefs.put("sync_last", "");
                showSync();
                return;
            }
            i -= off;
            switch (i) {
                case 0:
                    Widgets.input(this, ui, "WebDAV 文件地址（如 https://dav.jianguoyun.com/dav/vane.json）",
                            url, true, v -> {
                                Prefs.put("sync_url", v.trim());
                                showSync();      // 填完回面板，省得每次重新点进来
                            });
                    break;
                case 1:
                    Widgets.input(this, ui, "账号", Prefs.get("sync_user", ""), false, v -> {
                        Prefs.put("sync_user", v.trim());
                        showSync();
                    });
                    break;
                case 2:
                    Widgets.input(this, ui, "密码（应用专用密码）", "", false, v -> {
                        Prefs.put("sync_pass", v.trim());
                        showSync();
                    });
                    break;
                case 3:
                    Widgets.toast(this, "正在上传…");
                    com.vane.browser.core.Sync.upload(this, (ok, msg) -> runOnUiThread(() ->
                            Widgets.toast(this, (ok ? "上传成功 · " : "") + msg)));
                    break;
                default:
                    Widgets.toast(this, "正在对比…");
                    new Thread(() -> {
                        long remote = com.vane.browser.core.Sync.remoteTime(this);
                        long local = Prefs.getLong("sync_local_time", 0);
                        runOnUiThread(() -> {
                            if (remote > local) {
                                Widgets.pick(this, ui, "服务器上有更新的数据", java.util.Arrays.asList(
                                        "用服务器的（覆盖本机）",
                                        "保留本机并上传（覆盖服务器）",
                                        "先不动"), k -> {
                                    if (k == 0) doSyncDownload();
                                    else if (k == 1) {
                                        Widgets.toast(this, "正在上传本机数据…");
                                        com.vane.browser.core.Sync.upload(this, (ok, msg) ->
                                                runOnUiThread(() -> Widgets.toast(this,
                                                        (ok ? "已覆盖服务器 · " : "上传失败 · ") + msg)));
                                    }
                                });
                            } else {
                                doSyncDownload();
                            }
                        });
                    }, "sync-check").start();
                    break;
            }
        });
    }

    private void doSyncDownload() {
        Widgets.toast(this, "正在下载…");
        com.vane.browser.core.Sync.download(this, (ok2, msg2) -> runOnUiThread(() -> {
            Widgets.toast(this, msg2);
            if (ok2) {
                Prefs.putLong("sync_local_time", System.currentTimeMillis());
                rebuildChrome();
            }
        }));
    }

    /** 主题包：导出成一个小 json 文件。 */
    public void exportTheme() {
        try {
            org.json.JSONObject t = new org.json.JSONObject();
            t.put("app", "vane-theme");
            t.put("name", ui.theme);
            t.put("accent", ui.accent);
            t.put("customBg", ui.customBg);
            t.put("topBarHeight", ui.topBarHeight);
            t.put("bottomBarHeight", ui.bottomBarHeight);
            t.put("corner", ui.corner);
            saveToDownload("vane-theme-" + stamp() + ".json", "application/json", t.toString(2));
        } catch (Exception e) {
            Widgets.toast(this, "导出失败：" + e.getMessage());
        }
    }

    public void importTheme() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_THEME);
        } catch (Exception e) {
            Widgets.toast(this, "打不开选择器");
        }
    }

    private void doImportTheme(android.net.Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            org.json.JSONObject t = new org.json.JSONObject(b.toString("UTF-8"));
            ui.theme = t.optString("name", ui.theme);
            ui.accent = t.optString("accent", ui.accent);
            ui.customBg = t.optString("customBg", ui.customBg);
            ui.topBarHeight = t.optInt("topBarHeight", ui.topBarHeight);
            ui.bottomBarHeight = t.optInt("bottomBarHeight", ui.bottomBarHeight);
            ui.corner = t.optInt("corner", ui.corner);
            ui.save();
            Theme.applyToWindow(this, ui);
            root.setBackgroundColor(Theme.bg(this, ui));
            rebuildChrome();
            Widgets.toast(this, "主题已导入");
        } catch (Exception e) {
            Widgets.toast(this, "导入失败：" + e.getMessage());
        }
    }



    /** 密码解锁：有锁屏就先验证一次。 */
    public void showPasswordsGated() {
        try {
            android.app.KeyguardManager km =
                    (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isDeviceSecure()) {
                Intent i = km.createConfirmDeviceCredentialIntent("查看保存的密码", "验证后可见");
                if (i != null) {
                    startActivityForResult(i, REQ_UNLOCK);
                    return;
                }
            }
        } catch (Exception ignored) {}
        showPasswords();
    }

    /** 从 CSV 导入密码（Chrome/Edge 导出格式：name,url,username,password）。 */
    public void importPasswordsCsv() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_PWCSV);
        } catch (Exception e) {
            Widgets.toast(this, "打不开选择器");
        }
    }

    private void doImportCsv(android.net.Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            int added = 0, skipped = 0;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) { skipped++; continue; }
                String url = parts[1];
                String user = parts[2];
                String pass = parts[parts.length - 1];
                String host = "";
                try {
                    host = Uri.parse(url).getHost();
                } catch (Exception ignored) {}
                if (host == null || host.isEmpty() || pass.isEmpty()) { skipped++; continue; }
                com.vane.browser.core.Passwords.save(this, host, user, pass);
                added++;
            }
            r.close();
            Widgets.info(this, ui, "导入完成", "新增 " + added + " 条，跳过 " + skipped + " 条。");
        } catch (Exception e) {
            Widgets.toast(this, "导入失败：" + e.getMessage());
        }
    }

    /** 密码管理面板：查看 / 复制 / 删除。 */
    public void showPasswords() {
        final java.util.List<String[]> list = com.vane.browser.core.Passwords.list(this);
        if (list.isEmpty()) {
            Widgets.info(this, ui, "密码管理", "还没有保存过密码。\n在网页里登录后会询问是否保存。");
            return;
        }
        List<String> rows = new ArrayList<>();
        for (String[] e : list) rows.add(e[0] + (e[1].isEmpty() ? "" : "  ·  " + e[1]));
        Widgets.pick(this, ui, "密码 · " + list.size() + " 条", rows, idx -> {
            final String host = list.get(idx)[0];
            String[] cred = com.vane.browser.core.Passwords.get(this, host);
            Widgets.pick(this, ui, host, java.util.Arrays.asList("复制账号", "复制密码", "删除这条"), k -> {
                if (k == 0) copyText(cred[0]);
                else if (k == 1) copyText(cred[1]);
                else {
                    com.vane.browser.core.Passwords.delete(this, host);
                    Widgets.toast(this, "已删除");
                }
            });
        });
    }

    /** 长按图片：保存 / 复制地址 / 新标签打开 / 以图搜图。 */
    public void onImageLongPress(final String src, final String alt) {
        if (src == null || src.isEmpty()) return;
        Widgets.pick(this, ui, "图片", java.util.Arrays.asList(
                "保存图片", "复制图片地址", "新标签打开", "以图搜图"), i -> {
            switch (i) {
                case 0:
                    Downloads.enqueue(this, src, userAgent(), "image", "image/*", "");
                    Widgets.toast(this, "开始下载图片");
                    break;
                case 1:
                    copyText(src);
                    Widgets.toast(this, "已复制图片地址");
                    break;
                case 2:
                    newTab(src, true);
                    break;
                default:
                    newTab("https://lens.google.com/uploadbyurl?url=" + Uri.encode(src), true);
                    break;
            }
        });
    }

    public void showLinkMenu(String url, String text) {
        if (url != null && url.startsWith("vane:")) return;   // 内置页链接不弹菜单
        if (url == null || url.isEmpty()) {
            Widgets.toast(this, "没有链接");
            return;
        }
        lastLinkUrl = url;
        final java.util.List<String> ids = new ArrayList<>(ui.linkMenu);
        // 媒体直链额外插一条「用内置播放器播放」
        try {
            String low = url.toLowerCase();
            if (low.matches(".*\\.(m3u8|mp4|flv|mkv|webm|mpd|mov)(\\?.*)?$")
                    && !ids.contains("linkPlay")) {
                ids.add(0, "linkPlay");
            }
        } catch (Throwable ignored) {}
        List<String> labels = new ArrayList<>();
        for (String id : ids) labels.add(Actions.label(id));
        Widgets.pick(this, ui, trim(url, 60), labels, idx -> dispatch(ids.get(idx)));
    }

    public void showAbout() {
        Widgets.info(this, ui, "关于Vane", "极简浏览器框架\n\n"
                        + "· 搜索引擎：" + SearchEngines.all().size() + " 个（内置 + 自定义）\n"
                        + "· 广告规则：" + blocker.ruleCount() + " 条，纯本地\n"
                        + "· 可自定义动作：" + Actions.all().size() + " 个，任意按键/手势都能绑定\n"
                        + "· 分流：国内域名 " + ProxyManager.get(this).rules.cnDomainCount() + " 条，国内网段 "
                        + ProxyManager.get(this).rules.cnIpCount() + " 条\n"
                        + "· " + ProxyManager.get(this).kernelInfo() + "\n\n"
                        + "没有任何广告、没有统计、没有后台唤醒。");
    }

    // ================= 网页事件 =================

    public void askPermission(final PermissionRequest request) {
        Widgets.choice(this, ui, "网页请求权限",
                java.util.Arrays.toString(request.getResources()),
                "允许", () -> request.grant(request.getResources()),
                "拒绝", () -> request.deny());
    }

    public boolean pickFile(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params) {
        fileCallback = cb;
        try {
            Intent i = params.createIntent();
            startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_FILE);
            return true;
        } catch (Exception e) {
            fileCallback = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_UNLOCK) {
            if (resultCode == RESULT_OK) showPasswords();
            else Widgets.toast(this, "没有验证通过，密码不显示");
            return;
        }
        if (requestCode == REQ_PWCSV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            doImportCsv(data.getData());
            return;
        }
        if (requestCode == REQ_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            installPickedApk(data.getData());
            return;
        }
        if (requestCode == REQ_INSTALL_PERM) {
            File f = new File(getCacheDir(), "update.apk");
            if (f.exists()) doInstall(f);
            return;
        }
        if (requestCode == REQ_THEME && resultCode == RESULT_OK && data != null && data.getData() != null) {
            doImportTheme(data.getData());
            return;
        }
        if (requestCode == REQ_FONT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            applyPickedFont(data.getData());
            return;
        }
        if (requestCode == REQ_RESTORE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            applyRestore(data.getData());
            return;
        }
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            Prefs.put("apk_tree", tree.toString());
            installFromTree();
            return;
        }
        if (requestCode == REQ_PICK_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            launchInstaller(data.getData(), "所选安装包");
            return;
        }
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK && data != null) {
            String text = data.getStringExtra("text");
            if (text == null || text.isEmpty()) return;
            if (text.startsWith("http")) {
                newTab(text, true);
            } else {
                ClipboardManager cm2 = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm2 != null) cm2.setPrimaryClip(ClipData.newPlainText("qr", text));
                Widgets.info(this, ui, "扫到内容（已复制）", text);
            }
            return;
        }
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        }
    }

    public void enterFullscreen(View view, WebChromeClient.CustomViewCallback cb) {
        customViewCallback = cb;
        fullscreenHost.removeAllViews();
        fullscreenHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenHost.setVisibility(View.VISIBLE);
        if (topBar != null) topBar.setVisibility(View.GONE);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 全屏时右上角留一个「退出」按钮，避免出不去
        if (fsExit == null) {
            fsExit = new TextView(this);
            fsExit.setText("✕");
            fsExit.setTextSize(16);
            fsExit.setGravity(Gravity.CENTER);
            fsExit.setTextColor(0xCCFFFFFF);
            fsExit.setBackground(Theme.rounded(0x66000000, Theme.dp(this, 18)));
            fsExit.setOnClickListener(v -> exitFullscreen());
        }
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                Theme.dp(this, 36), Theme.dp(this, 36));
        elp.gravity = Gravity.TOP | Gravity.END;
        elp.topMargin = Theme.dp(this, 12);
        elp.rightMargin = Theme.dp(this, 12);
        if (fsExit.getParent() != null) ((ViewGroup) fsExit.getParent()).removeView(fsExit);
        fullscreenHost.addView(fsExit, elp);
        // 3 秒后自动淡出，点一下屏幕再出现
        fsExit.setAlpha(1f);
        fsExit.animate().alpha(0.15f).setStartDelay(3000).setDuration(400).start();
    }

    public void exitFullscreen() {
        if (fsExit != null && fsExit.getParent() != null) {
            ((ViewGroup) fsExit.getParent()).removeView(fsExit);
        }
        fullscreenHost.removeAllViews();
        fullscreenHost.setVisibility(View.GONE);
        if (topBar != null && ui.showTopBar) topBar.setVisibility(View.VISIBLE);
        if (bottomBar != null && ui.showBottomBar) bottomBar.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    public void handleVaneUri(Uri u) {
        String host = u.getHost() == null ? "" : u.getHost();
        List<String> seg = u.getPathSegments();
        if ("action".equals(host)) {
            if (!seg.isEmpty()) dispatch(seg.get(0));
        } else if ("search".equals(host)) {
            String q = u.getQueryParameter("q");
            String eng = u.getQueryParameter("engine");
            if (eng != null && !eng.isEmpty()) Prefs.put("engine", eng);
            openUrlOrSearch(q);
        } else if ("clear".equals(host)) {
            String t = u.getQueryParameter("t");
            Prefs.putArray("b".equals(t) ? "bookmarks" : "history", new JSONArray());
            showLibraryPage("b".equals(t) ? 0 : 1);
        } else if ("offline".equals(host)) {
            String path = u.getQueryParameter("path");
            BrowserTab tb2 = currentTab();
            if (tb2 != null && path != null) {
                tb2.web.getSettings().setAllowFileAccess(true);
                tb2.web.loadUrl("file://" + path);
            }
        } else if ("dl".equals(host)) {
            String op = u.getQueryParameter("op");
            android.app.DownloadManager dm = (android.app.DownloadManager)
                    getSystemService(Context.DOWNLOAD_SERVICE);
            if ("del".equals(op)) {
                try { dm.remove(Long.parseLong(u.getQueryParameter("id"))); } catch (Exception ignored) {}
                showDownloadsPage();
            } else if ("clear".equals(op)) {
                try {
                    android.database.Cursor c = dm.query(new android.app.DownloadManager.Query());
                    if (c != null) {
                        int iId = c.getColumnIndex(android.app.DownloadManager.COLUMN_ID);
                        while (c.moveToNext()) dm.remove(c.getLong(iId));
                        c.close();
                    }
                } catch (Exception ignored) {}
                showDownloadsPage();
            } else if ("open".equals(op)) {
                openDownloadedFile(u.getQueryParameter("n"));
            } else if ("dir".equals(op)) {
                try { startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)); } catch (Exception ignored) {}
            } else {
                showDownloadsPage();
            }
        } else if ("lib".equals(host)) {
            String tab = u.getQueryParameter("tab");
            int idx = 0;
            try {
                idx = Integer.parseInt(tab == null ? "0" : tab);
            } catch (Exception ignored) {}
            showLibraryPage(Math.max(0, Math.min(2, idx)));   // 0 书签 1 历史 2 离线
        } else if ("del".equals(host)) {
            String t = u.getQueryParameter("t");
            String url = u.getQueryParameter("url");
            JSONArray arr = Prefs.getArray("b".equals(t) ? "bookmarks" : "history");
            JSONArray fresh = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !url.equals(o.optString("url", ""))) fresh.put(o);
            }
            Prefs.putArray("b".equals(t) ? "bookmarks" : "history", fresh);
            showLibraryPage("b".equals(t) ? 0 : 1);
        } else if ("open".equals(host)) {
            String url = u.getQueryParameter("url");
            if (url != null) newTab(url, true);
        }
    }

    /** 各站点常见的「跳转 App」scheme：这些一律不弹报错。 */
    private static final String[] APP_SCHEMES = {
            "baiduboxapp", "baiduyun", "weixin", "wechat", "alipay", "alipays", "taobao", "tmall",
            "openapp.jdmobile", "snssdk", "zhihu", "bilibili", "sinaweibo", "mqq", "mqqapi", "qq",
            "pinduoduo", "xhsdiscover", "kwai", "douyin", "ctrip", "meituan", "dianping", "amap",
            "openapp", "market", "tmast", "taobaooauth", "youku", "iqiyi", "qqmusic", "kuaishou"
    };

    public void openExternal(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();

        // intent:// 里通常带 browser_fallback_url，直接取来在浏览器里打开
        if (scheme.equals("intent")) {
            String fallback = intentFallback(u.toString());
            if (fallback != null) {
                openUrlOrSearch(fallback);
            } else {
                Widgets.toast(this, "已忽略应用跳转");
            }
            return;
        }

        for (String s : APP_SCHEMES) {
            if (scheme.contains(s)) {
                // 有些链接把真实网页塞在参数里，能取到就原地打开
                String inner = firstHttpParam(u);
                if (inner != null) {
                    openUrlOrSearch(inner);
                    return;
                }
                String mode = Prefs.get("app_link", "notify");
                if ("open".equals(mode)) {
                    tryStart(u);
                } else if ("ask".equals(mode)) {
                    Widgets.confirm(this, ui, "这个链接想打开别的 App",
                            scheme + "://\n\n可以在浏览器里继续看，也可以交给系统的应用。", () -> tryStart(u));
                }
                // ignore：什么都不做，静默忽略
                return;
            }
        }

        // tel: / mailto: / sms: / geo: 这类正常交给系统
        tryStart(u);
    }

    private void tryStart(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            showUnhandledScheme(u);
        }
    }

    /** 没有应用能处理这个 scheme：给几个可操作的选择，而不是一句“没应用”。 */
    private void showUnhandledScheme(final Uri u) {
        final String scheme = u.getScheme() == null ? "" : u.getScheme();
        java.util.List<String> ops = new java.util.ArrayList<>();
        ops.add("复制链接");
        ops.add("在应用内搜索这个链接");
        ops.add("忽略");

        Widgets.pick(this, ui, "没有应用能打开 " + scheme + "://", ops, i -> {
            if (i == 0) {
                copyText(u.toString());
            } else if (i == 1) {
                openUrlOrSearch(Uri.encode(u.toString()));
            }
        });
    }

    /** 从 intent://...#Intent;...S.browser_fallback_url=xxx;end 里取回退网址。 */
    private static String intentFallback(String url) {
        try {
            int i = url.indexOf("S.browser_fallback_url=");
            if (i < 0) return null;
            String rest = url.substring(i + "S.browser_fallback_url=".length());
            int end = rest.indexOf(';');
            if (end > 0) rest = rest.substring(0, end);
            String v = java.net.URLDecoder.decode(rest, "UTF-8");
            return v.startsWith("http") ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 链接参数里有没有夹带真实网址（百度/淘宝这类跳转经常这么干）。 */
    private static String firstHttpParam(Uri u) {
        String[] keys = {"url", "target", "u", "src", "redirect", "link", "h5url", "fallback"};
        for (String k : keys) {
            try {
                String v = u.getQueryParameter(k);
                if (v != null) {
                    v = java.net.URLDecoder.decode(v, "UTF-8");
                    if (v.startsWith("http")) return v;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void showKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null && address != null) im.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null && address != null) im.hideSoftInputFromWindow(address.getWindowToken(), 0);
    }

    // ================= 生命周期 =================

    /** singleTop 下从别的界面带 extra 回来时不会再走 onCreate，这里补一份。 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        if (intent.getStringExtra("open_url") != null) {
            String u = intent.getStringExtra("open_url");
            if (intent.getBooleanExtra("private_url", false)) privateUrl = u;
            intent.removeExtra("private_url");
            intent.removeExtra("open_url");
            openUrlOrSearch(u);
        }
        if (intent.getBooleanExtra("edit_cards", false)) {
            intent.removeExtra("edit_cards");
            root.postDelayed(this::editHomeCards, 400);
        }
        if (intent.getBooleanExtra("open_sync", false)) {
            intent.removeExtra("open_sync");
            root.postDelayed(this::showSync, 400);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (reopenProxyTools) {                 // 从代理页返回：回到工具箱的代理栏
            reopenProxyTools = false;
            root.postDelayed(() -> {
                try {
                    showToolsGroup("代理", new String[]{"proxy"}, true);
                } catch (Throwable ignored) {}
            }, 120);
        }
        final ProxyManager pmr = ProxyManager.get(this);
        if (!pmr.isRunning()) {
            // 端口没在监听时挂着代理 = 所有页面 ERR_CONNECTION_ABORTED，先摘掉
            com.vane.browser.proxy.WebViewProxyBridge.clear();
        }
        if (pmr.enabled && !pmr.isRunning() && !pmr.isStarting()) {
            new Thread(() -> {
                try {
                    pmr.start();
                } catch (Throwable ignored) {}
            }, "proxy-resume-start").start();
        }
        int rev = Prefs.getInt("ui_rev", 0);
        if (rev != uiRev) {
            uiRev = rev;
            ui = UiConfig.load();
            Theme.applyToWindow(this, ui);
            root.setBackgroundColor(Theme.bg(this, ui));
            rebuildChrome();
            for (BrowserTab tb : tabs) tb.applyDarkening();
        }
        if (engineIcon != null) showScanIcon(!addressEditing);
    }

    /** 渲染进程崩溃后的自救：丢掉这个 WebView，用同一地址重开一个标签。 */
    public void handleRendererGone(BrowserTab dead) {
        String url = dead.url == null ? "" : dead.url;
        int idx = tabs.indexOf(dead);
        try {
            tabHost.removeView(dead.web);
        } catch (Throwable ignored) {}
        try {
            dead.web.destroy();
        } catch (Throwable ignored) {}
        tabs.remove(dead);
        if (tabs.isEmpty()) {
            current = -1;
        } else {
            current = Math.max(0, Math.min(idx, tabs.size() - 1));
            selectTab(current);
        }
        App.logEvent(this, "renderer-gone",
                "url: " + url + "\ntabs: " + tabs.size() + "\nnote: WebView 渲染进程崩溃后已重建标签");
        newTab(url.isEmpty() ? ui.homePage : url, true);
        Widgets.toast(this, "页面进程崩溃，已自动重开");
    }


    // ================= Via 那套工具 =================

    /** 添加到桌面：生成一个直达主页的快捷方式。 */
    public void addToDesktop() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
                if (sm != null) {
                    android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, "vane-home")
                            .setShortLabel("Vane")
                            .setLongLabel("Vane · 主页")
                            .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
                            .setIntent(new Intent(this, MainActivity.class)
                                    .setAction(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_LAUNCHER))
                            .build();
                    sm.requestPinShortcut(info, null);
                    Widgets.toast(this, "已请求添加到桌面");
                    return;
                }
            }
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, MainActivity.class));
            i.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Vane");
            i.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            sendBroadcast(i);
            Widgets.toast(this, "已请求添加到桌面");
        } catch (Exception e) {
            Widgets.toast(this, "桌面不让加：请手动长按主屏幕添加");
        }
    }

    /** 朗读网页：取正文交给系统 TTS，面板上能停。 */
    public void speakPage() {
        final BrowserTab t = currentTab();
        if (t == null) return;
        Widgets.toast(this, "正在取正文…");
        t.web.evaluateJavascript(
                "(function(){try{return (document.body&&document.body.innerText||'').slice(0,3000);}catch(e){return '';}})()",
                value -> {
                    if (value == null) return;
                    String text = value;
                    try {
                        text = new org.json.JSONArray("[" + value + "]").optString(0);
                    } catch (Exception ignored) {}
                    text = text.trim();
                    if (text.isEmpty()) {
                        Widgets.toast(this, "这页没抓到正文");
                        return;
                    }
                    startSpeak(text);
                });
    }

    private void startSpeak(String text) {
        pendingSpeak = text;
        if (tts == null) {
            tts = new android.speech.tts.TextToSpeech(getApplicationContext(), status -> {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    ttsReady = true;
                    int r = tts.setLanguage(java.util.Locale.CHINA);
                    if (r == android.speech.tts.TextToSpeech.LANG_MISSING_DATA
                            || r == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(java.util.Locale.getDefault());
                    }
                    tts.setSpeechRate(Prefs.getInt("tts_rate", 100) / 100f);
                    if (pendingSpeak != null) {
                        tts.speak(pendingSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "vane");
                        runOnUiThread(this::showSpeakPanel);
                    }
                } else {
                    runOnUiThread(() -> Widgets.toast(this, "朗读不可用：系统没有语音引擎"));
                }
            });
            return;
        }
        if (ttsReady) {
            tts.setSpeechRate(Prefs.getInt("tts_rate", 100) / 100f);
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "vane");
            showSpeakPanel();
        }
    }

    /** 朗读小面板：慢一点 / 停止。 */
    private void showSpeakPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 18), 0, Theme.dp(this, 18), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        box.addView(Widgets.text(this, "正在朗读", 16, Theme.fg(this, ui)));

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Theme.dp(this, 14), 0, 0);

        row.addView(Widgets.ghostButton(this, ui, "慢一点", () -> {
            int rate = Prefs.getInt("tts_rate", 100) - 10;
            if (rate < 50) rate = 50;
            Prefs.putInt("tts_rate", rate);
            if (tts != null) {
                tts.setSpeechRate(rate / 100f);
                if (pendingSpeak != null) {
                    tts.speak(pendingSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "vane");
                }
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView stop = Widgets.button(this, ui, "停止", () -> {
            if (tts != null) tts.stop();
            if (holder[0] != null) holder[0].dismiss();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.setMargins(Theme.dp(this, 8), 0, 0, 0);
        row.addView(stop, slp);
        box.addView(row);

        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }


    /** 屏幕方向：横竖切换。 */
    public void stopSpeaking() {
        if (tts != null) {
            tts.stop();
            Widgets.toast(this, "已停止朗读");
        }
    }

    public void toggleOrientation() {
        int cur = getResources().getConfiguration().orientation;
        setRequestedOrientation(cur == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    /** 字体大小：按档位缩放网页字体。 */
    public void changeFontSize() {
        int zoom = Prefs.getInt("font_zoom", 100) + 10;
        if (zoom > 200) zoom = 60;
        applyFontZoom(zoom);
    }

    /** 查看网页源码。 */
    public void viewSource() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            Widgets.toast(this, "主页没有源码可看");
            return;
        }
        t.web.evaluateJavascript("(function(){try{return document.documentElement.outerHTML.slice(0,60000);}catch(e){return '';}})()",
                value -> {
                    String html = value;
                    try {
                        html = new org.json.JSONArray("[" + value + "]").optString(0);
                    } catch (Exception ignored) {}
                    if (html == null || html.isEmpty()) return;
                    String b64 = android.util.Base64.encodeToString(html.getBytes(), android.util.Base64.NO_WRAP);
                    newTab("data:text/html;charset=utf-8;base64," + b64, true);
                });
    }

    /** 翻译整页：交给在线翻译。 */
    public void translatePage() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        String src = Prefs.get("translate_source", "baidu");
        boolean googleOk = "google".equals(src) && googleReachable();
        String url;
        if ("google".equals(src) && googleOk) {
            url = "https://translate.google.com/translate?sl=auto&tl=zh-CN&u=" + Uri.encode(t.url);
        } else if ("google".equals(src)) {
            // 选的谷歌但打不通（国内直连）→ 自动用百度
            url = baiduTrans(t.url);
        } else if ("tencent".equals(src)) {
            url = "https://transmart.qq.com/zh-CN/index?url=" + Uri.encode(t.url);
        } else if ("bing".equals(src)) {
            url = "https://www.translatetheweb.com/?from=&to=zh-CHS&a=" + Uri.encode(t.url);
        } else {
            url = baiduTrans(t.url);
        }
        newTab(url, true);
    }

    private String baiduTrans(String pageUrl) {
        return "https://fanyi.baidu.com/transpage?query=" + Uri.encode(pageUrl)
                + "&from=auto&to=zh&source=url&ie=utf8&render=1";
    }

    /** 谷歌可达性（结果缓存 10 分钟，避免每次都测）。 */
    private boolean googleReachable() {
        long t0 = Prefs.getLong("google_ok_time", 0);
        long now = System.currentTimeMillis();
        if (now - t0 < 10 * 60 * 1000) {
            return Prefs.getBool("google_ok", false);
        }
        boolean ok = false;
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL("https://translate.google.com/").openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            c.setRequestMethod("HEAD");
            int code = c.getResponseCode();
            ok = code > 0 && code < 500;
        } catch (Exception ignored) {}
        Prefs.putBool("google_ok", ok);
        Prefs.putLong("google_ok_time", now);
        return ok;
    }

    /** 翻译源设置。 */
    public void pickTranslateSource() {
        List<String> names = java.util.Arrays.asList("百度翻译（直连）", "谷歌翻译（需代理）",
                "腾讯交互翻译", "微软翻译");
        Widgets.pick(this, ui, "翻译源", names, i -> {
            Prefs.put("translate_source",
                    i == 1 ? "google" : i == 2 ? "tencent" : i == 3 ? "bing" : "baidu");
        });
    }

    /** 无图模式开关。 */
    public void toggleImages() {
        boolean noImages = !Prefs.getBool("no_images", false);
        Prefs.putBool("no_images", noImages);
        for (BrowserTab tb : tabs) {
            tb.web.getSettings().setBlockNetworkImage(noImages);
            tb.web.reload();
        }
    }

    /** 把当前站点标记成广告（写入本地规则）。 */
    public void markAsAd() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            Widgets.info(this, ui, "标记广告", "先进一个网页，再点这一项，\n会把当前网站的域名加进拦截列表。");
            return;
        }
        String host = "";
        try {
            host = Uri.parse(t.url).getHost();
        } catch (Exception ignored) {}
        if (host == null || host.isEmpty()) {
            Widgets.info(this, ui, "标记广告", "这个页面没有域名，标记不了。");
            return;
        }
        final String h = host;
        // 写进自定义规则 + 落盘，并让拦截器立刻重载
        String cur = Prefs.get("ad_custom", "");
        if (!cur.contains(h)) {
            Prefs.put("ad_custom", cur.isEmpty() ? h : (cur + "\n" + h));
        }
        try {
            File dir = new File(getFilesDir(), "rules");
            if (!dir.exists()) dir.mkdirs();
            try (java.io.FileWriter w = new java.io.FileWriter(new File(dir, "hosts.txt"), true)) {
                w.write(h + System.lineSeparator());
            }
        } catch (Exception ignored) {}
        try {
            blocker.reload();
        } catch (Throwable ignored) {}
        t.web.reload();
        Widgets.choice(this, ui, "已标记为广告",
                h + "\n已加入拦截列表，页面正在刷新。",
                "撤销", () -> {
                    Prefs.put("ad_custom", Prefs.get("ad_custom", "").replace(h, "").trim());
                    try { blocker.reload(); } catch (Throwable ignored) {}
                    t.web.reload();
                }, "知道了", null);
    }


    /** 网络日志：最近拦截/请求的站点。 */
    public void showNetworkLog() {
        List<String> lines = blocker.recentLog();
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, lines.size() - 40);
        for (int i = from; i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
        Widgets.menu(this, ui, "网络日志（最近 40 条）",
                java.util.Collections.singletonList(sb.length() == 0 ? "还没有记录" : sb.toString()), i -> {});
    }

    /** 保存网页（存成 html 到下载目录）。 */
    public void savePage() {
        BrowserTab t = currentTab();
        if (t == null || t.isHome()) return;
        t.web.evaluateJavascript("(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()",
                value -> {
                    String html = value;
                    try {
                        html = new org.json.JSONArray("[" + value + "]").optString(0);
                    } catch (Exception ignored) {}
                    if (html == null || html.length() < 50) {
                        Widgets.toast(this, "这页抓不到内容");
                        return;
                    }
                    String host = "";
                    try { host = Uri.parse(t.url).getHost(); } catch (Exception ignored) {}
                    String name = (host == null || host.isEmpty() ? "page" : host)
                            + "-" + stamp() + ".html";
                    try {
                        File out = saveToApp(name, "text/html", html);
                        String where = out.getAbsolutePath();
                        Widgets.choice(this, ui, "已保存",
                                name + "\n\n" + where + "\n\n应用私有目录，文件管理器看不到；"
                                        + "想拿到外面就点「分享」。",
                                "分享", () -> shareFile(out), "知道了", null);
                    } catch (Exception e) {
                        Widgets.toast(this, "保存失败：" + e.getMessage());
                    }
                });
    }

    /** 扫码：调系统相机拍一张，用 ZXing 解码。 */
    public void scanQr() {
        if (isFinishing() || isDestroyed()) return;
        try {
            startActivityForResult(new Intent(this, ScannerActivity.class), REQ_SCAN);
        } catch (Exception e) {
            Widgets.toast(this, "打不开扫码界面：" + e.getMessage());
        }
    }

    private void handleScanResult(Intent data) {
        try {
            android.graphics.Bitmap bmp = (android.graphics.Bitmap) data.getExtras().get("data");
            if (bmp == null) {
                Widgets.toast(this, "没拿到照片");
                return;
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] pixels = new int[w * h];
            bmp.getPixels(pixels, 0, w, 0, 0, w, h);
            com.google.zxing.RGBLuminanceSource source =
                    new com.google.zxing.RGBLuminanceSource(w, h, pixels);
            com.google.zxing.BinaryBitmap bb = new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(source));
            com.google.zxing.Result r = new com.google.zxing.MultiFormatReader()
                    .decodeWithState(bb);
            String text = r.getText();
            if (text.startsWith("http")) {
                newTab(text, true);
            } else {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("qr", text));
                Widgets.menu(this, ui, "扫到内容（已复制）", java.util.Arrays.asList(text), i -> {});
            }
        } catch (Exception e) {
            Widgets.toast(this, "没识别出二维码");
        }
    }

    /** 离线保存：把整页 HTML 快照写到本地（WebView 无法渲染 .mht，所以存 html）。 */
    public void saveOffline() {
        final BrowserTab t = currentTab();
        if (t == null || t.isHome()) {
            Widgets.toast(this, "主页不用存");
            return;
        }
        final File dir = new File(getFilesDir(), "offline");
        if (!dir.exists()) dir.mkdirs();
        t.web.evaluateJavascript("(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()",
                value -> {
                    String html = value;
                    try {
                        html = new org.json.JSONArray("[" + value + "]").optString(0);
                    } catch (Exception ignored) {}
                    if (html == null || html.length() < 50) {
                        Widgets.toast(this, "这页抓不到内容");
                        return;
                    }
                    String host = "";
                    try {
                        host = Uri.parse(t.url).getHost();
                    } catch (Exception ignored) {}
                    String name = (host == null || host.isEmpty() ? "page" : host) + "-"
                            + new java.text.SimpleDateFormat("MMdd-HHmm", java.util.Locale.CHINA)
                            .format(new java.util.Date()) + ".html";
                    try {
                        File f = new File(dir, name);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                        fos.write(html.getBytes("UTF-8"));
                        fos.close();
                        Widgets.info(this, ui, "已离线保存",
                                name + "\n大小 " + (f.length() / 1024) + " KB\n断网也能看文字和结构，图片需要联网。");
                    } catch (Exception e) {
                        Widgets.toast(this, "保存失败：" + e.getMessage());
                    }
                });
    }

    /** 打开已保存的离线页面。 */
    /** 离线页面：和书签/历史同一个页面（第三个标签）。 */
    public void showOfflinePage() {
        showLibraryPage(2);
    }

    public void showOfflinePages() {
        final File dir = new File(getFilesDir(), "offline");
        File[] all = dir.listFiles();
        final java.util.List<File> files = new ArrayList<>();
        if (all != null) {
            java.util.Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : all) if (f.isFile() && f.length() > 0) files.add(f);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
        TextView head = Widgets.text(this, "离线页面", 18, Theme.fg(this, ui));
        head.setPadding(0, 0, 0, Theme.dp(this, 10));
        box.addView(head);
        final android.app.Dialog[] holder = new android.app.Dialog[1];

        if (files.isEmpty()) {
            TextView empty = Widgets.text(this, "还没有内容", 14, Theme.hint(this, ui));
            empty.setPadding(0, Theme.dp(this, 20), 0, Theme.dp(this, 20));
            box.addView(empty);
        } else {
            for (final File f : files) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Theme.dp(this, 12), 0, Theme.dp(this, 12));
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageResource(R.drawable.ic_book);
                iv.setColorFilter(Theme.fg(this, ui));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        Theme.dp(this, 20), Theme.dp(this, 20));
                ilp.setMargins(0, 0, Theme.dp(this, 12), 0);
                row.addView(iv, ilp);
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.addView(Widgets.text(this, trim(f.getName(), 30), 14, Theme.fg(this, ui)));
                String when = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                        .format(new java.util.Date(f.lastModified())) + " · " + (f.length() / 1024) + " KB";
                TextView sub = Widgets.text(this, when, 11, Theme.hint(this, ui));
                sub.setPadding(0, Theme.dp(this, 2), 0, 0);
                texts.addView(sub);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    BrowserTab tb = currentTab();
                    if (tb == null) return;
                    tb.web.getSettings().setAllowFileAccess(true);
                    tb.internalLabel = "离线页面";
                    tb.web.loadUrl("file://" + f.getAbsolutePath());
                });
                row.setOnLongClickListener(v -> {
                    Widgets.confirm(this, ui, "删掉这个离线页？", f.getName(), () -> {
                        f.delete();
                        if (holder[0] != null) holder[0].dismiss();
                    });
                    return true;
                });
                box.addView(row);
            }
        }
        holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
    }

    /**
     * AI：接口、密钥、模型都填你自己的，浏览器里直接用。
     * 支持两类：OpenAI 兼容（/chat/completions，覆盖大多数服务商与本地模型）与 Anthropic 原生（/messages）。
     */
    public void openAi() {
        final String endpoint = Prefs.get("ai_endpoint", "");
        if (!endpoint.isEmpty()) {
            Widgets.pick(this, ui, "AI · " + Prefs.get("ai_model", ""), java.util.Arrays.asList(
                    "总结这一页", "就这一页提问…", "换接口 / 密钥"), k -> {
                if (k == 2) {
                    Prefs.put("ai_endpoint", "");
                    openAi();
                    return;
                }
                final BrowserTab tb = currentTab();
                if (tb == null || tb.isHome()) {
                    Widgets.toast(this, "先进一个网页再用 AI");
                    return;
                }
                if (k == 0) {
                    runAi(tb, "用简洁中文列出这一页的要点，最多 6 条。", "AI 摘要");
                    return;
                }
                Widgets.input(this, ui, "问这一页", "", false, q -> {
                    if (q == null || q.trim().isEmpty()) return;
                    runAi(tb, q.trim(), "AI 回答");
                });
            });
            return;
        }
        if (endpoint.isEmpty()) {
            Widgets.input(this, ui, "AI 接口地址（如 https://api.openai.com/v1）", "", false, ep -> {
                Prefs.put("ai_endpoint", ep.trim());
                Widgets.input(this, ui, "AI 密钥", "", false, key -> {
                    Prefs.put("ai_key", key.trim());
                    Widgets.input(this, ui, "模型名（如 gpt-4o-mini）", "gpt-4o-mini", false, model -> {
                        Prefs.put("ai_model", model.trim());
                        Widgets.toast(this, "配置好了，再点一次 AI 就会总结当前页");
                    });
                });
            });
            return;
        }
    }

    /** 抓当前页正文，带着问题去问模型。 */
    private void runAi(final BrowserTab t, final String question, final String title) {
        Widgets.toast(this, "正在问模型…");
        t.web.evaluateJavascript(
                "(function(){try{return (document.body&&document.body.innerText||'').slice(0,6000);}catch(e){return '';}})()",
                value -> {
                    String text = value;
                    try {
                        text = new org.json.JSONArray("[" + value + "]").optString(0);
                    } catch (Exception ignored) {}
                    if (text == null || text.trim().isEmpty()) {
                        Widgets.toast(this, "这页没抓到文字");
                        return;
                    }
                    final String page = text;
                    new Thread(() -> aiRequest(page, question, title), "ai-call").start();
                });
    }

    private void aiRequest(String pageText, String question, String title) {
        String endpoint = Prefs.get("ai_endpoint", "");
        String key = Prefs.get("ai_key", "");
        String model = Prefs.get("ai_model", "gpt-4o-mini");
        String base = endpoint.replaceAll("/+$", "");
        boolean anthropic = base.contains("anthropic") || key.startsWith("sk-ant-");
        String result;
        try {
            String url = anthropic ? base + "/messages" : base + "/chat/completions";
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(10000);
            c.setReadTimeout(45000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            if (anthropic) {
                c.setRequestProperty("x-api-key", key);
                c.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                c.setRequestProperty("Authorization", "Bearer " + key);
            }
            String user = "网页正文：\n" + pageText + "\n\n问题：" + question;
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("model", model);
            if (anthropic) {
                body.put("max_tokens", 1024);
                body.put("system", "你是网页阅读助手，回答简洁，必要时分条。");
                org.json.JSONArray msgs = new org.json.JSONArray();
                msgs.put(new org.json.JSONObject().put("role", "user").put("content", user));
                body.put("messages", msgs);
            } else {
                org.json.JSONArray msgs = new org.json.JSONArray();
                msgs.put(new org.json.JSONObject().put("role", "system")
                        .put("content", "你是网页阅读助手，回答简洁，必要时分条。"));
                msgs.put(new org.json.JSONObject().put("role", "user").put("content", user));
                body.put("messages", msgs);
            }
            c.getOutputStream().write(body.toString().getBytes("UTF-8"));
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            org.json.JSONObject resp = new org.json.JSONObject(b.toString("UTF-8"));
            if (anthropic) {
                org.json.JSONArray arr = resp.optJSONArray("content");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    org.json.JSONObject seg = arr.optJSONObject(i);
                    if (seg != null && "text".equals(seg.optString("type"))) sb.append(seg.optString("text"));
                }
                result = sb.toString();
            } else {
                result = resp.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
            }
            if (result == null || result.trim().isEmpty()) result = "（模型没有返回内容）";
        } catch (Exception e) {
            result = "请求失败：" + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        final String out = result;
        runOnUiThread(() -> {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Theme.dp(this, 18), 0, Theme.dp(this, 18), Theme.dp(this, 16));
            box.addView(com.vane.browser.ui.Sheet.handle(this, ui));
            TextView hdr = Widgets.text(this, title == null ? "AI 摘要" : title, 16, Theme.fg(this, ui));
            hdr.setPadding(0, 0, 0, Theme.dp(this, 10));
            box.addView(hdr);
            TextView body2 = Widgets.text(this, out, 13, Theme.fg(this, ui));
            body2.setTextIsSelectable(true);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(body2);
            box.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 320)));
            final android.app.Dialog[] holder = new android.app.Dialog[1];
            TextView ok = Widgets.button(this, ui, "好", () -> {
                if (holder[0] != null) holder[0].dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Theme.dp(this, 14);
            box.addView(ok, lp);
            holder[0] = com.vane.browser.ui.Sheet.show(this, ui, box, true);
        });
    }

    /** 兼容性自检：换到别的手机时，这几项决定能不能用。 */
    public void compatCheck() {
        StringBuilder sb = new StringBuilder();
        sb.append("系统 Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("ABI ").append(android.os.Build.SUPPORTED_ABIS[0]).append('\n');
        try {
            String ua = android.webkit.WebSettings.getDefaultUserAgent(this);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Chrome/([0-9.]+)").matcher(ua);
            sb.append("WebView ").append(m.find() ? m.group(1) : "未知").append('\n');
        } catch (Throwable ignored) {}
        sb.append("网页走代理支持：").append(
                com.vane.browser.proxy.WebViewProxyBridge.supported() ? "支持" : "不支持（会退化为整机 VPN 模式）").append('\n');
        sb.append("可执行内核：").append(
                com.vane.browser.proxy.ProxyManager.get(this).core().available() ? "正常" : "缺失").append('\n');
        sb.append("相机扫码：").append(
                getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
                        ? "可用" : "无相机").append('\n');
        sb.append("语音朗读：").append(
                getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)
                        ? "系统 TTS" : "无麦克风（朗读仍可用）").append('\n');
        Widgets.info(this, ui, "兼容性自检", sb.toString());
    }

    /** 反馈：一键把问题信息准备好，发邮件或复制。 */
    public void openFeedback() {
        final String report = buildReport();
        String feedbackUrl = Prefs.get("feedback_url", "");
        List<String> ops = new ArrayList<>();
        ops.add("复制问题信息");
        ops.add("用邮件发给我");
        if (!feedbackUrl.isEmpty()) ops.add("打开反馈页面");
        ops.add("只看/复制当前网址");

        Widgets.pick(this, ui, "反馈 / 举报", ops, i -> {
            String label = ops.get(i);
            if (label.startsWith("复制问题信息")) {
                copyText(report);
                Widgets.toast(this, "已复制，粘到聊天里就能发我");
            } else if (label.startsWith("用邮件")) {
                try {
                    Intent m = new Intent(Intent.ACTION_SENDTO, Uri.parse(
                            "mailto:" + Prefs.get("feedback_mail", "") + "?subject="
                                    + Uri.encode("Vane问题反馈") + "&body=" + Uri.encode(report)));
                    startActivity(m);
                } catch (Exception e) {
                    copyText(report);
                    Widgets.toast(this, "没有邮件应用，已复制");
                }
            } else if (label.startsWith("打开反馈页面")) {
                newTab(feedbackUrl, true);
            } else {
                BrowserTab t = currentTab();
                copyText(t == null ? "" : t.url);
                Widgets.toast(this, "已复制网址");
            }
        });
    }

    private void copyText(String text) {
        ClipboardManager cmx = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cmx != null) cmx.setPrimaryClip(ClipData.newPlainText("vane", text));
    }

    /** 版本、设备、代理状态、当前网址——够我定位问题了。 */
    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vane ").append(appVersionName()).append('\n');
        sb.append("设备 ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append(" · Android ")
                .append(android.os.Build.VERSION.RELEASE).append('\n');
        sb.append("WebView ").append(android.webkit.WebSettings.getDefaultUserAgent(this)
                .replaceAll(".*Chrome/([0-9.]+).*", "$1")).append('\n');
        try {
            sb.append("代理 ").append(ProxyManager.get(this).isRunning() ? "已连接" : "未连接").append('\n');
        } catch (Throwable ignored) {}
        BrowserTab t = currentTab();
        sb.append("网址 ").append(t == null ? "" : t.url).append('\n');
        sb.append("时间 ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.CHINA).format(new java.util.Date())).append('\n');
        return sb.toString();
    }

    /** 举报网站：给出可复制的信息。 */
    public void reportSite() {
        BrowserTab t = currentTab();
        String url = t == null ? "" : t.url;
        Widgets.menu(this, ui, "举报/反馈", java.util.Arrays.asList(
                "复制页面地址", "打开 Via 的反馈页（示例）"), i -> {
            if (i == 0) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Widgets.toast(this, "已复制：" + url);
            }
        });
    }

    private android.speech.tts.TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeak;


    /** 退回系统选择器：只选一个 apk，不浏览目录。 */
    private void pickApkManually() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_PICK_APK);
        } catch (Exception e) {
            Widgets.toast(this, "打不开选择器：" + e.getMessage());
        }
    }

    /** 应用内安装：从「下载」里找出最新的 APK 直接交给系统安装器，不用开文件管理器。 */
    /** 从任意位置选一个 APK 安装（不依赖媒体库索引，也不进文件管理器）。 */
    public void pickApkToInstall() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/vnd.android.package-archive");
            startActivityForResult(i, REQ_APK);
        } catch (Exception e) {
            try {
                Intent i2 = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i2.addCategory(Intent.CATEGORY_OPENABLE);
                i2.setType("*/*");
                startActivityForResult(i2, REQ_APK);
            } catch (Exception e2) {
                Widgets.toast(this, "打不开选择器：" + e2.getMessage());
            }
        }
    }

    private void installPickedApk(android.net.Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            final File out = new File(getCacheDir(), "update.apk");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            in.close();

            // 没给「安装未知应用」权限时先带用户去开
            if (android.os.Build.VERSION.SDK_INT >= 26
                    && !getPackageManager().canRequestPackageInstalls()) {
                Widgets.choice(this, ui, "需要「安装未知应用」权限",
                        "允许 Vane 安装应用后就能一键装包。", "去开启", () -> {
                            try {
                                startActivityForResult(new Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName())), REQ_INSTALL_PERM);
                            } catch (Exception ignored) {}
                        }, "取消", null);
                return;
            }
            doInstall(out);
        } catch (Exception e) {
            Widgets.toast(this, "读取失败：" + e.getMessage());
        }
    }

    private void doInstall(File apk) {
        try {
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(u, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Widgets.toast(this, "调不起安装程序：" + e.getMessage());
        }
    }

    public void installUpdate() {
        // 0) 已经授权过目录就直接列文件
        if (!Prefs.get("apk_tree", "").isEmpty()) {
            installFromTree();
            return;
        }

        // 1) 先看应用自己的目录：外面拷进来就能装，不需要任何权限
        try {
            File dir = getExternalFilesDir("apk");
            File[] files = dir == null ? null : dir.listFiles();
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                List<String> names = new ArrayList<>();
                for (File f : files) {
                    if (f.getName().endsWith(".apk")) names.add(f.getName());
                }
                if (!names.isEmpty()) {
                    final File[] sorted = files;
                    Widgets.pick(this, ui, "选择要安装的包", names, idx -> {
                        String want = names.get(idx);
                        for (File f : sorted) {
                            if (f.getName().equals(want)) {
                                try {
                                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                            this, getPackageName() + ".fileprovider", f);
                                    launchInstaller(uri, f.getName());
                                } catch (Exception e) {
                                    Widgets.toast(this, "打开失败：" + e.getMessage());
                                }
                                return;
                            }
                        }
                    });
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            java.util.List<String[]> found = new ArrayList<>();   // {显示名, content uri}
            android.database.Cursor c = getContentResolver().query(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{
                            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.provider.MediaStore.MediaColumns.DATE_ADDED,
                            android.provider.MediaStore.MediaColumns._ID},
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%.apk"},
                    android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (c != null) {
                while (c.moveToNext() && found.size() < 30) {
                    String name = c.getString(0);
                    String id = c.getString(3);
                    if (name == null || id == null) continue;
                    android.net.Uri uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            Long.parseLong(id));
                    found.add(new String[]{name, uri.toString()});
                }
                c.close();
            }
            if (found.isEmpty()) {
                App.logEvent(this, "install-scan-empty", "MediaStore 没有索引到 apk");
                requestDownloadFolder();
                return;
            }
            List<String> names = new ArrayList<>();
            for (String[] f : found) names.add(f[0]);
            Widgets.pick(this, ui, "选择要安装的包", names, idx -> {
                String[] f = found.get(idx);
                launchInstaller(android.net.Uri.parse(f[1]), f[0]);
            });
        } catch (Exception e) {
            Widgets.toast(this, "读取失败：" + e.getMessage());
        }
    }

    /** 一次性授权「下载/Vane」目录后，以后每次更新都不用再找文件。 */
    private void requestDownloadFolder() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            Widgets.toast(this, "打不开目录授权：" + e.getMessage());
        }
    }

    private void installFromTree() {
        String saved = Prefs.get("apk_tree", "");
        if (saved.isEmpty()) {
            requestDownloadFolder();
            return;
        }
        try {
            android.net.Uri tree = android.net.Uri.parse(saved);
            String id = android.provider.DocumentsContract.getTreeDocumentId(tree);
            android.net.Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
            final List<String> names = new ArrayList<>();
            final List<String> uris = new ArrayList<>();
            android.database.Cursor c = getContentResolver().query(children,
                    new String[]{
                            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                    null, null,
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String n = c.getString(0);
                    String docId = c.getString(1);
                    if (n == null || !n.endsWith(".apk")) continue;
                    names.add(n);
                    uris.add(android.provider.DocumentsContract
                            .buildDocumentUriUsingTree(tree, docId).toString());
                }
                c.close();
            }
            if (names.isEmpty()) {
                Widgets.toast(this, "这个目录里没有安装包");
                return;
            }
            Widgets.pick(this, ui, "选择要安装的包", names, idx ->
                    launchInstaller(android.net.Uri.parse(uris.get(idx)), names.get(idx)));
        } catch (Exception e) {
            Widgets.toast(this, "读取失败：" + e.getMessage());
            requestDownloadFolder();
        }
    }

    private void launchInstaller(android.net.Uri uri, String name) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Widgets.toast(this, "正在打开安装器：" + name);
        } catch (Exception e) {
            Widgets.toast(this, "安装器打不开：" + e.getMessage());
        }
    }



    // ================= 应用 / 内核 自动更新 =================

    /** 更新清单（JSON）：versionCode / versionName / kernel / url / notes。 */
    private static final String DEFAULT_MANIFEST = "";

    public void checkUpdate(boolean byUser) {
        final String url = Prefs.get("update_manifest", DEFAULT_MANIFEST);
        if (url.isEmpty()) {
            if (byUser) {
                Widgets.input(this, ui, "更新地址（一个 JSON 文件的网址）",
                        "", true, v -> {
                            Prefs.put("update_manifest", v.trim());
                            Widgets.toast(this, "已保存，再点一次「检查更新」");
                        });
            }
            return;
        }
        if (byUser) Widgets.toast(this, "正在检查…");
        new Thread(() -> {
            String err = null;
            org.json.JSONObject j = null;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", "Vane/" + appVersionName());
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                j = new org.json.JSONObject(b.toString("UTF-8"));
            } catch (Exception e) {
                err = e.getMessage();
            }
            final org.json.JSONObject info = j;
            final String error = err;
            runOnUiThread(() -> {
                if (info == null) {
                    if (byUser) Widgets.info(this, ui, "检查更新", "没拿到更新信息：\n" + error);
                    return;
                }
                int newCode = info.optInt("versionCode", 0);
                int curCode = currentVersionCode();
                if (newCode <= curCode) {
                    if (byUser) Widgets.info(this, ui, "已经是最新",
                            "当前版本 " + appVersionName() + "\n已经是最新的了");
                    return;
                }
                String notes = info.optString("notes", "");
                String kernel = info.optString("kernel", "");
                String apkUrl = info.optString("url", "");
                String msg = "新版本 " + info.optString("versionName", "") + "\n"
                        + (kernel.isEmpty() ? "" : "组件 " + kernel + "\n")
                        + (notes.isEmpty() ? "" : notes + "\n")
                        + "\n下载后会自动打开安装器。";
                Widgets.choice(this, ui, "发现新版本", msg,
                        "下载并安装", () -> downloadAndInstall(apkUrl),
                        "稍后", null);
            });
        }, "update-check").start();
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int currentVersionCode() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 用系统下载器下载新包，下完直接调安装器。 */
    private void downloadAndInstall(final String url) {
        if (url == null || url.isEmpty()) {
            Widgets.toast(this, "这个版本没给下载地址");
            return;
        }
        try {
            android.app.DownloadManager.Request r =
                    new android.app.DownloadManager.Request(Uri.parse(url));
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                    "vane-update-" + System.currentTimeMillis() + ".apk");
            final android.app.DownloadManager dm =
                    (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;
            final long id = dm.enqueue(r);
            Widgets.toast(this, "开始下载，完成后会自动弹安装器");

            new Thread(() -> {
                for (int i = 0; i < 240; i++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                    android.database.Cursor c = dm.query(
                            new android.app.DownloadManager.Query().setFilterById(id));
                    if (c == null) continue;
                    boolean done = false, failed = false;
                    if (c.moveToFirst()) {
                        int st = c.getInt(c.getColumnIndexOrThrow(
                                android.app.DownloadManager.COLUMN_STATUS));
                        done = st == android.app.DownloadManager.STATUS_SUCCESSFUL;
                        failed = st == android.app.DownloadManager.STATUS_FAILED;
                    }
                    c.close();
                    if (failed) {
                        runOnUiThread(() -> Widgets.toast(this, "下载失败，检查一下网络或地址"));
                        return;
                    }
                    if (done) {
                        android.net.Uri uri = dm.getUriForDownloadedFile(id);
                        runOnUiThread(() -> launchInstaller(uri, "vane-update.apk"));
                        return;
                    }
                }
            }, "update-download").start();
        } catch (Exception e) {
            Widgets.toast(this, "下载启动失败：" + e.getMessage());
        }
    }


    // ================= 手势（边缘滑动）=================

    private float downX = 0, downY = 0;
    private long downTime = 0;
    private boolean gestureClaimed = false;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                downTime = System.currentTimeMillis();
                gestureClaimed = false;
                break;
            case android.view.MotionEvent.ACTION_UP:
                float dx = ev.getX() - downX;
                float dy = Math.abs(ev.getY() - downY);
                long dt = System.currentTimeMillis() - downTime;
                float edge = Theme.dp(this, 28);
                float trigger = Theme.dp(this, 70);
                if (!gestureClaimed && dy < Theme.dp(this, 60) && dt < 600) {
                    if (downX < edge && dx > trigger) {
                        String act = ui.gestures.get("edge_left_swipe");
                        if (act != null && !act.isEmpty()) {
                            gestureClaimed = true;
                            dispatch(act);
                        }
                    } else if (downX > getWindow().getDecorView().getWidth() - edge && dx < -trigger) {
                        String act = ui.gestures.get("edge_right_swipe");
                        if (act != null && !act.isEmpty()) {
                            gestureClaimed = true;
                            dispatch(act);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private long gameModeBack = 0;

    /** 游戏模式：两秒内连按两次才真的退出。 */
    private boolean handleGameModeBack() {
        if (!Prefs.getBool("game_mode", false)) return false;
        long now = System.currentTimeMillis();
        if (now - gameModeBack < 2000) {
            gameModeBack = 0;
            return false;                  // 第二次：放行，正常返回
        }
        gameModeBack = now;
        Widgets.toast(this, "再按一次返回键退出游戏模式");
        return true;
    }

    /** 外接键盘快捷键（对应 Via 的「键盘快捷键」）。 */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        try {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && (e.isCtrlPressed() || e.isAltPressed() || e.getKeyCode() == android.view.KeyEvent.KEYCODE_F5)) {
                int code = e.getKeyCode();
                boolean ctrl = e.isCtrlPressed();
                boolean shift = e.isShiftPressed();
                if (ctrl && code == android.view.KeyEvent.KEYCODE_T) { newTab(ui.homePage, true); return true; }
                if (ctrl && code == android.view.KeyEvent.KEYCODE_W) { closeCurrentTab(); return true; }
                if (ctrl && code == android.view.KeyEvent.KEYCODE_L) { focusAddress(); return true; }
                if (ctrl && code == android.view.KeyEvent.KEYCODE_R) { reload(); return true; }
                if (ctrl && code == android.view.KeyEvent.KEYCODE_D) { addBookmark(); return true; }
                if (ctrl && code == android.view.KeyEvent.KEYCODE_TAB) {
                    if (shift) switchTabRelative(-1); else switchTabRelative(1);
                    return true;
                }
                if (code == android.view.KeyEvent.KEYCODE_F5) { reload(); return true; }
                if (ctrl && code == android.view.KeyEvent.KEYCODE_PERIOD) { stopLoading(); return true; }
                if (e.isAltPressed() && code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) { goBack(); return true; }
                if (e.isAltPressed() && code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { goForward(); return true; }
            }
        } catch (Throwable ignored) {}
        return super.dispatchKeyEvent(e);
    }

    private void switchTabRelative(int delta) {
        if (tabs.size() < 2) return;
        int next = (current + delta + tabs.size()) % tabs.size();
        selectTab(next);
    }

    private long lastExitBack = 0;

    @Override
    public void onBackPressed() {
        if (handleGameModeBack()) return;

        // 1) 有打开的面板：先关面板
        try {
            if (com.vane.browser.ui.Sheet.current != null) {
                com.vane.browser.ui.Sheet.current.dismiss();
                com.vane.browser.ui.Sheet.current = null;
                return;
            }
        } catch (Throwable ignored) {}

        // 2) 建议列表展开中：先收起
        if (suggestBox != null) {
            hideSuggest();
            address.clearFocus();
            setAddressEditing(false);
            return;
        }

        // 3) 视频全屏：退出
        if (fullscreenHost != null && fullscreenHost.getVisibility() == View.VISIBLE) {
            exitFullscreen();
            return;
        }

        // 4) 全屏阅读：恢复工具栏
        if (!ui.showTopBar) {
            toggleFullscreenBars();
            return;
        }

        BrowserTab t = currentTab();

        // 5) 网页历史：后退
        if (t != null && t.web.canGoBack()) {
            t.web.goBack();
            return;
        }

        // 6) 内置页（书签/历史/下载/离线）：回到主页，而不是退出
        if (t != null && t.internalLabel != null && !t.internalLabel.isEmpty()) {
            t.internalLabel = null;
            loadTabUrl(t, ui.homePage);
            return;
        }

        // 7) 多标签：先关当前标签
        if (tabs.size() > 1) {
            closeCurrentTab();
            return;
        }

        // 8) 已经在主页：两秒内再按一次才退出（Chrome 的做法）
        if (t != null && t.isHome()) {
            long now = System.currentTimeMillis();
            if (now - lastExitBack < 2000) {
                super.onBackPressed();
                return;
            }
            lastExitBack = now;
            Widgets.toast(this, "再按一次返回键退出");
            return;
        }

        // 9) 其他情况：回主页
        if (t != null) {
            loadTabUrl(t, ui.homePage);
            return;
        }
        super.onBackPressed();
    }

    /** 让某个标签加载指定地址（内部用，不新建标签）。 */
    private void loadTabUrl(BrowserTab t, String url) {
        if (t == null) return;
        t.load(url);
        if (address != null) {
            address.setText(t.isHome() ? "" : url);
            address.clearFocus();
        }
        onTabStateChanged();
    }

    /** 保存当前标签，供下次启动恢复。 */
    private void saveSession() {
        if (Prefs.getBool("no_history", false)) return;
        try {
            JSONArray arr = new JSONArray();
            for (BrowserTab t : tabs) {
                if (t.url == null || t.url.isEmpty() || t.isHome()) continue;
                if (privateUrl != null && t.url.startsWith(privateUrl)) continue;   // 隐私空间的站不存进会话
                JSONObject o = new JSONObject();
                o.put("url", t.url);
                arr.put(o);
            }
            Prefs.putArray("session_tabs", arr);
        } catch (Exception ignored) {}
    }

    /** 启动时恢复上次的标签。 */
    private void restoreSession() {
        if (!Prefs.getBool("restore_session", true)) return;
        JSONArray arr = Prefs.getArray("session_tabs");
        if (arr.length() == 0) return;
        for (int i = 0; i < arr.length() && i < 12; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String u = o.optString("url", "");
            if (!u.startsWith("http")) continue;
            newTab(u, i == arr.length() - 1);
        }
    }

    /** 关掉标签后底部弹一条「撤销」。 */
    private void showUndoBar(final String url, String title) {
        if (undoBar != null) {
            root.removeView(undoBar);
            undoBar = null;
        }
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(this, 14), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));
        bar.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 16)));
        bar.addView(Widgets.text(this, "已关闭：" + trim(title == null ? url : title, 16), 13, Theme.fg(this, ui)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView undo = Widgets.text(this, "撤销", 13, Theme.accent(ui));
        undo.setPadding(Theme.dp(this, 12), Theme.dp(this, 6), Theme.dp(this, 12), Theme.dp(this, 6));
        undo.setOnClickListener(v -> {
            if (undoBar != null) root.removeView(undoBar);
            undoBar = null;
            newTab(url, true);
        });
        bar.addView(undo);

        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Theme.dp(this, 12);
        lp.rightMargin = Theme.dp(this, 12);
        lp.bottomMargin = Theme.dp(this, (ui.bottomBarHeight == 0 ? 46 : ui.bottomBarHeight) + 8);
        root.addView(bar, lp);
        undoBar = bar;
        bar.postDelayed(() -> {
            if (undoBar == bar) {
                root.removeView(bar);
                undoBar = null;
            }
        }, 4000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { saveSession(); } catch (Throwable ignored) {}
    }


    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {}
        }
        for (BrowserTab t : tabs) t.destroy();
        tabs.clear();
        super.onDestroy();
    }
}
