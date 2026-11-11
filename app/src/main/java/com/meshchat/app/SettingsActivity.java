package com.meshchat.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends BaseActivity {
    private SwitchCompat swRelay, swStore;
    private TextView relaySub, leaveLabel;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        setupTop("Settings");
        swRelay = findViewById(R.id.swRelay); swStore = findViewById(R.id.swStore);
        relaySub = findViewById(R.id.rowRelaySub); leaveLabel = findViewById(R.id.leaveLabel);

        swRelay.setChecked(Prefs.relay(this));
        swStore.setChecked(Prefs.storeForward(this));
        updateRelayText();
        swRelay.setOnCheckedChangeListener((v, on) -> { Prefs.setRelay(this, on); updateRelayText(); });
        swStore.setOnCheckedChangeListener((v, on) -> Prefs.setStoreForward(this, on));

        findViewById(R.id.rowPrivacy).setOnClickListener(v -> privacyDialog());
        findViewById(R.id.rowAbout).setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btnLeave).setOnClickListener(v -> toggleJoin());
        updateLeave();
    }

    private void updateRelayText() { relaySub.setText(Prefs.relay(this) ? "Automatic" : "Off"); }
    private void updateLeave() { leaveLabel.setText(Prefs.joined(this) ? "Leave Mesh Network" : "Join Mesh Network"); }

    private void toggleJoin() {
        if (Prefs.joined(this)) { mesh.leave(); MeshService.halt(this); Toast.makeText(this, "Left mesh network", Toast.LENGTH_SHORT).show(); }
        else { Prefs.setJoined(this, true); mesh.start(); MeshService.ensure(this); Toast.makeText(this, "Joined mesh network", Toast.LENGTH_SHORT).show(); }
        updateLeave();
    }

    private void privacyDialog() {
        int nb = Prefs.blocked(this).size();
        new AlertDialog.Builder(this).setTitle("Privacy & Security")
                .setItems(new String[]{"My ID & encryption", "Change my name", "Blocked users (" + nb + ")", "Clear all chats & stored messages"}, (d, i) -> {
                    if (i == 0) new AlertDialog.Builder(this).setTitle("My ID")
                            .setMessage(Prefs.userId(this) + "\n\nMessages are end-to-end encrypted (ECDH P-256 + AES-256-GCM). Relay phones carry your messages but cannot read them.")
                            .setPositiveButton("OK", null).show();
                    else if (i == 1) nameDialog();
                    else if (i == 2) blockedDialog();
                    else new AlertDialog.Builder(this).setMessage("Delete all chats and stored relay messages?")
                            .setPositiveButton("Delete", (x, y) -> { mesh.store().clear(); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show(); })
                            .setNegativeButton("Cancel", null).show();
                }).show();
    }

    private void blockedDialog() {
        final java.util.List<String> ids = new java.util.ArrayList<>(), names = new java.util.ArrayList<>();
        for (String s : Prefs.blocked(this)) { int k = s.indexOf('|'); ids.add(s.substring(0, k)); names.add(s.substring(k + 1)); }
        if (ids.isEmpty()) { Toast.makeText(this, "No blocked users", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Tap to unblock")
                .setItems(names.toArray(new String[0]), (d, i) -> {
                    Prefs.unblock(this, ids.get(i));
                    Toast.makeText(this, names.get(i) + " unblocked", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void nameDialog() {
        final EditText et = new EditText(this);
        et.setText(Prefs.name(this)); et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        new AlertDialog.Builder(this).setTitle("Your name").setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) { Prefs.setName(this, n); mesh.refreshAdvert(); }
                }).setNegativeButton("Cancel", null).show();
    }
}
