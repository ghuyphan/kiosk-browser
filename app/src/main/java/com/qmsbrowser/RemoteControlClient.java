package com.qmsbrowser;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RemoteControlClient {
    private static final String TAG = "RemoteControlClient";
    private final String topic;
    private final Callback callback;
    private Thread listenThread;
    private volatile boolean running;

    public interface Callback {
        void onCommand(String action, String value);
    }

    public RemoteControlClient(String topic, Callback callback) {
        this.topic = topic;
        this.callback = callback;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Log.d(TAG, "Starting RemoteControlClient on topic: " + topic);
        listenThread = new Thread(this::listenLoop, "RemoteControlListenThread");
        listenThread.start();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (listenThread != null) {
            listenThread.interrupt();
            listenThread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void listenLoop() {
        while (running) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://ntfy.sh/" + topic + "/json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setReadTimeout(0); // Keep connection alive indefinitely
                conn.setConnectTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Log.d(TAG, "Connection successful to ntfy.sh topic: " + topic);
                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        parseAndDispatch(line);
                    }
                } else {
                    Log.w(TAG, "Server returned response code: " + responseCode);
                    // Wait before retrying on server error
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                if (running) {
                    Log.w(TAG, "Connection error: " + e.getMessage() + ", retrying in 5 seconds...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    private void parseAndDispatch(String line) {
        if (line == null || line.trim().isEmpty()) return;
        try {
            // A simple JSON parser to extract the "message" field
            // Line format from ntfy is: {"id":"...","time":...,"event":"message","topic":"...","message":"..."}
            int msgIndex = line.indexOf("\"message\":\"");
            if (msgIndex == -1) return;
            int start = msgIndex + 11;
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = start; i < line.length(); i++) {
                char c = line.charAt(i);
                if (escaped) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 'r') sb.append('\r');
                    else if (c == 't') sb.append('\t');
                    else sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }

            String messagePayload = sb.toString();
            Log.d(TAG, "Received message payload: " + messagePayload);
            // messagePayload is a JSON string like: {"action":"back"} or {"action":"url","value":"..."}
            String action = getJsonField(messagePayload, "action");
            String value = getJsonField(messagePayload, "value");
            if (action != null) {
                callback.onCommand(action, value);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing line: " + line, e);
        }
    }

    private String getJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int index = json.indexOf(key);
        if (index == -1) {
            return null;
        }
        int start = index + key.length();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
