package com.meshchat.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity implements MeshManager.Listener {
    protected MeshManager mesh;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        mesh = MeshManager.get(this);
    }

    /** Wires the shared top bar (view_topbar). */
    protected void setupTop(String title) {
        TextView t = findViewById(R.id.topTitle);
        if (t != null) t.setText(title);
        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());
    }

    protected ImageView actionButton() { return findViewById(R.id.btnAction); }

    @Override protected void onResume() {
        super.onResume();
        mesh.addListener(this);
        if (Prefs.joined(this) && MeshManager.hasPerms(this)) { mesh.start(); MeshService.ensure(this); }
    }

    @Override protected void onPause() {
        super.onPause();
        mesh.removeListener(this);
    }

    @Override public void onPeers() { }
    @Override public void onChat() { }
}
