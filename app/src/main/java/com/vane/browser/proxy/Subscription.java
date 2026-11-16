package com.vane.browser.proxy;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 一个订阅：一个 URL 加解析出来的节点。 */
public class Subscription {
    public String url = "";
    public String name = "";
    public long updated = 0;
    public String error = "";
    public List<Node> nodes = new ArrayList<>();

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("url", url);
            o.put("name", name);
            o.put("updated", updated);
            o.put("error", error);
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Node n : nodes) arr.put(n.toJson());
            o.put("nodes", arr);
        } catch (Exception ignored) {}
        return o;
    }

    public static Subscription fromJson(JSONObject o) {
        Subscription s = new Subscription();
        s.url = o.optString("url", "");
        s.name = o.optString("name", s.url);
        s.updated = o.optLong("updated", 0);
        s.error = o.optString("error", "");
        org.json.JSONArray arr = o.optJSONArray("nodes");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject n = arr.optJSONObject(i);
                if (n != null) s.nodes.add(Node.fromJson(n));
            }
        }
        return s;
    }
}
