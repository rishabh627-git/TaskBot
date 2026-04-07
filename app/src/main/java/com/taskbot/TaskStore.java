package com.taskbot;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads the task name list locally on the phone.
 * So tasks survive app restarts without needing the ESP32.
 */
public class TaskStore {

    private static final String PREFS = "taskbot";
    private static final String KEY   = "tasks";

    public static void save(Context ctx, List<String> tasks) {
        JSONArray arr = new JSONArray();
        for (String t : tasks) arr.put(t);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }

    public static List<String> load(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++)
                list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }
}
