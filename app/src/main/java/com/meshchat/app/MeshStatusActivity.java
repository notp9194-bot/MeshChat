package com.meshchat.app;

import android.os.Bundle;
import android.widget.TextView;
import java.util.List;

public class MeshStatusActivity extends BaseActivity {
    private MeshView view;
    private TextView conn, relayTitle, relaySub;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_mesh_status);
        setupTop("Mesh Status");
        view = findViewById(R.id.meshView); conn = findViewById(R.id.connText);
        relayTitle = findViewById(R.id.relayTitle); relaySub = findViewById(R.id.relaySub);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onPeers() { refresh(); }

    private void refresh() {
        List<Peer> peers = mesh.peers();
        view.setPeers(peers);
        conn.setText(peers.size() + " Devices Connected");
        boolean relay = Prefs.relay(this) && Prefs.joined(this);
        relayTitle.setText(relay ? "Mesh Relay Active" : "Mesh Relay Off");
        int stored = mesh.store().boxSize();
        relaySub.setText(relay ? "Messages can flow through nearby devices\nStored for relay: " + stored
                : "You are not relaying messages for others");
    }
}
