package com.meshchat.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class Prefs {
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("mesh_prefs", Context.MODE_PRIVATE); }

    public static String userId(Context c) { return Crypto.get(c).id; }
    public static String name(Context c) { return sp(c).getString("name", ""); }
    public static void setName(Context c, String n) { sp(c).edit().putString("name", n).apply(); }
    public static boolean relay(Context c) { return sp(c).getBoolean("relay", true); }
    public static void setRelay(Context c, boolean v) { sp(c).edit().putBoolean("relay", v).apply(); }
    public static boolean storeForward(Context c) { return sp(c).getBoolean("sf", true); }
    public static void setStoreForward(Context c, boolean v) { sp(c).edit().putBoolean("sf", v).apply(); }
    public static boolean joined(Context c) { return sp(c).getBoolean("joined", true); }
    public static void setJoined(Context c, boolean v) { sp(c).edit().putBoolean("joined", v).apply(); }

    public static long lastRead(Context c, String peer) { return sp(c).getLong("read_" + peer, 0); }
    public static void markRead(Context c, String peer) { sp(c).edit().putLong("read_" + peer, System.currentTimeMillis()).apply(); }

    public static Set<String> blocked(Context c) { return new HashSet<>(sp(c).getStringSet("blocked", new HashSet<String>())); }
    public static boolean isBlocked(Context c, String id) {
        for (String s : blocked(c)) if (s.startsWith(id + "|")) return true;
        return false;
    }
    public static void block(Context c, String id, String name) {
        Set<String> s = blocked(c); s.add(id + "|" + name);
        sp(c).edit().putStringSet("blocked", s).apply();
    }
    public static void unblock(Context c, String id) {
        Set<String> s = blocked(c);
        for (String x : new HashSet<>(s)) if (x.startsWith(id + "|")) s.remove(x);
        sp(c).edit().putStringSet("blocked", s).apply();
    }
}
