package com.meshchat.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

public class Ui {
    public static final int[] PAL = {0xFF6C3CE1, 0xFF8E4EC6, 0xFF3E63DD, 0xFFD6409F, 0xFF12A594, 0xFFE08A1E};

    public static int color(String id) { return PAL[(id.hashCode() & 0x7fffffff) % PAL.length]; }

    public static void avatar(TextView t, String name, String id) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color(id));
        t.setBackground(g);
        t.setTextColor(Color.WHITE);
        t.setText(name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
    }
}
