package com.vane.browser;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vane.browser.proxy.Node;
import com.vane.browser.proxy.ProxyManager;
import com.vane.browser.proxy.Subscription;
import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 订阅 + 节点 + 分流 + 代理开关，一个页面全搞定。 */
public class SubsActivity extends AppCompatActivity {

    private UiConfig ui;
    private LinearLayout list;
    private final Handler ui$ = new Handler(Looper.getMainLooper());

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

    private void build() {
        list.removeAllViews();
        final ProxyManager pm = ProxyManager.get(this);

        add(Widgets.section(this, ui, "代理"));

        // ---- 一行开关 ----
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        boolean running = pm.isRunning();
        boolean wanted = pm.enabled;
        String state = running ? "已连接" : (wanted ? "正在连接…" : "未连接");
        TextView st = Widgets.text(this, state, 16, running ? Theme.accent(ui) : Theme.fg(this, ui));
        texts.addView(st);
        String detail;
        if (running) {
            detail = (pm.node() == null ? "没有节点" : pm.node().name + " · " + pm.linkLine().replace("代理：", ""));
        } else if (wanted) {
            detail = "内核启动中，等十几秒；若一直这样点开关重试";
        } else {
            detail = "点右边开关即可开始";
        }
        TextView sub = Widgets.text(this, detail, 12, Theme.hint(this, ui));
        sub.setPadding(0, Theme.dp(this, 3), 0, 0);
        texts.addView(sub);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 关态时显示「连接中 / 开启」，开态时显示「已开启」
        final TextView sw = Widgets.pill(this, ui, running, "已开启", wanted ? "连接中" : "开启");
        sw.setOnClickListener(v -> {
            if (pm.isRunning()) toggle();    // 真在跑 → 关
            else toggle();                   // 没跑（哪怕记住的是开着）→ 重新开
        });
        card.addView(sw);
        add(card);
        if (pm.lastError != null && !pm.lastError.isEmpty()) {
            TextView err = Widgets.text(this, "上次失败：" + pm.lastError, 12, 0xFFE0A32E);
            err.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 12));
            add(err);
        }
        add(Widgets.divider(this, ui));

        // ---- 节点列表（点一下就切） ----
        add(Widgets.section(this, ui, "节点 · " + pm.nodes.size()));
        if (pm.nodes.isEmpty()) {
            add(Widgets.row(this, ui, "还没有节点", "先在下面粘一个订阅链接", null, null));
        }
        for (final Node n : new ArrayList<>(pm.nodes)) {
            boolean sel = n.name.equals(pm.selectedNode);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.dp(this, 16), Theme.dp(this, 12), Theme.dp(this, 16), Theme.dp(this, 12));
            TextView nm = Widgets.text(this, n.name, 15, sel ? Theme.accent(ui) : Theme.fg(this, ui));
            row.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Widgets.text(this, sel ? "✓" : "", 14, Theme.accent(ui)));
            row.setOnClickListener(v -> {
                pm.selectedNode = n.name;
                pm.save();
                if (pm.enabled) {
                    Widgets.toast(this, "切换到 " + n.name);
                    toggle();   // 关掉旧的
                    ui$.postDelayed(this::build, 300);
                } else {
                    Widgets.toast(this, "已选 " + n.name + "，打开上面的开关即可");
                    build();
                }
            });
            add(row);
            add(Widgets.divider(this, ui));
        }

        // ---- 添加订阅 ----
        add(Widgets.section(this, ui, "订阅"));
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        addRow.setPadding(Theme.dp(this, 16), Theme.dp(this, 8), Theme.dp(this, 16), Theme.dp(this, 8));
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("粘贴订阅链接");
        input.setTextSize(13);
        input.setTextColor(Theme.fg(this, ui));
        input.setHintTextColor(Theme.hint(this, ui));
        input.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 14)));
        input.setPadding(Theme.dp(this, 12), Theme.dp(this, 9), Theme.dp(this, 12), Theme.dp(this, 9));
        addRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ok = Widgets.ghostButton(this, ui, "添加", () -> {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) return;
            pm.addSubscription(url);
            input.setText("");
            Widgets.toast(this, "正在获取节点…");
            ui$.postDelayed(this::build, 2500);
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.setMargins(Theme.dp(this, 8), 0, 0, 0);
        addRow.addView(ok, olp);
        add(addRow);

        // ---- 高级（默认收起） ----
        boolean adv = Prefs.getBool("proxy_adv", false);
        add(Widgets.section(this, ui, adv ? "高级 ▴" : "高级 ▾"));
        if (!adv) {
            add(Widgets.row(this, ui, "展开高级设置", "分流模式、测速、自检、日志、删除订阅", null, () -> {
                Prefs.putBool("proxy_adv", true);
                build();
            }));
        } else {
            add(Widgets.row(this, ui, "收起高级设置", null, null, () -> {
                Prefs.putBool("proxy_adv", false);
                build();
            }));
            add(Widgets.divider(this, ui));
            add(Widgets.segment(this, ui, "分流模式", new String[]{"规则分流", "全局代理", "全局直连"},
                    modeIndex(pm.rules.mode()), i -> {
                        String[] vals = {"rule", "global", "direct"};
                        pm.rules.setMode(vals[i]);
                        if (pm.isRunning()) {
                            pm.stop();
                            ui$.postDelayed(this::toggle, 200);
                        }
                        build();
                    }));
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "测试节点连通性", "TCP 握手耗时", null, () -> {
                Widgets.toast(this, "开始测试…");
                pm.testAllAsync(() -> ui$.post(this::build));
            }));
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "代理自检（诊断）", "结果写到 下载/Vane/", null, this::diagnose));
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, pm.kernelInfo(), "内核", null, () -> Widgets.toast(this, pm.kernelInfo())));
            add(Widgets.divider(this, ui));
            add(Widgets.row(this, ui, "查看日志", "每条请求走了直连还是节点", null, () -> {
                StringBuilder sb = new StringBuilder();
                synchronized (pm.logs) {
                    for (int i = Math.max(0, pm.logs.size() - 30); i < pm.logs.size(); i++) sb.append(pm.logs.get(i)).append('\n');
                }
                Widgets.menu(this, ui, "日志", java.util.Collections.singletonList(
                        sb.length() == 0 ? "暂无日志" : sb.toString()), i -> {});
            }));
            add(Widgets.divider(this, ui));
            for (final Subscription sb1 : new ArrayList<>(pm.subs)) {
                add(Widgets.row(this, ui, "订阅：" + sb1.name + " · " + sb1.nodes.size() + " 节点",
                        sb1.url.length() > 46 ? sb1.url.substring(0, 46) + "…" : sb1.url, null, null));
                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                btns.setPadding(Theme.dp(this, 12), 0, Theme.dp(this, 12), Theme.dp(this, 10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, Theme.dp(this, 8), 0);
                btns.addView(Widgets.ghostButton(this, ui, "刷新", () -> {
                    pm.refreshSubscription(sb1, (ok2, msg) -> ui$.post(() -> {
                        Widgets.toast(this, msg);
                        build();
                    }));
                }), lp);
                btns.addView(Widgets.ghostButton(this, ui, "删除", () -> Widgets.confirm(this, ui, "删除订阅", "确定删除？", () -> {
                    pm.removeSubscription(sb1);
                    build();
                })), lp);
                add(btns);
                add(Widgets.divider(this, ui));
            }
        }

        View pad = new View(this);
        list.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 50)));
    }

    private int modeIndex(String mode) {
        if (com.vane.browser.proxy.RuleEngine.MODE_GLOBAL.equals(mode)) {
            return 1;
        }
        if (com.vane.browser.proxy.RuleEngine.MODE_DIRECT.equals(mode)) {
            return 2;
        }
        return 0;
    }

    private void pickMode() {
        List<String> names = new ArrayList<>();
        names.add("规则分流：国内直连 / 境外代理");
        names.add("全局代理");
        names.add("全局直连");
        String[] vals = {"rule", "global", "direct"};
        Widgets.pick(this, ui, "分流模式", names, i -> {
            ProxyManager.get(this).rules.setMode(vals[i]);
            if (ProxyManager.get(this).enabled) toggle();
            build();
        });
    }

    private void toggle() {
        final ProxyManager pm = ProxyManager.get(this);
        final boolean turningOn = !pm.isRunning();   // 以「实际是否在跑」为准
        Widgets.toast(this, turningOn ? "正在启动代理…" : "正在关闭代理…");
        new Thread(() -> {
            if (turningOn) pm.start();
            else pm.stop();
            final String msg = turningOn
                    ? (pm.enabled ? "代理已开启" : (pm.lastError.isEmpty() ? "启动失败" : pm.lastError))
                    : "代理已关闭";
            ui$.post(() -> {
                Widgets.toast(this, msg);
                build();
            });
        }, "subs-toggle").start();
    }

    private void addManual() {
        Widgets.input(this, ui, "节点链接", "", true, s -> {
            ProxyManager pm = ProxyManager.get(this);
            List<Node> parsed = com.vane.browser.proxy.SubscriptionParser.parse(s);
            if (parsed.isEmpty()) {
                Widgets.toast(this, "识别不了这条链接");
                return;
            }
            for (Node n : parsed) pm.addManualNode(n);
            Widgets.toast(this, "已添加 " + parsed.size() + " 个节点");
            build();
        });
    }

    /** 三项检查：内核是否在跑 / 能不能经本地端口出网 / WebView 是否支持代理覆盖。 */
    private void diagnose() {
        final ProxyManager pm = ProxyManager.get(this);
        Widgets.toast(this, "正在自检…");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("enabled=").append(pm.enabled)
              .append(" localPort=").append(pm.localPort)
              .append(" coreRunning=").append(pm.coreRunning())
              .append(" kernel=").append(pm.kernelInfo()).append('\n');
            sb.append("mode=").append(pm.rules.mode()).append('\n');
            Node n = pm.node();
            sb.append("node=").append(n == null ? "无" : (n.type + " " + n.name + " " + n.server + ":" + n.port)).append('\n');
            sb.append("\n[1] 经本地代理取 ip-api：\n")
              .append(pm.fetchViaLocalProxy("http://ip-api.com/json/?fields=query,country")).append('\n');
            sb.append("\n[2] 直连取 ip-api（对比）：\n")
              .append(ProxyManager.fetchDirect("http://ip-api.com/json/?fields=query,country")).append('\n');
            boolean supported = androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.PROXY_OVERRIDE);
            sb.append("\n[3] WebView PROXY_OVERRIDE 支持=").append(supported).append('\n');
            sb.append("webview=").append(android.webkit.WebView.getCurrentWebViewPackage() == null ? "?"
                    : android.webkit.WebView.getCurrentWebViewPackage().versionName).append('\n');
            App.logEvent(this, "proxy-diagnose", sb.toString());
            ui$.post(() -> {
                Widgets.toast(this, "自检完成，结果已写入下载目录");
                build();
            });
        }, "proxy-diagnose").start();
    }

    private void add(View v) {
        list.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
