package com.qmsbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NavigationPolicyTest {

    @Test
    public void testIsHostInAllowlist() {
        String allowlist = "login.microsoftonline.com, accounts.google.com; okta.com";

        // Exact matches
        assertTrue(SecurityPolicy.isHostInAllowlist("login.microsoftonline.com", allowlist));
        assertTrue(SecurityPolicy.isHostInAllowlist("accounts.google.com", allowlist));
        assertTrue(SecurityPolicy.isHostInAllowlist("okta.com", allowlist));

        // Subdomain matches
        assertTrue(SecurityPolicy.isHostInAllowlist("sub.okta.com", allowlist));
        assertTrue(SecurityPolicy.isHostInAllowlist("my.accounts.google.com", allowlist));

        // Case insensitivity
        assertTrue(SecurityPolicy.isHostInAllowlist("LOGIN.MICROSOFTONLINE.COM", allowlist));
        assertTrue(SecurityPolicy.isHostInAllowlist("Sub.Okta.Com", allowlist));

        // Non-matches
        assertFalse(SecurityPolicy.isHostInAllowlist("microsoft.com", allowlist));
        assertFalse(SecurityPolicy.isHostInAllowlist("google.com", allowlist));
        assertFalse(SecurityPolicy.isHostInAllowlist("evilokta.com", allowlist));
        assertFalse(SecurityPolicy.isHostInAllowlist(null, allowlist));
        assertFalse(SecurityPolicy.isHostInAllowlist("okta.com", ""));
        assertFalse(SecurityPolicy.isHostInAllowlist("okta.com", null));
    }
}
