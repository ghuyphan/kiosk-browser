package com.qmsbrowser;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

final class RemoteControllerLink {
    private static final String COMMIT =
            "982c71694cc2a7b35a2109d31a3ea43f42c97c4c";
    private static final String BASE_URL =
            "https://raw.githack.com/ghuyphan/kiosk-browser/"
                    + COMMIT
                    + "/remote.html";

    private RemoteControllerLink() {
    }

    static String create(String topic, String secret) {
        return BASE_URL
                + "#topic=" + encode(topic)
                + "&secret=" + encode(secret);
    }

    static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            URI expected = URI.create(BASE_URL);
            if (uri.getRawFragment() == null
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || !expected.getHost().equalsIgnoreCase(uri.getHost())
                    || !expected.getPath().equals(uri.getPath())) {
                return false;
            }
            String topic = null;
            String secret = null;
            for (String part : uri.getRawFragment().split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                String key = decode(pair[0]);
                String decoded = decode(pair[1]);
                if ("topic".equals(key)) {
                    topic = decoded;
                } else if ("secret".equals(key)) {
                    secret = decoded;
                }
            }
            return topic != null && topic.length() >= 32
                    && secret != null && secret.length() >= 43;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
