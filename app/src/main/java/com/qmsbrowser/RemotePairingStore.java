package com.qmsbrowser;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

final class RemotePairingStore {
    private static final String FILE = "secure_remote_pairing";
    private static final String TOPIC = "topic";
    private static final String SECRET = "secret";
    private static final String ENABLED = "enabled";

    static final class Pairing {
        final String topic;
        final String secret;
        final boolean enabled;
        final boolean persistent;

        Pairing(String topic, String secret, boolean enabled, boolean persistent) {
            this.topic = topic;
            this.secret = secret;
            this.enabled = enabled;
            this.persistent = persistent;
        }
    }

    private final SharedPreferences preferences;

    RemotePairingStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = SecurePreferences.create(appContext, FILE);
        BrowserPreferences.get(appContext).edit()
                .remove("remote_control_topic")
                .remove("remote_control_secret")
                .remove("remote_control_enabled")
                .apply();
    }

    Pairing loadOrCreate() {
        if (preferences == null) {
            return new Pairing(newTopic(), randomToken(32), false, false);
        }
        String topic = preferences.getString(TOPIC, null);
        String secret = preferences.getString(SECRET, null);
        if (topic == null || topic.length() < 32 || secret == null || secret.length() < 43) {
            topic = newTopic();
            secret = randomToken(32);
            preferences.edit()
                    .putString(TOPIC, topic)
                    .putString(SECRET, secret)
                    .putBoolean(ENABLED, false)
                    .commit();
        }
        return new Pairing(topic, secret, preferences.getBoolean(ENABLED, false), true);
    }

    boolean setEnabled(boolean enabled) {
        return preferences != null && preferences.edit().putBoolean(ENABLED, enabled).commit();
    }

    Pairing rotate(boolean enabled) {
        String topic = newTopic();
        String secret = randomToken(32);
        boolean persistent = preferences != null && preferences.edit()
                .putString(TOPIC, topic)
                .putString(SECRET, secret)
                .putBoolean(ENABLED, enabled)
                .commit();
        return new Pairing(topic, secret, persistent && enabled, persistent);
    }

    private static String newTopic() {
        return "qms-kiosk-" + randomToken(24);
    }

    private static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        return android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE
                        | android.util.Base64.NO_PADDING
                        | android.util.Base64.NO_WRAP
        );
    }
}
