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

/** Live mesh graph: dashed links flow toward peers, centre pulses. */
public class MeshView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final Drawable phone;
    private final int primary, ink;
    private List<Peer> peers = new ArrayList<>();
    private float t;
    private ValueAnimator anim;

    public MeshView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        primary = ContextCompat.getColor(c, R.color.primary);
        ink = ContextCompat.getColor(c, R.color.ink);
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(2 * d); line.setColor(ColorUtils.setAlphaComponent(primary, 140));
        txt.setTextAlign(Paint.Align.CENTER);
        phone = ContextCompat.getDrawable(c, R.drawable.ic_phone_android).mutate();
        phone.setTint(Color.WHITE);
    }

    public void setPeers(List<Peer> l) {
        peers = new ArrayList<>(l.subList(0, Math.min(6, l.size())));
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1600); anim.setRepeatCount(ValueAnimator.INFINITE); anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }
    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float R = Math.min(cx, cy) - 50 * d;
        int n = peers.size();
        float[] xs = new float[n], ys = new float[n];
        line.setPathEffect(new DashPathEffect(new float[]{8 * d, 8 * d}, -t * 16 * d));
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + Math.PI / n + 2 * Math.PI * i / n;
            xs[i] = cx + (float) Math.cos(ang) * R;
            ys[i] = cy + (float) Math.sin(ang) * R;
            cv.drawLine(cx, cy, xs[i], ys[i], line);
        }
        for (int i = 0; i < n; i++) {
            Peer p = peers.get(i);
            fill.setStyle(Paint.Style.FILL); fill.setColor(Ui.color(p.id));
            cv.drawCircle(xs[i], ys[i], 30 * d, fill);
            txt.setColor(Color.WHITE); txt.setTextSize(20 * d); txt.setFakeBoldText(true);
            String ch = p.label().isEmpty() ? "?" : p.label().substring(0, 1).toUpperCase();
            cv.drawText(ch, xs[i], ys[i] - (txt.ascent() + txt.descent()) / 2, txt);
            txt.setColor(ink); txt.setTextSize(12 * d); txt.setFakeBoldText(false);
            cv.drawText(p.label() + " • " + p.meters() + "m", xs[i], ys[i] + 46 * d, txt);
        }
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ColorUtils.setAlphaComponent(primary, (int) (90 * (1 - t))));
        cv.drawCircle(cx, cy, 38 * d + 22 * d * t, fill);
        fill.setColor(primary);
        cv.drawCircle(cx, cy, 38 * d, fill);
        phone.setBounds((int) (cx - 18 * d), (int) (cy - 18 * d), (int) (cx + 18 * d), (int) (cy + 18 * d));
        phone.draw(cv);
    }
}
