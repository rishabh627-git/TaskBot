package com.taskbot;

/**
 * Represents one task as returned by GET /tasks from the ESP32.
 */
public class TaskItem {
    public int     id;
    public String  name;
    public boolean done;
    public boolean running;
    public long    totalMs;   // accumulated ms on ESP32
    public String  hms;       // "HH:MM:SS" pre-formatted by ESP32

    // For the live ticker: when we received this snapshot
    public long    snapshotAt; // System.currentTimeMillis() at poll time

    public TaskItem(int id, String name, boolean done,
                    boolean running, long totalMs, String hms) {
        this.id         = id;
        this.name       = name;
        this.done       = done;
        this.running    = running;
        this.totalMs    = totalMs;
        this.hms        = hms;
        this.snapshotAt = System.currentTimeMillis();
    }

    /**
     * Returns live elapsed ms: adds time since snapshot if running.
     */
    public long liveTotalMs() {
        if (!running) return totalMs;
        return totalMs + (System.currentTimeMillis() - snapshotAt);
    }

    /** Formats liveTotalMs() as HH:MM:SS */
    public String liveHms() {
        long s = liveTotalMs() / 1000;
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
