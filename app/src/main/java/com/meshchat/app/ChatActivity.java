package com.meshchat.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatActivity extends BaseActivity {
    private String peerId, peerName;
    private MessageAdapter adapter;
    private RecyclerView rv;
    private EditText input;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_chat);
        peerId = getIntent().getStringExtra("id");
        peerName = getIntent().getStringExtra("name");
        if (peerName == null || peerName.isEmpty()) peerName = peerId;

        ((TextView) findViewById(R.id.chatName)).setText(peerName);
        Ui.avatar((TextView) findViewById(R.id.avatar), peerName, peerId);
        status = findViewById(R.id.chatStatus);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        input = findViewById(R.id.input);

        findViewById(R.id.btnMore).setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add("Block user");
            pm.setOnMenuItemClickListener(i -> {
                Prefs.block(this, peerId, peerName);
                Toast.makeText(this, peerName + " blocked", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            });
            pm.show();
        });

        adapter = new MessageAdapter(m -> startActivity(new Intent(this, MessageDetailsActivity.class).putExtra("id", m.id)));
        rv = findViewById(R.id.list);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            MeshMessage r = mesh.sendText(peerId, peerName, t);
            if (r == null) {
                Toast.makeText(this, "Secure key not received yet. Stay near the device for a few seconds.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!mesh.isRunning()) Toast.makeText(this, "Bluetooth off or not joined – message queued", Toast.LENGTH_SHORT).show();
            input.setText("");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        mesh.activeChat = peerId;
        Notifs.cancel(this, peerId);
        Prefs.markRead(this, peerId);
        reload(); updateStatus();
    }

    @Override protected void onPause() {
        super.onPause();
        if (peerId.equals(mesh.activeChat)) mesh.activeChat = null;
        Prefs.markRead(this, peerId);
    }

    @Override public void onChat() { Prefs.markRead(this, peerId); Notifs.cancel(this, peerId); reload(); }
    @Override public void onPeers() { updateStatus(); }

    private void reload() {
        adapter.set(mesh.store().chatWith(peerId));
        if (adapter.getItemCount() > 0) rv.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void updateStatus() {
        Peer p = mesh.peer(peerId);
        if (p != null) {
            status.setText("● Online • " + p.meters() + "m • 🔒 encrypted");
            status.setTextColor(ContextCompat.getColor(this, R.color.green));
        } else {
            status.setText("Out of range • will deliver via mesh");
            status.setTextColor(ContextCompat.getColor(this, R.color.sub));
        }
    }
}
