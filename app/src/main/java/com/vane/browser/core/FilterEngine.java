package com.vane.browser.core;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * EasyList 子集的网络规则引擎：支持 ||domain^ 、/ads/* 通配、@@ 例外。
 * 只做请求级拦截，忽略 $modifier（third-party/script 等），够用且轻。
 */
public final class FilterEngine {

    private static final class Rule {
        Pattern re;
        boolean exception;
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static volatile boolean loaded = false;

    private FilterEngine() {}

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        RULES.clear();
        readAsset(c, "filters.txt");
        readFile(new File(c.getFilesDir(), "rules/remote-filters.txt"));
        String custom = com.vane.browser.settings.Prefs.get("ad_filter_custom", "");
        for (String line : custom.split("\\n")) add(line.trim());
    }

    private static void readAsset(Context c, String name) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open(name)), 8192)) {
            String line;
            while ((line = r.readLine()) != null) add(line.trim());
        } catch (Exception ignored) {}
    }

    private static void readFile(File f) {
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) add(line.trim());
        } catch (Exception ignored) {}
    }

    private static void add(String line) {
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return;
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#")) return;   // 元素规则不在这里
        boolean exception = line.startsWith("@@");
        if (exception) line = line.substring(2);
        int dollar = line.indexOf('$');
        if (dollar > 0) line = line.substring(0, dollar);      // 丢弃修饰符
        if (line.isEmpty()) return;

        StringBuilder re = new StringBuilder();
        String body = line;
        if (body.startsWith("||")) {                            // 域名锚定
            body = body.substring(2);
            re.append("^https?://([a-z0-9-]+\\\\.)*");
            int caret = body.indexOf('^');
            if (caret >= 0) body = body.substring(0, caret);
            re.append(Pattern.quote(body).replace("\\\\Q", "").replace("\\\\E", ""));
            re.append("([/:?]|$)");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String part : body.split("\\*")) {
                if (sb.length() > 0) sb.append(".*");
                sb.append(Pattern.quote(part));
            }
            re.append(".*").append(sb).append(".*");
        }
        try {
            Rule rule = new Rule();
            rule.re = Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE);
            rule.exception = exception;
            RULES.add(rule);
        } catch (Exception ignored) {}
    }

    /** 命中返回 true（例外规则优先）。 */
    public static boolean blocked(String url) {
        if (url == null || url.isEmpty() || RULES.isEmpty()) return false;
        boolean hit = false;
        for (Rule r : RULES) {
            if (r.exception) {
                if (r.re.matcher(url).find()) return false;      // 有例外立即放行
            } else if (!hit && r.re.matcher(url).find()) {
                hit = true;
            }
        }
        return hit;
    }

    public static int size() {
        return RULES.size();
    }
}
