package com.careerai.backend.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates CORS_ALLOWED_ORIGINS (careerai.cors.allowed-origins) at startup: exact origins only
 * ("https://app.example.com", "http://localhost:5173"). Wildcards, "null", paths and non-HTTP
 * schemes are rejected so a misconfiguration fails fast instead of opening the API to any site.
 * A trailing slash is removed, because browsers send origins without one.
 */
final class CorsOrigins {

    private CorsOrigins() {
    }

    static List<String> parse(List<String> configured) {
        List<String> origins = new ArrayList<>();
        for (String raw : configured == null ? List.<String>of() : configured) {
            if (raw == null || raw.isBlank()) continue;
            String value = raw.trim();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            if (value.contains("*")) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list exact origins; wildcards are not allowed: " + raw);
            }
            URI uri;
            try {
                uri = new URI(value);
            } catch (URISyntaxException ex) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS contains an invalid origin: " + raw);
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean pathless = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null && uri.getRawFragment() == null;
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null || uri.getUserInfo() != null || !pathless) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS entries must look like https://host[:port]: " + raw);
            }
            String origin = scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            if (!origins.contains(origin)) origins.add(origin);
        }
        if (origins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list at least one frontend origin.");
        }
        return List.copyOf(origins);
    }
}
