package com.qmsbrowser;

import java.net.URI;
final class SecurityPolicy {
    private SecurityPolicy() {
    }

    static boolean isAllowedWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || scheme == null) {
                return false;
            }
            return "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    static boolean isAllowedKioskHost(String requestedHost, String configuredHost, boolean restrict) {
        if (!restrict) {
            return true;
        }
        if (requestedHost == null || configuredHost == null) {
            return false;
        }
        String requested = requestedHost.toLowerCase(java.util.Locale.US);
        String configured = configuredHost.toLowerCase(java.util.Locale.US);
        return requested.equals(configured) || requested.endsWith("." + configured);
    }
}
