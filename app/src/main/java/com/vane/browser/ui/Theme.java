package com.vane.browser.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.ColorUtils;

import com.vane.browser.settings.UiConfig;

/**
 * 配色：浅色 / 深色 / 纯黑 / 自定义（用户给一个背景色，其余颜色由它推导）。
 * 所有界面（栏、面板、输入框、分隔线）和内置主页都从这里取色，避免出现"一半白一半黑"。
 */
public final class Theme {

    private Theme() {}

    private static int parse(String v, int def) {
        try {
            return Color.parseColor(v);
        } catch (Exception e) {
            return def;
        }
    }

    public static int bg(Context c, UiConfig ui) {
        if (ui.theme == null) return Color.WHITE;
        switch (ui.theme) {
            case "dark": return Color.parseColor("#101114");
            case "amoled": return Color.BLACK;
            case "custom": return parse(ui.customBg, Color.parseColor("#101114"));
            default: return Color.WHITE;
        }
    }

    /** 栏（顶栏/底栏/面板）的底色，与背景一致，避免出现色块。 */
    public static int bar(Context c, UiConfig ui) {
        return bg(c, ui);
    }

    public static boolean isDark(Context c, UiConfig ui) {
        return ColorUtils.calculateLuminance(bg(c, ui)) < 0.42;
    }

    public static int fg(Context c, UiConfig ui) {
        return isDark(c, ui) ? Color.parseColor("#E9EAEE") : Color.BLACK;
    }

    /** 输入框/胶囊底：背景与前景混一点点。 */
    public static int field(Context c, UiConfig ui) {
        return ColorUtils.blendARGB(bg(c, ui), fg(c, ui), isDark(c, ui) ? 0.10f : 0.06f);
    }

    public static int line(Context c, UiConfig ui) {
        return ColorUtils.blendARGB(bg(c, ui), fg(c, ui), isDark(c, ui) ? 0.16f : 0.10f);
    }

    public static int hint(Context c, UiConfig ui) {
        return ColorUtils.blendARGB(bg(c, ui), fg(c, ui), isDark(c, ui) ? 0.55f : 0.55f);
    }

    public static int accent(UiConfig ui) {
        return parse(ui.accent, Color.parseColor("#5B8CFF"));
    }

    public static int dp(Context c, int v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    public static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** 指定线宽的描边圆角。 */
    public static GradientDrawable roundedStrokeWidth(int fill, int stroke, int radiusPx, int widthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        d.setStroke(Math.max(1, widthPx), stroke);
        return d;
    }

    public static GradientDrawable roundedStroke(int fill, int stroke, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        d.setStroke(Math.max(1, dp2(stroke)), stroke);
        return d;
    }

    private static int dp2(int v) {
        return Math.max(1, v);
    }

    /** 把颜色写成 #RRGGBB，给网页注入用。 */
    public static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    public static void applyToWindow(Activity a, UiConfig ui) {
        Window w = a.getWindow();
        int bg = bg(a, ui);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        boolean dark = isDark(a, ui);
        if (Build.VERSION.SDK_INT >= 23) {
            if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }
}
