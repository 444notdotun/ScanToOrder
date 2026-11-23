package com.scantoorder.scantoorder.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address a request came from, for counting and never for showing. The site sits behind Cloudflare, so the first choice is what
 * Cloudflare says: True-Client-IP, which it sets itself and overwrites if the caller sends one. X-Forwarded-For only ever gets added to,
 * and its last entry can be a shared edge address, so it comes after the two Cloudflare headers. Blank values are skipped.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String address = clean(request.getHeader("True-Client-IP"));
        if (address == null) {
            address = clean(request.getHeader("CF-Connecting-IP"));
        }
        if (address == null) {
            address = lastOf(request.getHeader("X-Forwarded-For"));
        }
        if (address == null) {
            address = clean(request.getRemoteAddr());
        }
        return address == null ? "unknown" : address;
    }

    private static String lastOf(String forwarded) {
        if (forwarded == null) {
            return null;
        }
        String[] parts = forwarded.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String entry = clean(parts[i]);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
