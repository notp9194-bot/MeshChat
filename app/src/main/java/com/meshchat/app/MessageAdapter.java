package com.meshchat.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
    public interface OnClick { void on(MeshMessage m); }

    private List<MeshMessage> list = new ArrayList<>();
    private final OnClick cb;
    private final SimpleDateFormat fmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
    public MessageAdapter(OnClick cb) { this.cb = cb; }
    public void set(List<MeshMessage> l) { list = l; notifyDataSetChanged(); }

    @Override public int getItemViewType(int i) { return list.get(i).incoming ? 1 : 0; }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(t == 1 ? R.layout.item_msg_recv : R.layout.item_msg_sent, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final MeshMessage m = list.get(i);
        h.msg.setText(m.text);
        String time = fmt.format(new Date(m.ts));
        if (m.incoming) h.meta.setText(time);
        else {
            String st = m.status == MeshMessage.DELIVERED ? "✓✓ Delivered" : m.status == MeshMessage.RELAYED ? "✓ Relayed" : "✓ Sent";
            h.meta.setText(time + "  " + st);
        }
        h.bubble.setOnClickListener(v -> cb.on(m));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView msg, meta; View bubble;
        VH(View v) { super(v); msg = v.findViewById(R.id.msg); meta = v.findViewById(R.id.meta); bubble = v.findViewById(R.id.bubble); }
    }
}
