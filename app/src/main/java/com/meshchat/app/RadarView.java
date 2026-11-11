package com.meshchat.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.List;

public class RadarView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final Drawable phone;
    private final int primary, surface;
    private List<Peer> peers = new ArrayList<>();
    private float t;
    private ValueAnimator anim;

    public RadarView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        primary = ContextCompat.getColor(c, R.color.primary);
        surface = ContextCompat.getColor(c, R.color.surface);
        phone = ContextCompat.getDrawable(c, R.drawable.ic_phone_android).mutate();
        phone.setTint(primary);
    }

    public void setPeers(List<Peer> l) { peers = new ArrayList<>(l); invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2200); anim.setRepeatCount(ValueAnimator.INFINITE); anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }
    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, maxR = Math.min(cx, cy) - 4 * d;
        float[] f = {1f, 0.66f, 0.33f};
        int[] alpha = {16, 26, 42};
        for (int i = 0; i < 3; i++) {
            p.setStyle(Paint.Style.FILL); p.setColor(ColorUtils.setAlphaComponent(primary, alpha[i]));
            cv.drawCircle(cx, cy, maxR * f[i], p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * d); p.setColor(ColorUtils.setAlphaComponent(primary, 60));
            cv.drawCircle(cx, cy, maxR * f[i], p);
        }
        // expanding scan wave
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * d);
        p.setColor(ColorUtils.setAlphaComponent(primary, (int) (120 * (1 - t))));
        cv.drawCircle(cx, cy, 22 * d + (maxR - 22 * d) * t, p);

        p.setStyle(Paint.Style.FILL); p.setColor(surface);
        cv.drawCircle(cx, cy, 22 * d, p);
        phone.setBounds((int) (cx - 13 * d), (int) (cy - 13 * d), (int) (cx + 13 * d), (int) (cy + 13 * d));
        phone.draw(cv);
        for (Peer pr : peers) {
            double ang = ((pr.id.hashCode() & 0xffff) / 65535.0) * Math.PI * 2;
            float r = maxR * Math.max(0.3f, Math.min(0.92f, pr.meters() / 12f));
            float x = cx + (float) Math.cos(ang) * r, y = cy + (float) Math.sin(ang) * r;
            int col = Ui.color(pr.id);
            p.setStyle(Paint.Style.FILL); p.setColor(ColorUtils.setAlphaComponent(col, 60));
            cv.drawCircle(x, y, 11 * d, p);
            p.setColor(col);
            cv.drawCircle(x, y, 7 * d, p);
        }
    }
}
