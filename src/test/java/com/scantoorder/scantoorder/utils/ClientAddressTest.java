package com.scantoorder.scantoorder.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Where a request came from, in the order the brief sets: True-Client-IP, CF-Connecting-IP, the last X-Forwarded-For entry, the remote address.
class ClientAddressTest {

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void testThatTrueClientIpWinsOverAForgedForwardedFor() {
        MockHttpServletRequest request = request();
        request.addHeader("True-Client-IP", "203.0.113.7");
        request.addHeader("CF-Connecting-IP", "198.51.100.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 172.70.1.1");

        assertEquals("203.0.113.7", ClientAddress.of(request));
    }

    @Test
    void testThatTwoDifferentTrueClientIpValuesAreTwoAddresses() {
        MockHttpServletRequest first = request();
        first.addHeader("True-Client-IP", "203.0.113.7");
        MockHttpServletRequest second = request();
        second.addHeader("True-Client-IP", "203.0.113.8");
        // Both arrive through the same edge address, which must not make them one.
        first.addHeader("X-Forwarded-For", "172.70.1.1");
        second.addHeader("X-Forwarded-For", "172.70.1.1");

        assertEquals("203.0.113.7", ClientAddress.of(first));
        assertEquals("203.0.113.8", ClientAddress.of(second));
    }

    @Test
    void testThatCloudflareConnectingIpIsUsedWhenThereIsNoTrueClientIp() {
        MockHttpServletRequest request = request();
        request.addHeader("CF-Connecting-IP", "198.51.100.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 172.70.1.1");

        assertEquals("198.51.100.9", ClientAddress.of(request));
    }

    @Test
    void testThatTheLastForwardedForEntryIsUsedWhenNeitherCloudflareHeaderIsThere() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", " 6.6.6.6 ,  172.70.1.1 ");

        assertEquals("172.70.1.1", ClientAddress.of(request));
    }

    @Test
    void testThatTheRemoteAddressIsUsedWithNoHeaders() {
        assertEquals("10.0.0.1", ClientAddress.of(request()));
    }

    @Test
    void testThatBlankValuesAreIgnoredAndValuesAreTrimmed() {
        MockHttpServletRequest request = request();
        request.addHeader("True-Client-IP", "   ");
        request.addHeader("CF-Connecting-IP", "  198.51.100.9  ");
        assertEquals("198.51.100.9", ClientAddress.of(request));

        MockHttpServletRequest blanks = request();
        blanks.addHeader("True-Client-IP", "");
        blanks.addHeader("CF-Connecting-IP", " ");
        blanks.addHeader("X-Forwarded-For", "1.1.1.1, ,  ");
        assertEquals("1.1.1.1", ClientAddress.of(blanks));

        MockHttpServletRequest nothing = new MockHttpServletRequest();
        nothing.setRemoteAddr("  ");
        assertEquals("unknown", ClientAddress.of(nothing));
    }
}
