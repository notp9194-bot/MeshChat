package com.meshchat.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * BLE mesh engine.
 * - Advertises service UUID + (userId,name) in scan response
 * - Scans for other Mesh Chat devices
 * - GATT server receives messages, GATT client writes messages to peers
 * - Flood routing with dedupe (message id), hop limit, expiry, store & forward, ACK for delivery
 * All state is touched on the main thread only.
 */
@SuppressLint("MissingPermission")
public class MeshManager {
    public interface Listener { void onPeers(); void onChat(); }

    public static final UUID SERVICE = UUID.fromString("7a1c0000-5b0e-4f3a-9c11-3d2e6f0a9b01");
    public static final UUID CHAR = UUID.fromString("7a1c0001-5b0e-4f3a-9c11-3d2e6f0a9b01");
    private static final int COMPANY = 0xFFFF, MAX_HOPS = 8;
    private static final long TTL = 24L * 3600 * 1000;

    private static MeshManager inst;
    public static synchronized MeshManager get(Context c) {
        if (inst == null) inst = new MeshManager(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MeshStore store;
    private final Map<String, Peer> peers = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<String, Set<String>> sentTo = new HashMap<>();
    private final Map<String, ByteArrayOutputStream> rx = new HashMap<>();
    private final Set<String> seen = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) { return size() > 3000; }
    });

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer server;
    private boolean running, scanning;
    private long lastNotify, lastAnnounce;
    private String lastKeyId;
    /** Peer id of the chat screen currently open (suppresses notifications). */
    public String activeChat;

    private MeshManager(Context c) { ctx = c; store = new MeshStore(c); }

    // ---------------- public API ----------------
    public MeshStore store() { return store; }
    public boolean isRunning() { return running; }
    public boolean isScanning() { return scanning; }
    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public String me() { return Prefs.userId(ctx); }

    public static boolean hasPerms(Context c) {
        String[] p = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for (String s : p) if (ContextCompat.checkSelfPermission(c, s) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    public static String[] neededPerms() {
        return Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public boolean isBluetoothOn() {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter a = bm == null ? null : bm.getAdapter();
        return a != null && a.isEnabled();
    }

    public List<Peer> peers() {
        List<Peer> l = new ArrayList<>(peers.values());
        Collections.sort(l, (a, b) -> Integer.compare(b.rssi, a.rssi));
        return l;
    }
    public Peer peer(String id) { return peers.get(id); }

    public boolean start() {
        if (running) return true;
        if (!hasPerms(ctx)) return false;
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;
        running = true;
        startServer(bm);
        startAdvertising();
        startDiscovery();
        announce();
        main.postDelayed(tick, 5000);
        notifyPeers();
        return true;
    }

    public void stop() {
        running = false;
        main.removeCallbacks(tick);
        stopDiscovery();
        try { if (advertiser != null) advertiser.stopAdvertising(advCb); } catch (Exception ignored) { }
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        synchronized (queue) { queue.clear(); }
        peers.clear();
        notifyPeers();
    }

    public void leave() { Prefs.setJoined(ctx, false); stop(); }

    public void startDiscovery() {
        if (!running || scanning || adapter == null) return;
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;
        try {
            List<ScanFilter> f = Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE)).build());
            ScanSettings s = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(f, s, scanCb);
            scanning = true;
        } catch (Exception ignored) { }
        notifyPeers();
    }

    public void stopDiscovery() {
        try { if (scanner != null && scanning) scanner.stopScan(scanCb); } catch (Exception ignored) { }
        scanning = false;
        notifyPeers();
    }

    public void refreshAdvert() {
        if (!running) return;
        announce();
        if (advertiser == null) return;
        try { advertiser.stopAdvertising(advCb); } catch (Exception ignored) { }
        startAdvertising();
    }

    public boolean hasKey(String id) { return store.contactKey(id) != null; }

    /** Encrypts and sends. Returns null if the receiver's public key is not known yet. */
    public MeshMessage sendText(String toId, String toName, String text) {
        String peerKey = store.contactKey(toId);
        if (peerKey == null) return null;
        String enc;
        try { enc = Crypto.get(ctx).encrypt(peerKey, text); } catch (Exception e) { return null; }
        long now = System.currentTimeMillis();
        MeshMessage m = new MeshMessage();
        m.id = UUID.randomUUID().toString();
        m.from = me(); m.fromName = displayName();
        m.to = toId; m.toName = toName; m.text = text;
        m.ts = now; m.expiry = now + TTL; m.status = MeshMessage.SENT; m.incoming = false;
        seen.add(m.id);
        store.addChat(m);                       // local copy: plain text
        MeshMessage w = m.copy();               // wire copy: encrypted
        w.text = enc; w.pk = Crypto.get(ctx).pubB64;
        store.addBox(w);
        notifyChat();
        forwardMsg(w);
        return m;
    }

    private String displayName() { String n = Prefs.name(ctx); return n.isEmpty() ? me() : n; }

    /** Floods our public key so anyone in the mesh can send us encrypted messages. */
    private void announce() {
        long now = System.currentTimeMillis();
        if (lastKeyId != null) store.removeBox(lastKeyId);
        MeshMessage k = new MeshMessage();
        k.id = UUID.randomUUID().toString(); k.type = "KEY";
        k.from = me(); k.fromName = displayName(); k.to = "*"; k.toName = "";
        k.text = Crypto.get(ctx).pubB64; k.ts = now; k.expiry = now + 3600_000L;
        seen.add(k.id);
        lastKeyId = k.id; lastAnnounce = now;
        store.addBox(k.copy());
        forwardMsg(k);
    }

    // ---------------- advertising / scanning / server ----------------
    private final AdvertiseCallback advCb = new AdvertiseCallback() { };

    private byte[] payload() {
        byte[] id = me().getBytes(StandardCharsets.UTF_8);
        String n = Prefs.name(ctx);
        if (n.isEmpty()) n = "User";
        while (n.getBytes(StandardCharsets.UTF_8).length > 14) n = n.substring(0, n.length() - 1);
        byte[] nb = n.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[id.length + nb.length];
        System.arraycopy(id, 0, out, 0, id.length);
        System.arraycopy(nb, 0, out, id.length, nb.length);
        return out;
    }

    private void startAdvertising() {
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return;
        AdvertiseSettings s = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true).setTimeout(0).build();
        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(new ParcelUuid(SERVICE)).build();
        AdvertiseData resp = new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(COMPANY, payload()).build();
        try { advertiser.startAdvertising(s, data, resp, advCb); } catch (Exception ignored) { }
    }

    private final ScanCallback scanCb = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) { handleScan(r); }
        @Override public void onBatchScanResults(List<ScanResult> l) { for (ScanResult r : l) handleScan(r); }
    };

    private void handleScan(final ScanResult r) {
        main.post(() -> {
            if (!running) return;
            ScanRecord rec = r.getScanRecord();
            if (rec == null) return;
            byte[] md = rec.getManufacturerSpecificData(COMPANY);
            if (md == null || md.length < 7) return;
            String id = new String(md, 0, 6, StandardCharsets.UTF_8);
            if (id.equals(me())) return;
            String name = new String(md, 6, md.length - 6, StandardCharsets.UTF_8);
            long now = System.currentTimeMillis();
            Peer p = peers.get(id);
            boolean isNew = p == null;
            if (isNew) { p = new Peer(); p.id = id; p.rssi = r.getRssi(); peers.put(id, p); }
            else p.rssi = (int) (p.rssi * 0.6 + r.getRssi() * 0.4);
            p.name = name; p.address = r.getDevice().getAddress(); p.lastSeen = now;
            if (isNew || now - lastNotify > 2000) { lastNotify = now; notifyPeers(); }
            if (isNew) forwardAll();
        });
    }

    private void startServer(BluetoothManager bm) {
        server = bm.openGattServer(ctx, serverCb);
        if (server == null) return;
        BluetoothGattService svc = new BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        svc.addCharacteristic(new BluetoothGattCharacteristic(CHAR, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        server.addService(svc);
    }

    private final BluetoothGattServerCallback serverCb = new BluetoothGattServerCallback() {
        @Override public void onCharacteristicWriteRequest(BluetoothDevice d, int reqId, BluetoothGattCharacteristic c,
                                                           boolean prep, boolean resp, int off, byte[] val) {
            final byte[] v = val == null ? new byte[0] : val;
            try { if (resp && server != null) server.sendResponse(d, reqId, BluetoothGatt.GATT_SUCCESS, off, null); } catch (Exception ignored) { }
            if (!CHAR.equals(c.getUuid())) return;
            final String a = d.getAddress();
            main.post(() -> {
                ByteArrayOutputStream b = rx.get(a);
                if (b == null) { b = new ByteArrayOutputStream(); rx.put(a, b); }
                b.write(v, 0, v.length);
                if (v.length > 0 && v[v.length - 1] == '\n') { rx.remove(a); handleIncoming(b.toByteArray()); }
            });
        }
        @Override public void onConnectionStateChange(BluetoothDevice d, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) { final String a = d.getAddress(); main.post(() -> rx.remove(a)); }
        }
    };

    // ---------------- routing ----------------
    private void handleIncoming(byte[] raw) {
        MeshMessage m;
        try { m = MeshMessage.fromJson(new JSONObject(new String(raw, StandardCharsets.UTF_8).trim())); } catch (Exception e) { return; }
        if (m == null || m.id == null || seen.contains(m.id) || m.expired()) return;
        seen.add(m.id);
        String me = me();
        if (m.lastHop != null && !m.lastHop.isEmpty()) sent(m.id).add(m.lastHop);
        sent(m.id).add(m.from);

        if (m.isKey()) {
            if (!store.putContact(m.from, m.fromName, m.text)) return;   // id must match key hash
            notifyPeers();
        } else if (m.isAck()) {
            store.removeBox(m.refId);
        }

        if (me.equals(m.to)) {
            if (m.isAck()) { store.setStatus(m.refId, MeshMessage.DELIVERED, m.text); notifyChat(); }
            else deliver(m);
            return;
        }
        // Not for me (or broadcast): relay
        if (!Prefs.relay(ctx) || m.hops >= MAX_HOPS) return;
        m.hops++;
        m.path.add(displayName());
        m.incoming = false;
        if (m.isKey()) store.removeKeysFrom(m.from);
        if (Prefs.storeForward(ctx)) store.addBox(m.copy());
        forwardMsg(m);
    }

    private void deliver(MeshMessage m) {
        if (Prefs.isBlocked(ctx, m.from)) return;
        String pk = m.pk;
        if (pk == null || pk.isEmpty() || !Crypto.idFor(pk).equals(m.from)) return;
        String plain;
        try { plain = Crypto.get(ctx).decrypt(pk, m.text); } catch (Exception e) { return; }
        store.putContact(m.from, m.fromName, pk);
        m.text = plain; m.pk = "";
        m.incoming = true; m.status = MeshMessage.DELIVERED; m.ts = System.currentTimeMillis();
        store.addChat(m);
        sendAck(m);
        notifyChat();
        if (!m.from.equals(activeChat)) Notifs.message(ctx, m.from, store.contactName(m.from), plain);
    }

    private void sendAck(MeshMessage orig) {
        long now = System.currentTimeMillis();
        String myName = displayName();
        String p = orig.pathString();
        MeshMessage a = new MeshMessage();
        a.id = UUID.randomUUID().toString(); a.type = "ACK"; a.refId = orig.id;
        a.from = me(); a.fromName = myName; a.to = orig.from; a.toName = orig.fromName;
        a.text = p.isEmpty() ? myName : p + " → " + myName;
        a.ts = now; a.expiry = now + TTL;
        seen.add(a.id);
        store.addBox(a.copy());
        forwardMsg(a);
    }

    private Set<String> sent(String id) {
        Set<String> s = sentTo.get(id);
        if (s == null) { s = new HashSet<>(); sentTo.put(id, s); }
        return s;
    }

    private void forwardAll() { for (MeshMessage m : store.box()) forwardMsg(m); }

    private void forwardMsg(final MeshMessage m) {
        if (m.expired() || !running) return;
        for (Peer p : new ArrayList<>(peers.values())) {
            final Set<String> s = sent(m.id);
            if (s.contains(p.id) || p.address == null) continue;
            s.add(p.id);
            MeshMessage out = m.copy();
            out.lastHop = me(); out.incoming = false;
            byte[] data = (out.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8);
            final String pid = p.id, mid = m.id;
            enqueue(p.address, data, () -> onSendOk(mid), () -> sent(mid).remove(pid));
        }
    }

    private void onSendOk(String mid) {
        MeshMessage c = store.chatById(mid);
        if (c != null && !c.incoming && c.status == MeshMessage.SENT) {
            store.setStatus(mid, MeshMessage.RELAYED, null);
            notifyChat();
        }
    }

    // ---------------- periodic tick ----------------
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!isBluetoothOn()) { stop(); return; }
            long now = System.currentTimeMillis();
            boolean ch = false;
            for (Iterator<Peer> it = peers.values().iterator(); it.hasNext(); ) {
                if (now - it.next().lastSeen > 25000) { it.remove(); ch = true; }
            }
            if (ch) notifyPeers();
            store.purge();
            if (now - lastAnnounce > 600000) announce();
            forwardAll();
            main.postDelayed(this, 6000);
        }
    };

    // ---------------- GATT write queue (one connection at a time) ----------------
    private static class Job {
        String addr; byte[] data; Runnable onOk, onFail, timeout;
        BluetoothGatt gatt; BluetoothGattCharacteristic ch;
        int offset, chunk = 20; boolean done, finished;
    }
    private final LinkedList<Job> queue = new LinkedList<>();
    private boolean busy;

    private void enqueue(String addr, byte[] data, Runnable ok, Runnable fail) {
        Job j = new Job(); j.addr = addr; j.data = data; j.onOk = ok; j.onFail = fail;
        synchronized (queue) { queue.add(j); }
        pump();
    }

    private void pump() {
        final Job j;
        synchronized (queue) {
            if (busy || queue.isEmpty()) return;
            busy = true; j = queue.poll();
        }
        try {
            BluetoothDevice d = adapter.getRemoteDevice(j.addr);
            j.timeout = () -> finishJob(j, false);
            main.postDelayed(j.timeout, 12000);
            j.gatt = d.connectGatt(ctx, false, new GattCb(j), BluetoothDevice.TRANSPORT_LE);
        } catch (Exception e) { finishJob(j, false); }
    }

    private void finishJob(Job j, boolean ok) {
        if (j.finished) return;
        j.finished = true;
        if (j.timeout != null) main.removeCallbacks(j.timeout);
        if (j.gatt != null) {
            try { j.gatt.disconnect(); } catch (Exception ignored) { }
            try { j.gatt.close(); } catch (Exception ignored) { }
        }
        if (ok) { if (j.onOk != null) j.onOk.run(); } else if (j.onFail != null) j.onFail.run();
        synchronized (queue) { busy = false; }
        main.postDelayed(this::pump, 250);
    }

    private void writeNext(BluetoothGatt g, Job j) {
        int n = Math.min(j.chunk, j.data.length - j.offset);
        byte[] part = Arrays.copyOfRange(j.data, j.offset, j.offset + n);
        j.offset += n;
        j.ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        j.ch.setValue(part);
        if (!g.writeCharacteristic(j.ch)) finishJob(j, false);
    }

    private class GattCb extends BluetoothGattCallback {
        final Job j;
        GattCb(Job j) { this.j = j; }

        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int ns) {
            if (ns == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (!g.requestMtu(185)) g.discoverServices();
            } else if (ns == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                main.post(() -> finishJob(j, j.done));
            }
        }
        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) j.chunk = Math.max(20, mtu - 3);
            g.discoverServices();
        }
        @Override public void onServicesDiscovered(final BluetoothGatt g, int status) {
            BluetoothGattService s = g.getService(SERVICE);
            final BluetoothGattCharacteristic c = s == null ? null : s.getCharacteristic(CHAR);
            if (c == null) { main.post(() -> finishJob(j, false)); return; }
            j.ch = c;
            main.post(() -> writeNext(g, j));
        }
        @Override public void onCharacteristicWrite(final BluetoothGatt g, BluetoothGattCharacteristic c, final int status) {
            main.post(() -> {
                if (status != BluetoothGatt.GATT_SUCCESS) { finishJob(j, false); return; }
                if (j.offset >= j.data.length) { j.done = true; finishJob(j, true); }
                else writeNext(g, j);
            });
        }
    }

    // ---------------- notify ----------------
    private void notifyPeers() { main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onPeers(); }); }
    private void notifyChat() { main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onChat(); }); }
}
