package com.qmsbrowser;

import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.json.JSONObject;

final class RemoteCommandSender {
    interface Callback {
        void onResult(boolean success, int responseCode);
    }

    private final String topic;
    private final String secret;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile long rateLimitedUntil;

    RemoteCommandSender(String topic, String secret) {
        this.topic = topic;
        this.secret = secret;
    }

    boolean isRateLimited() {
        return System.currentTimeMillis() < rateLimitedUntil;
    }

    void send(String action, String value, Callback callback) {
        if (!RemoteCommandPolicy.isAllowed(action, value)) {
            deliver(callback, false, 400);
            return;
        }
        if (isRateLimited()) {
            deliver(callback, false, 429);
            return;
        }
        new Thread(() -> publish(action, value, callback), "RemoteCommandSendThread").start();
    }

    private void publish(String action, String value, Callback callback) {
        HttpURLConnection connection = null;
        int responseCode = 0;
        try {
            String id = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            JSONObject payload = new JSONObject();
            payload.put("version", 2);
            payload.put("id", id);
            payload.put("timestamp", timestamp);
            payload.put("action", action);
            payload.put("value", value);
            payload.put(
                    "signature",
                    RemoteCommandAuth.sign(secret, id, timestamp, action, value)
            );

            connection = (HttpURLConnection) new URL(
                    "https://ntfy.sh/" + topic
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
            responseCode = connection.getResponseCode();
            if (responseCode == 429) {
                rateLimitedUntil = System.currentTimeMillis() + 15000;
            }
            deliver(callback, responseCode >= 200 && responseCode < 300, responseCode);
        } catch (Exception error) {
            deliver(callback, false, responseCode);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void deliver(Callback callback, boolean success, int responseCode) {
        if (callback != null) {
            mainHandler.post(() -> callback.onResult(success, responseCode));
        }
    }
}
