package com.meshchat.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.content.ContextCompat;

/** Keeps the mesh (advertise/scan/relay) alive in the background. */
public class MeshService extends Service implements MeshManager.Listener {
    private final Handler h = new Handler(Looper.getMainLooper());
    private MeshManager mesh;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (Prefs.joined(MeshService.this)) mesh.start();
            h.postDelayed(this, 10000);
        }
    };

    public static void ensure(Context c) {
        if (!Prefs.joined(c) || !MeshManager.hasPerms(c)) return;
        try { ContextCompat.startForegroundService(c, new Intent(c, MeshService.class)); } catch (Exception ignored) { }
    }
    public static void halt(Context c) { c.stopService(new Intent(c, MeshService.class)); }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        Notifs.channels(this);
        mesh = MeshManager.get(this);
        Notification n = Notifs.service(this, mesh.peers().size());
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifs.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(Notifs.SERVICE_ID, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        mesh.addListener(this);
        h.removeCallbacks(loop);
        h.post(loop);
        return START_STICKY;
    }

    @Override public void onPeers() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(Notifs.SERVICE_ID, Notifs.service(this, mesh.peers().size()));
    }
    @Override public void onChat() { }

    @Override public void onDestroy() {
        h.removeCallbacks(loop);
        if (mesh != null) mesh.removeListener(this);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
