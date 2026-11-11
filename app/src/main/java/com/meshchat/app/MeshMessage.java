package com.meshchat.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MeshMessage {
    public static final int SENT = 0, RELAYED = 1, DELIVERED = 2;

    public String id, type = "MSG", from, fromName, to, toName, text = "", refId, via = "", lastHop = "", pk = "";
    public long ts, expiry;
    public int hops, status;
    public boolean incoming;
    public List<String> path = new ArrayList<>();

    public boolean isAck() { return "ACK".equals(type); }
    public boolean isKey() { return "KEY".equals(type); }
    public boolean expired() { return System.currentTimeMillis() > expiry; }

    public String pathString() {
        StringBuilder sb = new StringBuilder();
        for (String p : path) { if (sb.length() > 0) sb.append(" → "); sb.append(p); }
        return sb.toString();
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("i", id); o.put("t", type); o.put("f", from); o.put("fn", fromName);
            o.put("to", to); o.put("tn", toName); o.put("x", text); o.put("ts", ts); o.put("ex", expiry);
            o.put("h", hops); o.put("st", status); o.put("in", incoming);
            o.put("r", refId == null ? "" : refId); o.put("v", via == null ? "" : via); o.put("l", lastHop == null ? "" : lastHop); o.put("k", pk == null ? "" : pk);
            JSONArray a = new JSONArray();
            for (String p : path) a.put(p);
            o.put("p", a);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    public static MeshMessage fromJson(JSONObject o) {
        try {
            MeshMessage m = new MeshMessage();
            m.id = o.getString("i"); m.type = o.optString("t", "MSG"); m.from = o.optString("f"); m.fromName = o.optString("fn");
            m.to = o.optString("to"); m.toName = o.optString("tn"); m.text = o.optString("x"); m.ts = o.optLong("ts"); m.expiry = o.optLong("ex");
            m.hops = o.optInt("h"); m.status = o.optInt("st"); m.incoming = o.optBoolean("in");
            m.refId = o.optString("r"); m.via = o.optString("v"); m.lastHop = o.optString("l"); m.pk = o.optString("k");
            JSONArray a = o.optJSONArray("p");
            if (a != null) for (int i = 0; i < a.length(); i++) m.path.add(a.getString(i));
            return m;
        } catch (Exception e) { return null; }
    }

    public MeshMessage copy() { return fromJson(toJson()); }
}
