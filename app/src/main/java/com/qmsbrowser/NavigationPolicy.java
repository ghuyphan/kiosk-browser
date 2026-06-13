package com.qmsbrowser;

import android.content.Context;
import android.net.Uri;

public final class NavigationPolicy {
    private NavigationPolicy() {}

    public static boolean isAllowedKioskHost(Context context, Uri uri, String configuredStartUrl, boolean restrictToStartHost) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        
        // 1. Check IDP allowlist
        String allowlist = BrowserPreferences.get(context).getString(BrowserPreferences.IDP_ALLOWLIST, "");
        if (SecurityPolicy.isHostInAllowlist(host, allowlist)) {
            return true;
        }

        // 2. Check main kiosk restrictions
        Uri startUri = Uri.parse(configuredStartUrl);
        return SecurityPolicy.isAllowedKioskHost(
                host,
                startUri.getHost(),
                restrictToStartHost
        );
    }
}
