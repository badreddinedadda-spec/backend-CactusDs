package com.cactusds.backend.comon.monitoring;

import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

    @Component
    public class AgentUrlPolicy {

        public String normalize(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("Agent URL is required.");
            }
            URI uri;
            try {
                uri = new URI(raw.trim());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Agent URL is not a valid URL.");
            }
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Agent URL must start with http:// or https://.");
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Agent URL must not contain credentials.");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("Agent URL must not contain a query or a fragment.");
            }
            String path = uri.getRawPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                throw new IllegalArgumentException("Agent URL must not contain a path: the gateway appends /metrics itself.");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Agent URL must contain a host.");
            }
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("The agent host cannot be resolved.");
            }
            for (InetAddress a : addresses) {
                if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
                    throw new IllegalArgumentException("Agent URL points to a forbidden address (wildcard, link-local or multicast).");
                }
            }
            int port = uri.getPort();
            return scheme.toLowerCase() + "://" + host.toLowerCase() + (port == -1 ? "" : ":" + port);
        }
}
