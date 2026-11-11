package com.meshchat.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class Notifs {
    public static final String CH_SERVICE = "mesh_service", CH_MSG = "mesh_msg";
    public static final int SERVICE_ID = 1;

    public static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_SERVICE, "Mesh service", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH));
    }

    public static Notification service(Context c, int peers) {
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, HomeActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(c, CH_SERVICE)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentTitle("Mesh Chat is active")
                .setContentText(peers + " devices nearby • relay " + (Prefs.relay(c) ? "on" : "off"))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
    }

    @SuppressLint("MissingPermission")
    public static void message(Context c, String peerId, String name, String text) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;
        channels(c);
        Intent i = new Intent(c, ChatActivity.class).putExtra("id", peerId).putExtra("name", name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, peerId.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(c, CH_MSG)
                .setSmallIcon(R.drawable.ic_chat).setContentTitle(name).setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL).setContentIntent(pi).build();
        nm.notify(peerId.hashCode(), n);
    }

    public static void cancel(Context c, String peerId) { NotificationManagerCompat.from(c).cancel(peerId.hashCode()); }
}
