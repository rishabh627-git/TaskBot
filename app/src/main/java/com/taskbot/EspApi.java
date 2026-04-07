package com.taskbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * All HTTP communication with the ESP32.
 * Every method is blocking — call from a background thread.
 */
public class EspApi {

    public static final String BASE = "http://192.168.4.1";
    private static final int TIMEOUT = 4000; // ms

    // ── Result wrapper ────────────────────────────────────────
    public static class Result {
        public boolean ok;
        public String  body;
        public String  error;
        Result(boolean ok, String body, String error) {
            this.ok = ok; this.body = body; this.error = error;
        }
    }

    // ── GET /status  — quick ping ─────────────────────────────
    public static Result ping() {
        return get("/status");
    }

    // ── GET /tasks  — full task state ─────────────────────────
    public static Result getTasks() {
        return get("/tasks");
    }

    // ── POST /tasks  — push task list ─────────────────────────
    // names: list of task name strings
    public static Result postTasks(java.util.List<String> names) {
        try {
            JSONArray arr = new JSONArray();
            for (String n : names) {
                JSONObject o = new JSONObject();
                o.put("name", n);
                arr.put(o);
            }
            return post("/tasks", arr.toString());
        } catch (Exception e) {
            return new Result(false, null, e.getMessage());
        }
    }

    // ── Low-level GET ─────────────────────────────────────────
    private static Result get(String path) {
        try {
            HttpURLConnection c = open(BASE + path, "GET");
            c.connect();
            int code = c.getResponseCode();
            String body = readStream(code < 400
                    ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
            return new Result(code == 200, body, code == 200 ? null : "HTTP " + code);
        } catch (Exception e) {
            return new Result(false, null, e.getMessage());
        }
    }

    // ── Low-level POST ────────────────────────────────────────
    private static Result post(String path, String json) {
        try {
            HttpURLConnection c = open(BASE + path, "POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = json.getBytes("UTF-8");
            c.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            c.connect();
            OutputStream os = c.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
            int code = c.getResponseCode();
            String body = readStream(code < 400
                    ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
            return new Result(code == 200, body, code == 200 ? null : "HTTP " + code);
        } catch (Exception e) {
            return new Result(false, null, e.getMessage());
        }
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(TIMEOUT);
        c.setReadTimeout(TIMEOUT);
        return c;
    }

    private static String readStream(java.io.InputStream is) {
        if (is == null) return "";
        try (Scanner sc = new Scanner(is, "UTF-8")) {
            sc.useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        }
    }
}
