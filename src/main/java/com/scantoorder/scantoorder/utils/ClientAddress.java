package com.scantoorder.scantoorder.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address a request came from, for counting and never for showing. Behind the host's proxy the last X-Forwarded-For entry is the one
 * the proxy itself saw; the earlier ones are whatever the caller wrote.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
