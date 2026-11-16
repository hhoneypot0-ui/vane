package com.vane.browser.settings;
import java.util.*;
public final class Prefs {
  static final Map<String,String> M = new HashMap<>();
  public static String get(String k,String d){ return M.getOrDefault(k,d==null?"":d); }
  public static void put(String k,String v){ M.put(k,v); }
  public static boolean getBool(String k,boolean d){ return Boolean.parseBoolean(M.getOrDefault(k,String.valueOf(d))); }
}
