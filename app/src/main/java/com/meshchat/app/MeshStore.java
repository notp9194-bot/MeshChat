package com.meshchat.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Simple persistent store: chat history + relay box (store & forward). Main-thread only. */
public class MeshStore {
    private final SharedPreferences sp;
    private final List<MeshMessage> chat = new ArrayList<>();
    private final List<MeshMessage> box = new ArrayList<>();
    private final Map<String, String[]> contacts = new LinkedHashMap<>(); // id -> {name, publicKey}

    public MeshStore(Context c) {
        sp = c.getSharedPreferences("mesh_store", Context.MODE_PRIVATE);
        load("chat", chat);
        load("box", box);
        try {
            JSONObject o = new JSONObject(sp.getString("contacts", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) { String k = it.next(); JSONArray a = o.getJSONArray(k); contacts.put(k, new String[]{a.getString(0), a.getString(1)}); }
        } catch (Exception ignored) { }
    }

    private void load(String key, List<MeshMessage> out) {
        try {
            JSONArray a = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                MeshMessage m = MeshMessage.fromJson(a.getJSONObject(i));
                if (m != null) out.add(m);
            }
        } catch (Exception ignored) { }
    }

    private void save() {
        JSONArray a = new JSONArray(), b = new JSONArray();
        for (MeshMessage m : chat) a.put(m.toJson());
        for (MeshMessage m : box) b.put(m.toJson());
        JSONObject co = new JSONObject();
        try { for (Map.Entry<String, String[]> e : contacts.entrySet()) co.put(e.getKey(), new JSONArray().put(e.getValue()[0]).put(e.getValue()[1])); } catch (Exception ignored) { }
        sp.edit().putString("chat", a.toString()).putString("box", b.toString()).putString("contacts", co.toString()).apply();
    }

    public void addChat(MeshMessage m) { chat.add(m); save(); }
    public MeshMessage chatById(String id) { for (MeshMessage m : chat) if (m.id.equals(id)) return m; return null; }

    public void setStatus(String id, int status, String via) {
        MeshMessage m = chatById(id);
        if (m == null) return;
        if (status > m.status) m.status = status;
        if (via != null && !via.isEmpty()) m.via = via;
        save();
    }

    public List<MeshMessage> chatWith(String peerId) {
        List<MeshMessage> r = new ArrayList<>();
        for (MeshMessage m : chat) if (peerId.equals(m.incoming ? m.from : m.to)) r.add(m);
        Collections.sort(r, (x, y) -> Long.compare(x.ts, y.ts));
        return r;
    }

    /** Returns {peerId, peerName, lastText, lastTs} newest first. */
    public List<String[]> conversations() {
        List<MeshMessage> all = new ArrayList<>(chat);
        Collections.sort(all, (x, y) -> Long.compare(y.ts, x.ts));
        Map<String, String[]> map = new LinkedHashMap<>();
        for (MeshMessage m : all) {
            String pid = m.incoming ? m.from : m.to;
            if (!map.containsKey(pid)) map.put(pid, new String[]{pid, m.incoming ? m.fromName : m.toName, m.text, String.valueOf(m.ts)});
        }
        return new ArrayList<>(map.values());
    }

    public List<MeshMessage> box() { return new ArrayList<>(box); }
    public int boxSize() { int n = 0; for (MeshMessage m : box) if (!m.isKey()) n++; return n; }
    public void addBox(MeshMessage m) { box.add(m); while (box.size() > 300) box.remove(0); save(); }
    public void removeKeysFrom(String from) {
        boolean ch = false;
        for (Iterator<MeshMessage> it = box.iterator(); it.hasNext(); ) { MeshMessage m = it.next(); if (m.isKey() && from.equals(m.from)) { it.remove(); ch = true; } }
        if (ch) save();
    }

    // ---- contacts (public keys learned through the mesh) ----
    public boolean putContact(String id, String name, String pk) {
        if (id == null || pk == null || pk.isEmpty() || !Crypto.idFor(pk).equals(id)) return false;
        String n = (name == null || name.isEmpty()) ? id : name;
        String[] old = contacts.get(id);
        if (old != null && old[0].equals(n) && old[1].equals(pk)) return true;
        contacts.put(id, new String[]{n, pk});
        save();
        return true;
    }
    public String contactKey(String id) { String[] c = contacts.get(id); return c == null ? null : c[1]; }
    public String contactName(String id) { String[] c = contacts.get(id); return c == null ? id : c[0]; }
    public List<String[]> contactList() {
        List<String[]> r = new ArrayList<>();
        for (Map.Entry<String, String[]> e : contacts.entrySet()) r.add(new String[]{e.getKey(), e.getValue()[0]});
        return r;
    }
    public int unread(String peerId, long lastRead) {
        int n = 0;
        for (MeshMessage m : chat) if (m.incoming && peerId.equals(m.from) && m.ts > lastRead) n++;
        return n;
    }
    public void removeBox(String id) {
        boolean ch = false;
        for (Iterator<MeshMessage> it = box.iterator(); it.hasNext(); ) if (it.next().id.equals(id)) { it.remove(); ch = true; }
        if (ch) save();
    }
    public void purge() {
        boolean ch = false;
        for (Iterator<MeshMessage> it = box.iterator(); it.hasNext(); ) if (it.next().expired()) { it.remove(); ch = true; }
        if (ch) save();
    }
    public void clear() { chat.clear(); box.clear(); save(); }
}
