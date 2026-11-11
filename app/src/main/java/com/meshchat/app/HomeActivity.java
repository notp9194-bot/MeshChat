package com.meshchat.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeActivity extends BaseActivity {
    private TextView btSub, btBadge, meshSub;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        btSub = findViewById(R.id.btSub); btBadge = findViewById(R.id.btBadge); meshSub = findViewById(R.id.meshSub);

        findViewById(R.id.cardBt).setOnClickListener(v -> {
            try {
                if (!mesh.isBluetoothOn()) startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                else startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); // apps cannot switch BT off themselves
            } catch (Exception ignored) { }
        });
        findViewById(R.id.cardMesh).setOnClickListener(v -> {
            if (!Prefs.joined(this)) { Prefs.setJoined(this, true); mesh.start(); MeshService.ensure(this); refresh(); }
            else startActivity(new Intent(this, MeshStatusActivity.class));
        });
        findViewById(R.id.tileMesh).setOnClickListener(v -> startActivity(new Intent(this, MeshStatusActivity.class)));
        findViewById(R.id.tileNearby).setOnClickListener(v -> startActivity(new Intent(this, NearbyActivity.class)));
        findViewById(R.id.btnPeople).setOnClickListener(v -> startActivity(new Intent(this, NearbyActivity.class)));
        findViewById(R.id.tileChats).setOnClickListener(v -> startActivity(new Intent(this, NearbyActivity.class).putExtra("mode", "chats")));
        findViewById(R.id.tileSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnGear).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        requestAll();
        askNameIfNeeded();
    }

    private void requestAll() {
        List<String> need = new ArrayList<>();
        List<String> all = new ArrayList<>(Arrays.asList(MeshManager.neededPerms()));
        if (Build.VERSION.SDK_INT >= 33) all.add(android.Manifest.permission.POST_NOTIFICATIONS);
        for (String p : all) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty()) ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 11);
    }

    private void askNameIfNeeded() {
        if (!Prefs.name(this).isEmpty()) return;
        final EditText et = new EditText(this);
        et.setHint("Your name");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Welcome to Mesh Chat")
                .setMessage("Nearby users will see this name.")
                .setView(et).setCancelable(false)
                .setPositiveButton("Continue", (d, w) -> {
                    String n = et.getText().toString().trim();
                    Prefs.setName(this, n.isEmpty() ? "User" : n);
                    mesh.refreshAdvert();
                }).show();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (Prefs.joined(this) && MeshManager.hasPerms(this)) { mesh.start(); MeshService.ensure(this); }
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onPeers() { refresh(); }

    private void refresh() {
        boolean on = mesh.isBluetoothOn();
        btBadge.setText(on ? "On" : "Off");
        btBadge.setBackgroundResource(on ? R.drawable.bg_badge_on : R.drawable.bg_badge_off);
        btSub.setText(on ? "Ready to connect • tap for settings" : "Tap to turn on");
        if (!Prefs.joined(this)) meshSub.setText("Not joined • tap to join");
        else if (!MeshManager.hasPerms(this)) meshSub.setText("Permission needed");
        else meshSub.setText(mesh.peers().size() + " Devices Nearby");
    }
}
