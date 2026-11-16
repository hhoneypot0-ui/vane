package com.vane.browser.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vane.browser.settings.UiConfig;

import java.util.List;

/** 极简对话框/行组件，全部用原生控件画，不依赖 Material。 */
public final class Widgets {

    private Widgets() {}

    public interface Pick { void on(int index); }
    public interface Text { void on(String value); }
    public interface Simple { void on(); }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static TextView text(Context c, String s, int size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    /** 面板外壳：横条 + 标题 + 内容 + 按钮。 */
    private static Dialog panel(Context c, UiConfig ui, String title, View content,
                                String negative, Simple negAction,
                                String positive, Simple posAction) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(c, 16);
        box.setPadding(pad, 0, pad, Theme.dp(c, 16));
        box.addView(Sheet.handle(c, ui));
        if (title != null && !title.isEmpty()) {
            TextView t = text(c, title, 16, Theme.fg(c, ui));
            t.setPadding(0, 0, 0, Theme.dp(c, 12));
            box.addView(t);
        }
        if (content != null) box.addView(content);

        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Theme.dp(c, 16), 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, Theme.dp(c, 8), 0);
        final Dialog[] holder = new Dialog[1];
        if (negative != null) {
            btns.addView(ghostButton(c, ui, negative, () -> {
                if (holder[0] != null) holder[0].dismiss();
                if (negAction != null) negAction.on();
            }), lp);
        }
        btns.addView(button(c, ui, positive == null ? "好" : positive, () -> {
            if (holder[0] != null) holder[0].dismiss();
            if (posAction != null) posAction.on();
        }), negative == null
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(btns);

        holder[0] = Sheet.show(c, ui, box, true);
        return holder[0];
    }

    /** 列表式选择：整屏可滚，点一项就选。 */
    public static Dialog pick(Context c, UiConfig ui, String title, List<String> items, Pick p) {
        final Dialog[] self = new Dialog[1];
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            TextView row = text(c, items.get(i), 14, Theme.fg(c, ui));
            row.setPadding(Theme.dp(c, 6), Theme.dp(c, 13), Theme.dp(c, 6), Theme.dp(c, 13));
            row.setOnClickListener(v -> {
                if (self[0] != null) self[0].dismiss();
                p.on(idx);
            });
            list.addView(row);
            View line = new View(c);
            line.setBackgroundColor(Theme.line(c, ui));
            list.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(c, 1) / 2 + 1)));
        }
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        int h = Math.min((int) (c.getResources().getDisplayMetrics().heightPixels * 0.62),
                Theme.dp(c, 60) * Math.min(items.size(), 10) + Theme.dp(c, 20));
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        Dialog d = panel(c, ui, title, sv, "关闭", null, null, null);
        self[0] = d;
        return d;
    }

    public static Dialog menu(Context c, UiConfig ui, String title, List<String> items, Pick p) {
        return pick(c, ui, title, items, p);
    }

    public static Dialog input(Context c, UiConfig ui, String title, String value, boolean multiline, Text cb) {
        EditText e = new EditText(c);
        e.setText(value == null ? "" : value);
        e.setTextColor(Theme.fg(c, ui));
        e.setHintTextColor(Theme.hint(c, ui));
        e.setTextSize(14);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(4);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        e.setBackground(rounded(ui, c));
        int p = Theme.dp(c, 12);
        e.setPadding(p, p, p, p);
        return panel(c, ui, title, e, "取消", null, "确定", () -> cb.on(e.getText().toString()));
    }

    private static android.graphics.drawable.GradientDrawable rounded(UiConfig ui, Context c) {
        return Theme.rounded(Theme.field(c, ui), Theme.dp(c, 12));
    }

    public static Dialog confirm(Context c, UiConfig ui, String title, String msg, Simple ok) {
        TextView m = text(c, msg == null ? "" : msg, 14, Theme.hint(c, ui));
        m.setPadding(0, 0, 0, Theme.dp(c, 4));
        return panel(c, ui, title, m, "取消", null, "确定", ok);
    }

    /** 带说明的两按钮面板（允许/拒绝、删除/取消这类）。 */
    public static Dialog choice(Context c, UiConfig ui, String title, String msg,
                                String posLabel, Simple pos,
                                String negLabel, Simple neg) {
        TextView m = text(c, msg == null ? "" : msg, 14, Theme.hint(c, ui));
        return panel(c, ui, title, m, negLabel, neg, posLabel, pos);
    }

    /** 纯说明面板。 */
    public static Dialog info(Context c, UiConfig ui, String title, String msg) {
        TextView m = text(c, msg == null ? "" : msg, 14, Theme.hint(c, ui));
        m.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(m);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 260)));
        return panel(c, ui, title, sv, null, null, "好", null);
    }

    /** 设置页里的一行：标题 + 右侧当前值。 */
    public static View row(Context c, UiConfig ui, String title, String value, String note, Simple click) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int ph = Theme.dp(c, 14), pv = Theme.dp(c, 12);
        l.setPadding(ph, pv, ph, pv);
        TextView t = text(c, title, 15, Theme.fg(c, ui));
        l.addView(t);
        if (value != null && !value.isEmpty()) {
            TextView v = text(c, value, 13, Theme.accent(ui));
            v.setPadding(0, Theme.dp(c, 2), 0, 0);
            l.addView(v);
        }
        if (note != null && !note.isEmpty()) {
            TextView n = text(c, note, 12, Theme.hint(c, ui));
            n.setPadding(0, Theme.dp(c, 2), 0, 0);
            l.addView(n);
        }
        l.setBackgroundColor(Theme.bg(c, ui));
        if (click != null) l.setOnClickListener(v -> click.on());
        return l;
    }

    /** 右侧那颗胶囊状态按钮：开=实心强调色，关=浅灰。 */
    public static TextView pill(Context c, UiConfig ui, boolean on, String onText, String offText) {
        TextView t = new TextView(c);
        t.setText(on ? onText : offText);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        // 开：强调色填充（蓝）；关：浅灰底灰字
        t.setTextColor(on ? Color.WHITE : Theme.hint(c, ui));
        t.setBackground(Theme.rounded(on ? Theme.accent(ui) : Theme.field(c, ui), Theme.dp(c, 15)));
        t.setMinWidth(Theme.dp(c, 56));
        t.setPadding(Theme.dp(c, 14), Theme.dp(c, 7), Theme.dp(c, 14), Theme.dp(c, 7));
        return t;
    }

    /** 一行「标题 + 右侧开关按钮」，整行可点。 */
    public static View switchRow(Context c, UiConfig ui, String title, String note, boolean on, Simple click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = Theme.dp(c, 16), pv = Theme.dp(c, 12);
        row.setPadding(ph, pv, ph, pv);
        row.setBackgroundColor(Theme.bg(c, ui));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        // 开着的时候标题用强调色，关着用正常黑字
        texts.addView(text(c, title, 15, Theme.fg(c, ui)));
        if (note != null && !note.isEmpty()) {
            TextView n = text(c, note, 12, Theme.hint(c, ui));
            n.setPadding(0, Theme.dp(c, 2), 0, 0);
            texts.addView(n);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = pill(c, ui, on, "开启", "关闭");
        row.addView(btn);
        row.setOnClickListener(v -> click.on());
        btn.setOnClickListener(v -> click.on());
        return row;
    }

    /** 分段按钮组：一排选项，选中的那个填充强调色。 */
    public interface Pick2 { void on(int index); }

    public static View segment(Context c, UiConfig ui, String title, String[] options, int selected, Pick2 cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int ph = Theme.dp(c, 16), pv = Theme.dp(c, 12);
        box.setPadding(ph, pv, ph, pv);
        box.setBackgroundColor(Theme.bg(c, ui));
        box.addView(text(c, title, 15, Theme.fg(c, ui)));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Theme.dp(c, 10);
        row.setLayoutParams(rlp);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView b = new TextView(c);
            b.setText(options[i]);
            b.setTextSize(13);
            b.setGravity(Gravity.CENTER);
            b.setTextColor(on ? Color.WHITE : Theme.hint(c, ui));
            b.setBackground(Theme.rounded(on ? Theme.accent(ui) : Theme.field(c, ui), Theme.dp(c, 12)));
            b.setPadding(Theme.dp(c, 6), Theme.dp(c, 9), Theme.dp(c, 6), Theme.dp(c, 9));
            b.setOnClickListener(v -> cb.on(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(i == 0 ? 0 : Theme.dp(c, 6), 0, 0, 0);
            row.addView(b, lp);
        }
        box.addView(row);
        return box;
    }

    public static View divider(Context c, UiConfig ui) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(c, 1)));
        v.setLayoutParams(lp);
        v.setBackgroundColor(Theme.line(c, ui));
        return v;
    }

    public static View section(Context c, UiConfig ui, String title) {
        TextView t = text(c, title, 12, Theme.hint(c, ui));
        int ph = Theme.dp(c, 16);
        t.setPadding(ph, Theme.dp(c, 18), ph, Theme.dp(c, 6));
        t.setBackgroundColor(Theme.bg(c, ui));
        return t;
    }

    /** 按下缩一点、松手弹回。 */
    public static void pressFeedback(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
            }
            return false;
        });
    }

    public static TextView button(Context c, UiConfig ui, String label, Simple click) {
        TextView t = text(c, label, 14, Color.WHITE);
        pressFeedback(t);
        t.setGravity(Gravity.CENTER);
        int ph = Theme.dp(c, 14), pv = Theme.dp(c, 9);
        t.setPadding(ph, pv, ph, pv);
        t.setBackground(Theme.rounded(Theme.accent(ui), Theme.dp(c, 10)));
        t.setOnClickListener(v -> click.on());
        return t;
    }

    public static TextView ghostButton(Context c, UiConfig ui, String label, Simple click) {
        TextView t = text(c, label, 14, Theme.accent(ui));
        pressFeedback(t);
        t.setGravity(Gravity.CENTER);
        int ph = Theme.dp(c, 12), pv = Theme.dp(c, 8);
        t.setPadding(ph, pv, ph, pv);
        t.setBackground(Theme.rounded(Theme.field(c, ui), Theme.dp(c, 10)));
        t.setOnClickListener(v -> click.on());
        return t;
    }
}
