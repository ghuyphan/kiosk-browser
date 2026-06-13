package com.qmsbrowser;

import android.util.Log;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.json.JSONObject;

public class RemoteControlClient {
    private static final String TAG = "RemoteControlClient";
    private final String topic;
    private final String statusTopic;
    private final String secret;
    private final Callback callback;
    private final Set<String> recentCommandIds = new LinkedHashSet<>();
    private Thread listenThread;
    private volatile boolean running;
    private volatile HttpURLConnection activeConnection;

    public interface Callback {
        void onCommand(String commandId, String action, String value);
    }

    public RemoteControlClient(String topic, String secret, Callback callback) {
        this.topic = topic;
        this.statusTopic = topic + "-status";
        this.secret = secret;
        this.callback = callback;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Log.d(TAG, "Starting authenticated remote-control client");
        listenThread = new Thread(this::listenLoop, "RemoteControlListenThread");
        listenThread.start();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
        Thread thread = listenThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
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
                activeConnection = conn;
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setReadTimeout(30000);
                conn.setConnectTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Log.d(TAG, "Remote-control connection established");
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
                activeConnection = null;
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    private void parseAndDispatch(String line) {
        if (line == null || line.trim().isEmpty()) return;
        try {
            JSONObject event = new JSONObject(line);
            if (!"message".equals(event.optString("event"))) {
                return;
            }
            JSONObject payload = new JSONObject(event.optString("message", "{}"));
            String commandId = payload.optString("id");
            long timestamp = payload.optLong("timestamp");
            String action = payload.optString("action", null);
            String value = payload.has("value") ? payload.optString("value", "") : "";
            String signature = payload.optString("signature");
            if (!RemoteCommandAuth.isFresh(timestamp, System.currentTimeMillis())
                    || commandId.isEmpty()
                    || action == null
                    || !RemoteCommandPolicy.isAllowed(action, value)
                    || !RemoteCommandAuth.verify(
                            secret,
                            commandId,
                            timestamp,
                            action,
                            value,
                            signature
                    )
                    || isReplay(commandId)) {
                Log.w(TAG, "Ignoring remote command with invalid authentication");
                return;
            }
            rememberCommand(commandId);
            if (!action.isEmpty()) {
                callback.onCommand(commandId, action, value);
            }
        } catch (Exception e) {
            Log.w(TAG, "Ignoring malformed remote-control event", e);
        }
    }

    public void sendEvent(String action, String value) {
        if (!running || action == null || value == null
                || value.length() > RemoteCommandPolicy.MAX_VALUE_LENGTH) {
            return;
        }
        new Thread(() -> publishEvent(action, value), "RemoteControlPublishThread").start();
    }

    private void publishEvent(String action, String value) {
        HttpURLConnection connection = null;
        try {
            String id = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            String signature = RemoteCommandAuth.sign(
                    secret,
                    id,
                    timestamp,
                    action,
                    value
            );
            JSONObject payload = new JSONObject();
            payload.put("version", 2);
            payload.put("id", id);
            payload.put("timestamp", timestamp);
            payload.put("action", action);
            payload.put("value", value);
            payload.put("signature", signature);

            connection = (HttpURLConnection) new URL(
                    "https://ntfy.sh/" + statusTopic
            ).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Log.w(TAG, "Status publish returned response code: " + responseCode);
            }
        } catch (Exception error) {
            if (running) {
                Log.w(TAG, "Failed to publish remote-control status", error);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isReplay(String commandId) {
        return recentCommandIds.contains(commandId);
    }

    private void rememberCommand(String commandId) {
        recentCommandIds.add(commandId);
        if (recentCommandIds.size() > 100) {
            String oldest = recentCommandIds.iterator().next();
            recentCommandIds.remove(oldest);
        }
    }
}
