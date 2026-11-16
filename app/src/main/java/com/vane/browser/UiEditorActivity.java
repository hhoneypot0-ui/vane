package com.vane.browser;

import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vane.browser.core.Actions;
import com.vane.browser.settings.Prefs;
import com.vane.browser.settings.UiConfig;
import com.vane.browser.ui.ActionIcons;
import com.vane.browser.ui.Sheet;
import com.vane.browser.ui.Theme;
import com.vane.browser.ui.Widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 自定义编辑器：上面分段切换，中间可视化预览，点任意一块就能换。 */
public class UiEditorActivity extends AppCompatActivity {

    private UiConfig ui;
    private LinearLayout body;
    private LinearLayout segRow;
    private int tab = 0;                      // 0 底栏 1 菜单 2 长按菜单 3 手势
    private final String[] TAB_NAMES = {"底栏", "菜单", "长按菜单", "手势"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ui = UiConfig.load();
        Theme.applyToWindow(this, ui);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.bg(this, ui));
        root.setFitsSystemWindows(true);

        // 顶栏
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Theme.dp(this, 8), Theme.dp(this, 12), Theme.dp(this, 12), Theme.dp(this, 8));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(Theme.fg(this, ui));
        int bp = Theme.dp(this, 10);
        back.setPadding(bp, bp, bp, bp);
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 40)));
        TextView reset = Widgets.text(this, "重置", 13, Theme.hint(this, ui));
        reset.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), Theme.dp(this, 10), Theme.dp(this, 6));
        reset.setOnClickListener(v -> resetAll());
        head.addView(reset);

        TextView title = Widgets.text(this, "自定义", 17, Theme.fg(this, ui));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(Theme.dp(this, 6), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        // 分段
        segRow = new LinearLayout(this);
        segRow.setOrientation(LinearLayout.HORIZONTAL);
        segRow.setPadding(Theme.dp(this, 12), 0, Theme.dp(this, 12), Theme.dp(this, 10));
        root.addView(segRow);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView t = Widgets.text(this, TAB_NAMES[i], 13, Theme.hint(this, ui));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Theme.dp(this, 8), 0, Theme.dp(this, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(Theme.dp(this, 6), 0, 0, 0);
            t.setOnClickListener(v -> {
                tab = idx;
                render();
            });
            segRow.addView(t, lp);
        }

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Theme.dp(this, 14), 0, Theme.dp(this, 14), Theme.dp(this, 30));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        render();
    }

    /** 一键重置所有自定义（对应 Via 编辑页顶部的「重置」）。 */
    private void resetAll() {
        Widgets.confirm(this, ui, "重置全部自定义？",
                "菜单、底栏、长按菜单、手势都会回到默认，当前修改会丢失。", () -> {
                    UiConfig d = UiConfig.defaults();
                    ui.items = d.items;
                    ui.menuItems = d.menuItems;
                    ui.linkMenu = d.linkMenu;
                    ui.gestures = d.gestures;
                    ui.save();
                    render();
                    Widgets.toast(this, "已重置");
                });
    }

    private void render() {
        // 分段高亮
        for (int i = 0; i < segRow.getChildCount(); i++) {
            TextView t = (TextView) segRow.getChildAt(i);
            boolean on = i == tab;
            t.setTextColor(on ? Theme.bg(this, ui) : Theme.hint(this, ui));
            t.setBackground(Theme.rounded(on ? Theme.fg(this, ui) : Theme.field(this, ui), Theme.dp(this, 12)));
        }
        body.removeAllViews();
        switch (tab) {
            case 0: renderBar(); break;
            case 1: renderMenu(); break;
            case 2: renderLinkMenu(); break;
            default: renderGestures(); break;
        }
    }

    // ================= 底栏 =================

    private void renderBar() {
        final List<UiConfig.Item> items = new ArrayList<>();
        for (UiConfig.Item it : ui.items) if (!it.flex) items.add(it);

        body.addView(hint("底栏固定，不能新增；点一格换功能，拖动行右侧 ⠿ 调顺序"));
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 16)));
        preview.setPadding(Theme.dp(this, 4), Theme.dp(this, 6), Theme.dp(this, 4), Theme.dp(this, 6));
        for (int i = 0; i < items.size(); i++) {
            final UiConfig.Item it = items.get(i);
            FrameLayout cell = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            int res = it.icon == null || it.icon.isEmpty()
                    ? ActionIcons.res(it.action)
                    : getResources().getIdentifier("ic_" + it.icon, "drawable", getPackageName());
            iv.setImageResource(res == 0 ? R.drawable.ic_menu : res);
            iv.setColorFilter(it.visible ? Theme.fg(this, ui) : Theme.hint(this, ui));
            int p = Theme.dp(this, 9);
            iv.setPadding(p, p, p, p);
            cell.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(this, 42), 1f);
            cell.setOnClickListener(v -> pickAction(act -> {
                it.action = act;
                ui.save();
                render();
            }));
            Widgets.pressFeedback(cell);
            preview.addView(cell, lp);
        }
        body.addView(preview);

        body.addView(hint("列表里可以改图标名和宽度"));
        for (int i = 0; i < items.size(); i++) {
            final UiConfig.Item it = items.get(i);
            final int idx = i;
            LinearLayout row = rowWithIcon(
                    it.icon == null || it.icon.isEmpty() ? ActionIcons.res(it.action)
                            : getResources().getIdentifier("ic_" + it.icon, "drawable", getPackageName()),
                    Actions.title(it.action) + (it.visible ? "" : "（已隐藏）"),
                    it.width == 0 ? "等分" : ("宽 " + it.width + "dp"));
            LinearLayout ops = new LinearLayout(this);
            ops.addView(smallBtn("▲", () -> {
                if (idx > 0) {
                    java.util.Collections.swap(ui.items, ui.items.indexOf(it), ui.items.indexOf(items.get(idx - 1)));
                    ui.save();
                    render();
                }
            }));
            ops.addView(smallBtn("▼", () -> {
                if (idx < items.size() - 1) {
                    java.util.Collections.swap(ui.items, ui.items.indexOf(it), ui.items.indexOf(items.get(idx + 1)));
                    ui.save();
                    render();
                }
            }));
            ops.addView(smallBtn(it.visible ? "隐藏" : "显示", () -> {
                it.visible = !it.visible;
                ui.save();
                render();
            }));
            row.addView(ops);
            row.setOnClickListener(v -> itemDetail(it));
            body.addView(row);
            body.addView(divider());
        }
        // 底栏固定：不提供新增入口
    }

    private void itemDetail(final UiConfig.Item it) {
        Widgets.pick(this, ui, Actions.title(it.action), java.util.Arrays.asList(
                "换功能", "改显示文字（现在：" + it.glyph + "）",
                "改宽度（现在 " + it.width + "dp）", "删除"), i -> {
            if (i == 0) pickAction(act -> {
                it.action = act;
                ui.save();
                render();
            });
            else if (i == 1) Widgets.input(this, ui, "显示文字", it.glyph, false, v -> {
                it.glyph = v;
                it.icon = "";
                ui.save();
                render();
            });
            else if (i == 2) Widgets.input(this, ui, "宽度 dp（0=等分）", String.valueOf(it.width), false, v -> {
                try {
                    it.width = Math.max(0, Integer.parseInt(v.trim()));
                } catch (Exception ignored) {}
                ui.save();
                render();
            });
            else {
                ui.items.remove(it);
                ui.save();
                render();
            }
        });
    }

    // ================= 菜单 =================

    private void renderMenu() {
        body.addView(hint("按住右侧 ⠿ 拖动排序；点一行换功能，长按删除"));
        addDragList(ui.menuItems);
        body.addView(bigBtn("＋ 加一格", () -> pickAction(act -> {
            ui.menuItems.add(act);
            ui.save();
            render();
        })));
    }

    /** 可拖动排序的列表（对应 Via 的「按住并拖动重新排列」）。 */
    private void addDragList(final List<String> ids) {
        final androidx.recyclerview.widget.RecyclerView rv =
                new androidx.recyclerview.widget.RecyclerView(this);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        final androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> adapter =
                new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                    @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                        LinearLayout row = new LinearLayout(UiEditorActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(0, Theme.dp(UiEditorActivity.this, 10), 0, Theme.dp(UiEditorActivity.this, 10));
                        return new androidx.recyclerview.widget.RecyclerView.ViewHolder(row) {};
                    }

                    @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder h, int position) {
                        LinearLayout row = (LinearLayout) h.itemView;
                        row.removeAllViews();
                        if (position < 0 || position >= ids.size()) return;
                        final String id = ids.get(position);
                        ImageView iv = new ImageView(UiEditorActivity.this);
                        iv.setImageResource(ActionIcons.res(id));
                        iv.setColorFilter(Theme.fg(UiEditorActivity.this, ui));
                        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                                Theme.dp(UiEditorActivity.this, 22), Theme.dp(UiEditorActivity.this, 22));
                        ilp.setMargins(0, 0, Theme.dp(UiEditorActivity.this, 12), 0);
                        row.addView(iv, ilp);
                        row.addView(Widgets.text(UiEditorActivity.this, Actions.title(id), 14,
                                Theme.fg(UiEditorActivity.this, ui)),
                                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        TextView handle = Widgets.text(UiEditorActivity.this, "\u283F", 16,
                                Theme.hint(UiEditorActivity.this, ui));
                        handle.setPadding(Theme.dp(UiEditorActivity.this, 10), 0, Theme.dp(UiEditorActivity.this, 4), 0);
                        row.addView(handle);
                        row.setOnClickListener(v -> promptFor(ids, id));
                        row.setOnLongClickListener(v -> {
                            Widgets.confirm(UiEditorActivity.this, ui, "删掉这一格？",
                                    "「" + Actions.title(id) + "」会从菜单移除。", () -> {
                                        ids.remove(id);
                                        ui.save();
                                        render();
                                    });
                            return true;
                        });
                    }

                    @Override public int getItemCount() { return ids.size(); }
                };
        rv.setAdapter(adapter);
        rv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(ids.size(), 8) * Theme.dp(this, 46)));
        new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP
                                | androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0) {
                    @Override public boolean onMove(androidx.recyclerview.widget.RecyclerView r,
                                                    androidx.recyclerview.widget.RecyclerView.ViewHolder a,
                                                    androidx.recyclerview.widget.RecyclerView.ViewHolder b) {
                        int from = a.getAdapterPosition(), to = b.getAdapterPosition();
                        if (from < 0 || to < 0 || from >= ids.size() || to >= ids.size()) return false;
                        java.util.Collections.swap(ids, from, to);
                        adapter.notifyItemMoved(from, to);
                        return true;
                    }
                    @Override public void onSwiped(androidx.recyclerview.widget.RecyclerView.ViewHolder vh, int dir) {}
                    @Override public void clearView(androidx.recyclerview.widget.RecyclerView r,
                                                    androidx.recyclerview.widget.RecyclerView.ViewHolder vh) {
                        super.clearView(r, vh);
                        ui.save();
                    }
                }).attachToRecyclerView(rv);
        body.addView(rv);
    }



    private void renderLinkMenu() {
        body.addView(hint("长按网页链接时弹出的项"));
        addGrid(ui.linkMenu, false);
        body.addView(bigBtn("＋ 加一项", () -> pickAction(act -> {
            ui.linkMenu.add(act);
            ui.save();
            render();
        })));
    }

    private void addGrid(final List<String> ids, boolean fiveCols) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(fiveCols ? 5 : 3);
        for (final String id : ids) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 8));
            ImageView iv = new ImageView(this);
            iv.setImageResource(ActionIcons.res(id));
            iv.setColorFilter(Theme.fg(this, ui));
            cell.addView(iv, new LinearLayout.LayoutParams(Theme.dp(this, 22), Theme.dp(this, 22)));
            TextView t = Widgets.text(this, Actions.title(id), 10, Theme.hint(this, ui));
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(1);
            t.setPadding(0, Theme.dp(this, 4), 0, 0);
            cell.addView(t);
            cell.setOnClickListener(v -> promptFor(ids, id));
            cell.setOnLongClickListener(v -> {
                Widgets.confirm(UiEditorActivity.this, ui, "删掉这一格？",
                        "「" + Actions.title(id) + "」会从菜单里移除，之后可以再加回来。",
                        () -> {
                            ids.remove(id);
                            ui.save();
                            render();
                        });
                return true;
            });
            Widgets.pressFeedback(cell);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            p.width = 0;
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            grid.addView(cell, p);
        }
        body.addView(grid);
    }

    private void promptFor(final List<String> ids, final String id) {
        Widgets.pick(this, ui, Actions.title(id), java.util.Arrays.asList(
                "换功能", "上移", "下移", "删除"), i -> {
            int at = ids.indexOf(id);
            if (i == 0) pickAction(act -> {
                if (at >= 0) ids.set(at, act);
                ui.save();
                render();
            });
            else if (i == 1 && at > 0) {
                java.util.Collections.swap(ids, at, at - 1);
                ui.save();
                render();
            } else if (i == 2 && at >= 0 && at < ids.size() - 1) {
                java.util.Collections.swap(ids, at, at + 1);
                ui.save();
                render();
            } else if (i == 3) {
                ids.remove(id);
                ui.save();
                render();
            }
        });
    }

    // ================= 手势 =================

    private void renderGestures() {
        body.addView(hint("每个手势绑定一个功能，点一行换"));
        for (final Map.Entry<String, String> e : new java.util.LinkedHashMap<>(ui.gestures).entrySet()) {
            LinearLayout row = rowWithIcon(ActionIcons.res(e.getValue()),
                    gestureName(e.getKey()), Actions.title(e.getValue()));
            row.addView(smallBtn("换", () -> pickAction(act -> {
                ui.gestures.put(e.getKey(), act);
                ui.save();
                render();
            })));
            body.addView(row);
            body.addView(divider());
        }
    }

    private String gestureName(String key) {
        switch (key) {
            case "edge_left_swipe": return "左边缘右滑";
            case "edge_right_swipe": return "右边缘左滑";
            case "toolbar_long_press": return "工具栏长按";
            case "address_long_press": return "地址栏长按";
            case "page_long_press_link": return "长按网页链接";
            case "pull_down": return "页面向下拉";
            case "double_tap_top": return "双击页面顶部";
            default: return key;
        }
    }

    // ================= 选功能（图标网格 + 搜索） =================

    private interface PickAct { void on(String actionId); }

    private void pickAction(final PickAct cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 16));
        box.addView(Sheet.handle(this, ui));
        TextView title = Widgets.text(this, "选一个功能", 17, Theme.fg(this, ui));
        title.setPadding(0, 0, 0, Theme.dp(this, 8));
        box.addView(title);

        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索功能");
        search.setTextSize(14);
        search.setTextColor(Theme.fg(this, ui));
        search.setHintTextColor(Theme.hint(this, ui));
        search.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 12)));
        int sp = Theme.dp(this, 10);
        search.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = Theme.dp(this, 8);
        box.addView(search, slp);

        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(grid);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.55);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        final Dialog d = Sheet.show(this, ui, box, true);

        final Runnable fill = new Runnable() {
            @Override public void run() {
                grid.removeAllViews();
                String q = search.getText().toString().trim().toLowerCase();
                for (Actions.Def def : Actions.all()) {
                    if (!q.isEmpty() && !(def.label + " " + def.id).toLowerCase().contains(q)) continue;
                    LinearLayout cell = new LinearLayout(UiEditorActivity.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER);
                    cell.setPadding(0, Theme.dp(UiEditorActivity.this, 10), 0, Theme.dp(UiEditorActivity.this, 8));
                    ImageView iv = new ImageView(UiEditorActivity.this);
                    iv.setImageResource(ActionIcons.res(def.id));
                    iv.setColorFilter(Theme.fg(UiEditorActivity.this, ui));
                    cell.addView(iv, new LinearLayout.LayoutParams(Theme.dp(UiEditorActivity.this, 22), Theme.dp(UiEditorActivity.this, 22)));
                    TextView t = Widgets.text(UiEditorActivity.this, def.label, 10, Theme.hint(UiEditorActivity.this, ui));
                    t.setGravity(Gravity.CENTER);
                    t.setMaxLines(2);
                    t.setPadding(0, Theme.dp(UiEditorActivity.this, 4), 0, 0);
                    cell.addView(t);
                    cell.setOnClickListener(v -> {
                        d.dismiss();
                        cb.on(def.id);
                    });
                    Widgets.pressFeedback(cell);
                    GridLayout.LayoutParams p = new GridLayout.LayoutParams();
                    p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    p.width = 0;
                    p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    grid.addView(cell, p);
                }
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { fill.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        fill.run();
    }

    // ================= 小部件 =================

    private TextView hint(String s) {
        TextView t = Widgets.text(this, s, 12, Theme.hint(this, ui));
        t.setPadding(0, Theme.dp(this, 4), 0, Theme.dp(this, 10));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Theme.line(this, ui));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(this, 1) / 2)));
        return v;
    }

    private LinearLayout rowWithIcon(int icon, String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 10));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon == 0 ? R.drawable.ic_menu : icon);
        iv.setColorFilter(Theme.fg(this, ui));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Theme.dp(this, 22), Theme.dp(this, 22));
        ilp.setMargins(0, 0, Theme.dp(this, 12), 0);
        row.addView(iv, ilp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Widgets.text(this, title, 14, Theme.fg(this, ui)));
        if (sub != null && !sub.isEmpty()) {
            TextView s2 = Widgets.text(this, sub, 11, Theme.hint(this, ui));
            s2.setPadding(0, Theme.dp(this, 2), 0, 0);
            texts.addView(s2);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private TextView smallBtn(String label, Runnable r) {
        TextView t = Widgets.text(this, label, 12, Theme.hint(this, ui));
        t.setGravity(Gravity.CENTER);
        t.setPadding(Theme.dp(this, 8), Theme.dp(this, 5), Theme.dp(this, 8), Theme.dp(this, 5));
        t.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Theme.dp(this, 4), 0, 0, 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> r.run());
        Widgets.pressFeedback(t);
        return t;
    }

    private TextView bigBtn(String label, Runnable r) {
        TextView t = Widgets.text(this, label, 13, Theme.fg(this, ui));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Theme.dp(this, 11), 0, Theme.dp(this, 11));
        t.setBackground(Theme.rounded(Theme.field(this, ui), Theme.dp(this, 12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 12);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> r.run());
        Widgets.pressFeedback(t);
        return t;
    }
}
