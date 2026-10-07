package com.cactusds.backend.comon.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentUrlPolicyTest {

    private final AgentUrlPolicy policy = new AgentUrlPolicy();

    @Test
    void acceptsPrivateLoopbackAndPublicHostsAndNormalisesThem() {
        assertEquals("http://10.0.0.12:9100", policy.normalize("http://10.0.0.12:9100"));
        assertEquals("http://10.0.0.12:9100", policy.normalize("  http://10.0.0.12:9100/  "));
        assertEquals("https://203.0.113.7", policy.normalize("HTTPS://203.0.113.7"));
        assertEquals("http://127.0.0.1:9100", policy.normalize("http://127.0.0.1:9100"));
        assertEquals("http://[::1]:9100", policy.normalize("http://[::1]:9100"));
    }

    @Test
    void refusesMissingOrMalformedUrls() {
        assertThrows(IllegalArgumentException.class, () -> policy.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("not a url"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://"));
    }

    @Test
    void refusesOtherSchemes() {
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("ftp://10.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("10.0.0.1:9100"));
    }

    @Test
    void refusesCredentialsPathsQueriesAndFragments() {
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://admin:secret@10.0.0.1:9100"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://10.0.0.1:9100/metrics"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://10.0.0.1:9100?x=1"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://10.0.0.1:9100#top"));
    }

    @Test
    void refusesWildcardLinkLocalAndMulticastAddresses_includingTheCloudMetadataEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://169.254.169.254"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://0.0.0.0:9100"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://224.0.0.1:9100"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalize("http://[fe80::1]:9100"));
    }
}