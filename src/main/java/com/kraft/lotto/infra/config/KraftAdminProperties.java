package com.kraft.lotto.infra.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.admin")
public record KraftAdminProperties(
        String apiToken,
        String apiTokens,
        String apiTokenHashes,
        String tokenHeader
) {
    private static final String DEFAULT_TOKEN_HEADER = "X-Kraft-Admin-Token";

    public String resolvedTokenHeader() {
        return normalize(tokenHeader, DEFAULT_TOKEN_HEADER);
    }

    public boolean hasApiToken() {
        return (apiToken != null && !apiToken.isBlank())
                || !resolvedApiTokens().isEmpty()
                || !resolvedApiTokenHashes().isEmpty();
    }

    public List<String> resolvedApiTokens() {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : splitCommaValues(apiTokens)) {
            tokens.add(token);
        }
        String singleToken = normalize(apiToken, null);
        if (singleToken != null) {
            tokens.add(singleToken);
        }
        return List.copyOf(tokens);
    }

    public List<AdminTokenHash> resolvedApiTokenHashes() {
        List<AdminTokenHash> result = new ArrayList<>();
        for (String entry : splitCommaValues(apiTokenHashes)) {
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            String id = entry.substring(0, separator).trim();
            String hashHex = entry.substring(separator + 1).trim().toLowerCase();
            if (id.isEmpty() || !isSha256Hex(hashHex)) {
                continue;
            }
            result.add(new AdminTokenHash(id, hashHex));
        }
        return List.copyOf(result);
    }

    private static List<String> splitCommaValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : raw.split(",")) {
            String normalized = normalize(token, null);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean digit = ch >= '0' && ch <= '9';
            boolean lowerHex = ch >= 'a' && ch <= 'f';
            if (!digit && !lowerHex) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    public record AdminTokenHash(String id, String hashHex) {
    }
}
