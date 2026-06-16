package com.qmsbrowser;

import android.content.Context;
import android.content.SharedPreferences;

final class CredentialStore {
    private static final String FILE = "secure_basic_auth_prefs_v2";
    private static final String LEGACY_FILE = "secure_basic_auth_prefs";

    private final SharedPreferences preferences;

    CredentialStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = SecurePreferences.create(appContext, FILE);
        // Older releases could write credentials without encryption if Android Keystore failed.
        appContext.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    boolean isAvailable() {
        return preferences != null;
    }

    String username(String key) {
        return preferences == null ? null : preferences.getString(key + "_user", null);
    }

    String password(String key) {
        return preferences == null ? null : preferences.getString(key + "_pass", null);
    }

    boolean save(String key, String username, String password) {
        return preferences != null && preferences.edit()
                .putString(key + "_user", username)
                .putString(key + "_pass", password)
                .commit();
    }

    void clear(String key) {
        if (preferences != null) {
            preferences.edit()
                    .remove(key + "_user")
                    .remove(key + "_pass")
                    .apply();
        }
    }

    boolean clearAll() {
        return preferences != null && preferences.edit().clear().commit();
    }
}
