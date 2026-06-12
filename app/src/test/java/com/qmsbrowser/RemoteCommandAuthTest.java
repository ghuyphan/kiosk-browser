package com.qmsbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteCommandAuthTest {
    @Test
    public void verifiesOnlyMatchingCommandSignatures() throws Exception {
        String signature = RemoteCommandAuth.sign(
                "secret",
                "command-id",
                123456L,
                "url",
                "https://example.com/"
        );
        assertEquals(
                "41cc4a2774067689450c52b9ce5d6b16fce2d31eea28b32652aa997322ead4a5",
                signature
        );

        assertTrue(RemoteCommandAuth.verify(
                "secret",
                "command-id",
                123456L,
                "url",
                "https://example.com/",
                signature
        ));
        assertFalse(RemoteCommandAuth.verify(
                "secret",
                "command-id",
                123456L,
                "url",
                "javascript:alert(1)",
                signature
        ));
    }

    @Test
    public void rejectsExpiredAndFutureCommands() {
        long now = 1_000_000L;
        assertTrue(RemoteCommandAuth.isFresh(now - 120000, now));
        assertFalse(RemoteCommandAuth.isFresh(now - 120001, now));
        assertFalse(RemoteCommandAuth.isFresh(now + 120001, now));
    }
}
