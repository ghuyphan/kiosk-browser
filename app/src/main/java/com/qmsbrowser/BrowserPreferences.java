package com.qmsbrowser;

import android.content.Context;
import android.content.SharedPreferences;

final class BrowserPreferences {
    static final String FILE = "browser_settings";
    static final String START_URL = "start_url";
    static final String SHOW_TOOLBAR = "show_toolbar";
    static final String DESKTOP_MODE = "desktop_mode";
    static final String KEEP_SCREEN_ON = "keep_screen_on";
    static final String FULLSCREEN = "fullscreen";

    static final String DEFAULT_START_URL = "https://www.google.com/";

    private BrowserPreferences() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

