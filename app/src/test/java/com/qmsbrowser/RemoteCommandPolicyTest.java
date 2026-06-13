package com.qmsbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteCommandPolicyTest {
    @Test
    public void acceptsKnownPracticalControlCommands() {
        assertTrue(RemoteCommandPolicy.isAllowed("pointer_move", "{\"dx\":5,\"dy\":-2}"));
        assertTrue(RemoteCommandPolicy.isAllowed("tap_at", "{\"x\":0.5,\"y\":0.25}"));
        assertTrue(RemoteCommandPolicy.isAllowed("key", "enter"));
        assertTrue(RemoteCommandPolicy.isAllowed("page_status", ""));
    }

    @Test
    public void rejectsUnknownNullAndOversizedCommands() {
        assertFalse(RemoteCommandPolicy.isAllowed("run_javascript", "alert(1)"));
        assertFalse(RemoteCommandPolicy.isAllowed(null, ""));
        assertFalse(RemoteCommandPolicy.isAllowed("type", null));
        assertFalse(RemoteCommandPolicy.isAllowed(
                "type",
                repeat("x", RemoteCommandPolicy.MAX_VALUE_LENGTH + 1)
        ));
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
