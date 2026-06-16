package com.qmsbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RemoteControllerLinkTest {
    private static final String TOPIC = "qms-kiosk-abcdefghijklmnopqrstuvwxyz123456";
    private static final String SECRET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    @Test
    public void acceptsOnlyPinnedControllerLinks() {
        String link = RemoteControllerLink.create(TOPIC, SECRET);
        assertTrue(RemoteControllerLink.isValid(link));
        assertFalse(RemoteControllerLink.isValid(
                link.replace("982c71694cc2a7b35a2109d31a3ea43f42c97c4c", "main")
        ));
        assertFalse(RemoteControllerLink.isValid(
                link.replace("raw.githack.com", "example.com")
        ));
        assertFalse(RemoteControllerLink.isValid(
                link.replace("https://", "http://")
        ));
    }
}
