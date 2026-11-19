package com.netscope.analysis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class WebUrl {
    private WebUrl() {
    }

    static String canonicalKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("URL must not be blank");
        }

        final URI uri;
        try {
            uri = new URI(value.trim()).normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid URL: " + value, exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only absolute HTTP and HTTPS URLs can be assessed");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("URL must have a host and must not contain user information");
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return scheme.toLowerCase(Locale.ROOT) + "://"
                + uri.getRawAuthority().toLowerCase(Locale.ROOT)
                + path
                + (query == null ? "" : "?" + query);
    }
}
