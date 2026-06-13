package com.qmsbrowser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class RemoteCommandPolicy {
    static final int MAX_VALUE_LENGTH = 8192;
    private static final Set<String> ALLOWED_ACTIONS = new HashSet<>(Arrays.asList(
            "hello",
            "back",
            "forward",
            "reload",
            "home",
            "url",
            "type",
            "key",
            "pointer_move",
            "tap_at",
            "tap",
            "double_tap",
            "long_press",
            "scroll",
            "page_status"
    ));

    private RemoteCommandPolicy() {
    }

    static boolean isAllowed(String action, String value) {
        return action != null
                && ALLOWED_ACTIONS.contains(action)
                && value != null
                && value.length() <= MAX_VALUE_LENGTH;
    }
}
