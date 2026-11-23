package com.scantoorder.scantoorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

// Every sign-in cookie comes from one place, and signing out clears it with the attributes it was set with.
class AccessCookieTest {

    @Test
    void testThatTheDefaultsAreTodaysSettings() {
        ResponseCookie cookie = new AccessCookie("None", "").set("token-value", Duration.ofHours(8));

        String header = cookie.toString();
        assertTrue(header.startsWith("access_token=token-value;"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=None"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("Max-Age=28800"));
        assertFalse(header.contains("Domain="), "no domain means the cookie belongs to the API's own host");
    }

    @Test
    void testThatSameSiteAndDomainComeFromSettingsAndTheCookieIsStillHttpOnlyAndSecure() {
        AccessCookie cookies = new AccessCookie("lax", " .platelyy.com ");

        String header = cookies.set("t", Duration.ofHours(4)).toString();

        assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Domain=.platelyy.com"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("Max-Age=14400"));
    }

    @Test
    void testThatSigningOutClearsTheCookieWithTheSameAttributesItWasSetWith() {
        for (AccessCookie cookies : new AccessCookie[]{new AccessCookie("None", ""), new AccessCookie("Lax", ".platelyy.com")}) {
            ResponseCookie set = cookies.set("t", Duration.ofHours(8));
            ResponseCookie cleared = cookies.clear();

            assertEquals(set.getDomain(), cleared.getDomain());
            assertEquals(set.getPath(), cleared.getPath());
            assertEquals(set.getSameSite(), cleared.getSameSite());
            assertEquals(set.isSecure(), cleared.isSecure());
            assertEquals(set.isHttpOnly(), cleared.isHttpOnly());
            assertEquals("", cleared.getValue());
            assertEquals(Duration.ZERO, cleared.getMaxAge());
            assertTrue(cleared.toString().startsWith("access_token=;"));
        }
    }

    @Test
    void testThatAnEmptySettingMeansTodaysDefaultAndANonsenseOneStopsTheApp() {
        assertTrue(new AccessCookie("", "").set("t", Duration.ofHours(1)).toString().contains("SameSite=None"));
        assertDoesNotThrow(() -> new AccessCookie("Strict", "").checkSettings());
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> new AccessCookie("sometimes", "").checkSettings());
        assertTrue(refused.getMessage().contains("COOKIE_SAME_SITE"));
    }
}
