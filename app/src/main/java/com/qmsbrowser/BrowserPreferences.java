package com.qmsbrowser;

import android.content.Context;
import android.content.SharedPreferences;

final class BrowserPreferences {
    static final String FILE = "browser_settings";
    static final String START_URL = "start_url";
    static final String DESKTOP_MODE = "desktop_mode";
    static final String KEEP_SCREEN_ON = "keep_screen_on";
    static final String FULLSCREEN = "fullscreen";
    static final String TOOLBAR_HIDDEN = "toolbar_hidden";
    static final String SCREEN_PINNING = "screen_pinning";
    static final String RESTRICT_TO_START_HOST = "restrict_to_start_host";
    static final String BLOCK_EXTERNAL_APPS = "block_external_apps";
    static final String PREVENT_SCREENSHOTS = "prevent_screenshots";
    static final String SAVE_PASSWORDS = "save_passwords";

    static final String DEFAULT_START_URL = "https://www.google.com/";

    private BrowserPreferences() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
