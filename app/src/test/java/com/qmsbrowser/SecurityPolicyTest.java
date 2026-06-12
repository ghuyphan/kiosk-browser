package com.qmsbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityPolicyTest {
    @Test
    public void acceptsHttpsOnly() {
        assertTrue(SecurityPolicy.isAllowedWebUrl("https://example.com/path"));
        assertFalse(SecurityPolicy.isAllowedWebUrl("http://192.168.1.20:8080/"));
        assertFalse(SecurityPolicy.isAllowedWebUrl("http://localhost:3000/"));
        assertFalse(SecurityPolicy.isAllowedWebUrl("http://example.com/"));
        assertFalse(SecurityPolicy.isAllowedWebUrl("javascript:alert(1)"));
        assertFalse(SecurityPolicy.isAllowedWebUrl("file:///sdcard/secret"));
    }

    @Test
    public void restrictsToConfiguredHostAndSubdomains() {
        assertTrue(SecurityPolicy.isAllowedKioskHost("qms.example.com", "qms.example.com", true));
        assertTrue(SecurityPolicy.isAllowedKioskHost("display.qms.example.com", "qms.example.com", true));
        assertFalse(SecurityPolicy.isAllowedKioskHost("evilqms.example.com", "qms.example.com", true));
        assertFalse(SecurityPolicy.isAllowedKioskHost("example.net", "qms.example.com", true));
        assertTrue(SecurityPolicy.isAllowedKioskHost("example.net", "qms.example.com", false));
    }
}
