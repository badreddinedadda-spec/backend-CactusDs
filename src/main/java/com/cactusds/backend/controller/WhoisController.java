package com.cactusds.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@Slf4j
@RequestMapping("/api/domaines")
public class WhoisController {

    private static final int WHOIS_PORT = 43;
    private static final String IANA_WHOIS_HOST = "whois.iana.org";
    private static final int SOCKET_TIMEOUT_MS = 8000;

    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");
    private static final Pattern REFERRAL_PATTERN =
            Pattern.compile("(?im)^\\s*(?:whois|refer)\\s*:\\s*(\\S+)\\s*$");

    @GetMapping("/whois")
    public ResponseEntity<?> whois(@RequestParam(required = false) String domaine) {
        if (domaine == null || domaine.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le paramètre 'domaine' est requis."));
        }

        String cleaned = domaine.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceFirst("^https?://", "").split("/")[0];

        if (!DOMAIN_PATTERN.matcher(cleaned).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Nom de domaine invalide.", "domaine", cleaned));
        }

        try {
            String raw = lookupRaw(cleaned);
            if (raw == null || raw.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aucune donnée Whois retournée pour ce domaine.", "domaine", cleaned));
            }
            return ResponseEntity.ok(Map.of("domaine", cleaned, "raw", raw));
        } catch (IOException e) {
            log.warn("Whois lookup failed for '{}': {}", cleaned, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "message", "Impossible de récupérer les informations Whois pour ce domaine. Réessayez dans un instant.",
                            "domaine", cleaned
                    ));
        }
    }

    private String lookupRaw(String domain) throws IOException {
        String tld = domain.substring(domain.lastIndexOf('.') + 1);

        String ianaResponse = query(IANA_WHOIS_HOST, tld);
        String registryHost = extractReferral(ianaResponse);
        if (registryHost == null) {
            return ianaResponse;
        }

        String registryResponse = query(registryHost, domain);
        String registrarHost = extractReferral(registryResponse);
        if (registrarHost == null || registrarHost.equalsIgnoreCase(registryHost)) {
            return registryResponse;
        }

        try {
            String registrarResponse = query(registrarHost, domain);
            return registryResponse + "\n\n" + registrarResponse;
        } catch (IOException e) {
            log.debug("Registrar whois server '{}' unreachable, keeping registry answer only", registrarHost);
            return registryResponse;
        }
    }

    private String extractReferral(String whoisResponse) {
        if (whoisResponse == null) return null;
        Matcher m = REFERRAL_PATTERN.matcher(whoisResponse);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private String query(String host, String queryText) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, WHOIS_PORT), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            try (OutputStream out = socket.getOutputStream();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out.write((queryText + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }
}