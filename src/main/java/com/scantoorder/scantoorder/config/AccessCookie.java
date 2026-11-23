package com.scantoorder.scantoorder.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Builds every access_token cookie, so every place that signs someone in or out sets the same attributes. It is always HttpOnly and Secure.
 * SameSite comes from COOKIE_SAME_SITE ("None" while the site and the API are on different domains, "Lax" once they share platelyy.com),
 * and Domain from COOKIE_DOMAIN (empty means the cookie belongs to the API's own host only). Signing out clears the cookie with the same
 * attributes it was set with, because a browser only replaces a cookie whose domain and path match.
 */
@Component
public class AccessCookie {

    public static final String NAME = "access_token";
    private static final List<String> SAME_SITE = List.of("None", "Lax", "Strict");

    private final String sameSite;
    private final String domain;

    public AccessCookie(@Value("${app.cookie.same-site:None}") String sameSite, @Value("${app.cookie.domain:}") String domain) {
        this.sameSite = normalise(sameSite);
        this.domain = domain == null ? "" : domain.trim();
    }

    @PostConstruct
    void checkSettings() {
        if (!SAME_SITE.contains(sameSite)) {
            throw new IllegalStateException("COOKIE_SAME_SITE must be None, Lax or Strict.");
        }
    }

    private static String normalise(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "None";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    public ResponseCookie set(String token, Duration lifetime) {
        return builder(token).maxAge(lifetime).build();
    }

    public ResponseCookie clear() {
        return builder("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite(sameSite).path("/");
        if (!domain.isEmpty()) {
            cookie.domain(domain);
        }
        return cookie;
    }
}
