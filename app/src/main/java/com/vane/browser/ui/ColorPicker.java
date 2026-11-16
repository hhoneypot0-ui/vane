package com.vane.browser.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.vane.browser.settings.UiConfig;

/** 色板：色相 / 饱和度 / 亮度三根滑条 + 实时预览。用我们自己的底部面板，不用系统弹窗。 */
public final class ColorPicker {

    public interface Cb {
        void on(String hex);
    }

    private ColorPicker() {}

    public static Dialog show(final Context c, final UiConfig ui, String initial, final Cb cb) {
        final float[] hsv = new float[]{210f, 0.45f, 0.35f};
        try {
            Color.colorToHSV(Color.parseColor(initial), hsv);
        } catch (Exception ignored) {}

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(c, 18);
        root.setPadding(pad, 0, pad, Theme.dp(c, 18));
        root.addView(Sheet.handle(c, ui));

        TextView title = Widgets.text(c, "调色", 13, Theme.hint(c, ui));
        title.setPadding(0, 0, 0, Theme.dp(c, 12));
        root.addView(title);

        // 预览色块 + 色值
        final View preview = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(Theme.dp(c, 14));
        pg.setStroke(Theme.dp(c, 1), Theme.line(c, ui));
        preview.setBackground(pg);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 72)));

        final TextView hexView = Widgets.text(c, "", 14, Theme.fg(c, ui));
        hexView.setGravity(Gravity.CENTER);
        hexView.setPadding(0, Theme.dp(c, 10), 0, Theme.dp(c, 4));
        root.addView(hexView);

        root.addView(label(c, ui, "色相"));
        final SeekBar hue = bar(c, ui, 360);
        GradientDrawable hueBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                Color.HSVToColor(new float[]{0, 1, 1}),
                Color.HSVToColor(new float[]{60, 1, 1}),
                Color.HSVToColor(new float[]{120, 1, 1}),
                Color.HSVToColor(new float[]{180, 1, 1}),
                Color.HSVToColor(new float[]{240, 1, 1}),
                Color.HSVToColor(new float[]{300, 1, 1}),
                Color.HSVToColor(new float[]{360, 1, 1})
        });
        hueBg.setCornerRadius(Theme.dp(c, 10));
        hue.setBackground(hueBg);
        root.addView(hue);

        root.addView(label(c, ui, "饱和度"));
        final SeekBar sat = bar(c, ui, 100);
        root.addView(sat);

        root.addView(label(c, ui, "亮度"));
        final SeekBar val = bar(c, ui, 100);
        root.addView(val);

        final Runnable update = () -> {
            int color = Color.HSVToColor(new float[]{hsv[0], hsv[1], hsv[2]});
            ((GradientDrawable) preview.getBackground()).setColor(color);
            hexView.setText(String.format("#%06X", 0xFFFFFF & color));
        };

        hue.setOnSeekBarChangeListener(new Simple() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                hsv[0] = p; update.run();
            }
        });
        sat.setOnSeekBarChangeListener(new Simple() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                hsv[1] = p / 100f; update.run();
            }
        });
        val.setOnSeekBarChangeListener(new Simple() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                hsv[2] = p / 100f; update.run();
            }
        });

        hue.setProgress((int) hsv[0]);
        sat.setProgress((int) (hsv[1] * 100));
        val.setProgress((int) (hsv[2] * 100));
        update.run();

        // 底部两个按钮，跟应用其他面板一套样式
        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Theme.dp(c, 18), 0, 0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(0, 0, Theme.dp(c, 8), 0);

        final Dialog[] holder = new Dialog[1];
        TextView cancel = Widgets.ghostButton(c, ui, "取消", () -> {
            if (holder[0] != null) holder[0].dismiss();
        });
        btns.addView(cancel, blp);

        TextView ok = Widgets.button(c, ui, "用这个颜色", () -> {
            int color = Color.HSVToColor(new float[]{hsv[0], hsv[1], hsv[2]});
            if (holder[0] != null) holder[0].dismiss();
            cb.on(String.format("#%06X", 0xFFFFFF & color));
        });
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(btns);

        holder[0] = Sheet.show(c, ui, root, true);
        return holder[0];
    }

    private static TextView label(Context c, UiConfig ui, String s) {
        TextView t = Widgets.text(c, s, 12, Theme.hint(c, ui));
        t.setPadding(0, Theme.dp(c, 10), 0, Theme.dp(c, 2));
        return t;
    }

    private static SeekBar bar(Context c, UiConfig ui, int max) {
        SeekBar b = new SeekBar(c);
        b.setMax(max);
        try {
            if (b.getProgressDrawable() != null) b.getProgressDrawable().setTint(Theme.accent(ui));
            if (b.getThumb() != null) b.getThumb().setTint(Theme.accent(ui));
        } catch (Throwable ignored) {}
        return b;
    }

    private abstract static class Simple implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
