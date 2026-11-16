package org.json;
public class JSONObject {
    public JSONObject() {}
    public JSONObject(String s) { this(); }
    public JSONObject put(String k, Object v) { return this; }
    public JSONObject put(String k, int v) { return this; }
    public JSONObject put(String k, boolean v) { return this; }
    public String optString(String k, String d) { return d; }
    public int optInt(String k, int d) { return d; }
    public long optLong(String k, long d) { return d; }
    public boolean optBoolean(String k, boolean d) { return d; }
    public org.json.JSONArray optJSONArray(String k) { return null; }
}
