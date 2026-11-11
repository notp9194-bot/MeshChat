package com.meshchat.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {
    public static class Row {
        String id, name, sub, time; boolean online, showChat; int unread;
        Row(String id, String name, String sub, boolean online, String time, int unread, boolean showChat) {
            this.id = id; this.name = name; this.sub = sub; this.online = online; this.time = time; this.unread = unread; this.showChat = showChat;
        }
    }
    public interface OnChat { void open(Row r); }

    private List<Row> rows = new ArrayList<>();
    private final OnChat cb;
    public DeviceAdapter(OnChat cb) { this.cb = cb; }
    public void set(List<Row> r) { rows = r; notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_device, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        final Row r = rows.get(i);
        h.name.setText(r.name);
        Ui.avatar(h.avatar, r.name, r.id);
        h.sub.setText(r.sub);
        h.sub.setTextColor(ContextCompat.getColor(h.itemView.getContext(), r.online ? R.color.green : R.color.sub));
        h.btn.setVisibility(r.showChat ? View.VISIBLE : View.GONE);
        h.time.setVisibility(r.time == null || r.time.isEmpty() ? View.GONE : View.VISIBLE);
        h.time.setText(r.time);
        h.badge.setVisibility(r.unread > 0 ? View.VISIBLE : View.GONE);
        h.badge.setText(String.valueOf(r.unread));
        View.OnClickListener c = v -> cb.open(r);
        h.btn.setOnClickListener(c);
        h.itemView.setOnClickListener(c);
    }
    @Override public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, sub, btn, time, badge;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.avatar); name = v.findViewById(R.id.name); sub = v.findViewById(R.id.sub);
            btn = v.findViewById(R.id.btnChat); time = v.findViewById(R.id.time); badge = v.findViewById(R.id.badge);
        }
    }
}
