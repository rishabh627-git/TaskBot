package com.taskbot;

/**
 * One task record saved to history on a specific date.
 */
public class HistoryEntry {
    public String  date;       // "2025-01-15"
    public String  name;
    public boolean done;
    public long    totalSecs;

    public HistoryEntry(String date, String name, boolean done, long totalSecs) {
        this.date      = date;
        this.name      = name;
        this.done      = done;
        this.totalSecs = totalSecs;
    }

    /** Format totalSecs as HH:MM:SS */
    public String hms() {
        long s = totalSecs;
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}