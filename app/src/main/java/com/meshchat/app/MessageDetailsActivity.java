package com.meshchat.app;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MessageDetailsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_message_details);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary));
        getWindow().getDecorView().setSystemUiVisibility(0);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOk).setOnClickListener(v -> finish());

        MeshMessage m = mesh.store().chatById(getIntent().getStringExtra("id"));
        if (m == null) { finish(); return; }

        String me = "You";
        String other = m.incoming ? m.fromName : m.toName;
        if (other == null || other.isEmpty()) other = m.incoming ? m.from : m.to;

        TextView title = findViewById(R.id.stTitle), sub = findViewById(R.id.stSub);
        ImageView icon = findViewById(R.id.stIcon);
        int green = ContextCompat.getColor(this, R.color.green), purple = ContextCompat.getColor(this, R.color.primary);
        String via;
        if (m.incoming) {
            title.setText("Received"); sub.setText("Message from " + other);
            icon.setColorFilter(green);
            via = m.path.isEmpty() ? "Direct" : m.pathString() + " → You";
        } else if (m.status == MeshMessage.DELIVERED) {
            title.setText("Delivered!"); sub.setText("Message reached " + other);
            icon.setColorFilter(green);
            via = (m.via == null || m.via.isEmpty()) ? "Direct" : m.via;
        } else if (m.status == MeshMessage.RELAYED) {
            title.setText("Relayed"); sub.setText("Handed to the mesh, waiting for\ndelivery confirmation");
            icon.setColorFilter(purple); via = "In progress…";
        } else {
            title.setText("Sent"); sub.setText("Waiting for a nearby device");
            icon.setColorFilter(purple); via = "Not relayed yet";
        }
        ((TextView) findViewById(R.id.dFrom)).setText(m.incoming ? other : me);
        ((TextView) findViewById(R.id.dTo)).setText(m.incoming ? me : other);
        ((TextView) findViewById(R.id.dTime)).setText(new SimpleDateFormat("h:mm a, dd MMM", Locale.getDefault()).format(new Date(m.ts)));
        ((TextView) findViewById(R.id.dVia)).setText(via);
        ((TextView) findViewById(R.id.dMsg)).setText(m.text);
    }
}
