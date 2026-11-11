package com.meshchat.app;

public class Peer {
    public String id, name, address;
    public int rssi;
    public long lastSeen;

    /** Rough distance estimate in metres from RSSI. */
    public int meters() {
        double d = Math.pow(10, (-59.0 - rssi) / (10 * 2.5));
        return (int) Math.max(1, Math.round(d));
    }
    public String label() { return (name == null || name.isEmpty()) ? id : name; }
}
