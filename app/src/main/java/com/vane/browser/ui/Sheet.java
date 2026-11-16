package com.vane.browser.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vane.browser.settings.UiConfig;

/** 从底部升起的面板：比一层套一层的对话框干净得多。 */
public final class Sheet {

    private Sheet() {}

    /** 当前打开的面板（返回键先关它）。 */
    public static Dialog current;

    public static Dialog show(Context c, UiConfig ui, View content, boolean dim) {
        Dialog d = new Dialog(c);
        current = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.bg(c, ui));
        float r = Theme.dp(c, 26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        wrap.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            // ColorOS 风：背景毛玻璃（Android 12+）+ 弹性动画
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    w.getAttributes().setBlurBehindRadius(Theme.dp(c, 26));
                    w.setAttributes(w.getAttributes());
                } catch (Throwable ignored) {}
            }
            w.setWindowAnimations(com.vane.browser.R.style.SheetAnim);
            if (dim) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = dim ? 0.16f : 0f;
            w.setAttributes(lp);
        }
        d.setOnDismissListener(x -> { if (current == d) current = null; });
        d.show();
        dragToDismiss(d, wrap);
        return d;
    }

    /** 下拉跟手关闭：抓住面板顶部往下拖，松手够远就关，否则弹回。 */
    private static void dragToDismiss(final Dialog d, final View panel) {
        final float[] downY = {0};
        final boolean[] dragging = {false};
        final int threshold = Theme.dp(panel.getContext(), 90);
        panel.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downY[0] = e.getRawY();
                    dragging[0] = e.getRawY() < panel.getTop() + Theme.dp(panel.getContext(), 56);
                    return false;   // 不吞事件，子控件照常响应
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) return false;
                    float dy = Math.max(0, e.getRawY() - downY[0]);
                    panel.setTranslationY(dy);
                    panel.setAlpha(Math.max(0.35f, 1f - dy / (threshold * 4f)));
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!dragging[0]) return false;
                    dragging[0] = false;
                    float dy2 = panel.getTranslationY();
                    if (dy2 > threshold) {
                        d.dismiss();
                    } else {
                        panel.animate().translationY(0).alpha(1f).setDuration(160).start();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    /** 顶部那根小横条，暗示可以往下划走。 */
    public static View handle(Context c, UiConfig ui) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.line(c, ui));
        g.setCornerRadius(Theme.dp(c, 3));
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(c, 36), Theme.dp(c, 4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Theme.dp(c, 10);
        lp.bottomMargin = Theme.dp(c, 6);
        v.setLayoutParams(lp);
        return v;
    }

    /** 九宫格里的一格：上面矢量图标，下面文字（现在用的这个）。 */
    public static View cell(Context c, UiConfig ui, int iconRes, String label, Runnable click) {
        return cell(c, ui, iconRes, label, false, click);
    }

    /** active=true 表示这个功能当前是开的：图标和文字用强调色。 */
    public static View cell(Context c, UiConfig ui, int iconRes, String label, boolean active, Runnable click) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Theme.dp(c, 14), 0, Theme.dp(c, 12));
        Widgets.pressFeedback(l);

        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setImageResource(iconRes);
        iv.setColorFilter(active ? Theme.accent(ui) : Theme.fg(c, ui));
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        l.addView(iv, new LinearLayout.LayoutParams(Theme.dp(c, 24), Theme.dp(c, 24)));

        TextView t = new TextView(c);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxLines(1);
        t.setTextSize(10);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(active ? Theme.accent(ui) : Theme.hint(c, ui));
        t.setPadding(0, Theme.dp(c, 7), 0, 0);
        l.addView(t);
        l.setOnClickListener(v -> click.run());
        return l;
    }

    /** 旧版：用文字当图标（自定义按键还是走这个）。 */
    public static View cell(Context c, UiConfig ui, String glyph, String label, Runnable click) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Theme.dp(c, 12), 0, Theme.dp(c, 12));
        TextView g = new TextView(c);
        g.setText(glyph);
        g.setTextSize(20);
        g.setGravity(Gravity.CENTER);
        g.setTextColor(Theme.fg(c, ui));
        TextView t = new TextView(c);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxLines(1);
        t.setTextSize(10);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Theme.hint(c, ui));
        t.setPadding(0, Theme.dp(c, 4), 0, 0);
        l.addView(g);
        l.addView(t);
        l.setOnClickListener(v -> click.run());
        return l;
    }
}
