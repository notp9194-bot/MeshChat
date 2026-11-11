package com.meshchat.app;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_splash);
        pulse(findViewById(R.id.ring1), 1.06f);
        pulse(findViewById(R.id.ring2), 1.1f);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }, 1800);
    }

    private void pulse(View v, float to) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("scaleX", 1f, to), PropertyValuesHolder.ofFloat("scaleY", 1f, to));
        a.setDuration(900); a.setRepeatMode(ValueAnimator.REVERSE); a.setRepeatCount(ValueAnimator.INFINITE); a.start();
    }
}
