package com.meshchat.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

/** Nearby Devices screen; with extra mode=chats it doubles as the conversation list. */
public class NearbyActivity extends BaseActivity {
    private boolean chatsMode;
    private DeviceAdapter adapter;
    private RadarView radar;
    private TextView found, empty, discoveryLabel;
    private final SimpleDateFormat today = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat older = new SimpleDateFormat("dd MMM", Locale.getDefault());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_nearby);
        chatsMode = "chats".equals(getIntent().getStringExtra("mode"));
        setupTop(chatsMode ? "Chats" : "Nearby Devices");
        radar = findViewById(R.id.radar); found = findViewById(R.id.foundText);
        empty = findViewById(R.id.emptyText); discoveryLabel = findViewById(R.id.discoveryLabel);

        adapter = new DeviceAdapter(r -> startActivity(new Intent(this, ChatActivity.class).putExtra("id", r.id).putExtra("name", r.name)));
        RecyclerView rv = findViewById(R.id.list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        if (chatsMode) {
            radar.setVisibility(View.GONE); found.setVisibility(View.GONE);
            findViewById(R.id.btnDiscovery).setVisibility(View.GONE);
        } else {
            actionButton().setVisibility(View.VISIBLE);
            actionButton().setOnClickListener(v -> { mesh.stopDiscovery(); mesh.startDiscovery(); refresh(); });
            findViewById(R.id.btnDiscovery).setOnClickListener(v -> {
                if (mesh.isScanning()) mesh.stopDiscovery(); else mesh.startDiscovery();
                refresh();
            });
        }
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onPeers() { refresh(); }
    @Override public void onChat() { refresh(); }

    private String fmt(long ts) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(ts);
        boolean same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return (same ? today : older).format(new Date(ts));
    }

    private void refresh() {
        List<DeviceAdapter.Row> rows = new ArrayList<>();
        MeshStore st = mesh.store();
        if (chatsMode) {
            for (String[] c : st.conversations()) {
                if (Prefs.isBlocked(this, c[0])) continue;
                String name = (c[1] == null || c[1].isEmpty()) ? c[0] : c[1];
                rows.add(new DeviceAdapter.Row(c[0], name, c[2], mesh.peer(c[0]) != null, fmt(Long.parseLong(c[3])),
                        st.unread(c[0], Prefs.lastRead(this, c[0])), false));
            }
            empty.setText("No chats yet.\nOpen Nearby and tap Chat.");
        } else {
            List<Peer> peers = new ArrayList<>();
            Set<String> online = new HashSet<>();
            for (Peer p : mesh.peers()) if (!Prefs.isBlocked(this, p.id)) { peers.add(p); online.add(p.id); }
            for (Peer p : peers) rows.add(new DeviceAdapter.Row(p.id, p.label(), "Online • " + p.meters() + "m", true, "", 0, true));
            for (String[] c : st.contactList())
                if (!online.contains(c[0]) && !Prefs.isBlocked(this, c[0]))
                    rows.add(new DeviceAdapter.Row(c[0], c[1], "In mesh • out of range", false, "", 0, true));
            radar.setPeers(peers);
            if (!mesh.isRunning()) found.setText("Turn on Bluetooth & allow permissions");
            else if (!mesh.isScanning()) found.setText("Discovery stopped");
            else found.setText("Found " + peers.size() + " Mesh Chat Users\n(Within ~10m)");
            discoveryLabel.setText(mesh.isScanning() ? "Stop Discovery" : "Start Discovery");
            empty.setText("Searching for nearby users…");
        }
        adapter.set(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
