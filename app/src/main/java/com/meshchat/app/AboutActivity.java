package com.meshchat.app;

import android.os.Bundle;

public class AboutActivity extends BaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_about);
        setupTop("About");
    }
}
