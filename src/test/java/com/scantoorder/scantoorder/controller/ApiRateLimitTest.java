package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.ApiRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// The whole-API limit: counted per client address a minute at a time, with the stream, the health check and the webhook left out.
@SpringBootTest(properties = "app.rate.api-per-min=5")
class ApiRateLimitTest {
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    private ApiRateLimitFilter filter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        // A burst must not straddle the turn of a minute, when the count starts again.
        long into = System.currentTimeMillis() % 60_000;
        if (into > 52_000) {
            Thread.sleep(60_000 - into + 200);
        }
    }

    private static String address() {
        return "198.51.100." + NEXT.getAndIncrement();
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder api(String ip) {
        return get("/api/v1/tenant").header("True-Client-IP", ip);
    }

    @Test
    void testThatTheRequestOverTheLimitGetsA429WithRetryAfterAndPlainWords() throws Exception {
        String ip = address();
        for (int i = 0; i < 5; i++) {
            assertEquals(401, status(api(ip)), "within the limit the request reaches the app");
        }

        MvcResult over = mvc.perform(api(ip)).andReturn();

        assertEquals(429, over.getResponse().getStatus());
        assertEquals("{\"status\":\"ERROR\",\"message\":\"Too many requests. Try again in a minute.\"}", over.getResponse().getContentAsString());
        int wait = Integer.parseInt(over.getResponse().getHeader("Retry-After"));
        assertTrue(wait >= 1 && wait <= 60, "Retry-After is the seconds left in the minute: " + wait);
    }

    @Test
    void testThatTwoClientsBehindTheSameEdgeAddressAreCountedApart() throws Exception {
        String first = address();
        String second = address();
        for (int i = 0; i < 6; i++) {
            status(api(first).header("X-Forwarded-For", "172.70.1.1"));
        }

        assertEquals(429, status(api(first).header("X-Forwarded-For", "172.70.1.1")));
        assertEquals(401, status(api(second).header("X-Forwarded-For", "172.70.1.1")), "the second client is not held back by the first");
    }

    @Test
    void testThatAForgedExtraAddressCannotDodgeTheLimit() throws Exception {
        String real = address();
        // Each request writes a different made-up first entry; the proxy's own last entry stays the same.
        for (int i = 0; i < 5; i++) {
            assertEquals(401, status(get("/api/v1/tenant").header("X-Forwarded-For", "6.6.6." + i + ", " + real)));
        }

        assertEquals(429, status(get("/api/v1/tenant").header("X-Forwarded-For", "7.7.7.7, " + real)));
    }

    @Test
    void testThatTrueClientIpWinsOverAForgedForwardedFor() throws Exception {
        String real = address();
        for (int i = 0; i < 5; i++) {
            assertEquals(401, status(api(real).header("X-Forwarded-For", "9.9.9." + i)));
        }

        assertEquals(429, status(api(real).header("X-Forwarded-For", "10.10.10.10")));
    }

    @Test
    void testThatTheStreamTheHealthCheckAndTheWebhookAreNeverLimited() throws Exception {
        String ip = address();
        for (int i = 0; i < 20; i++) {
            assertNotEquals(429, status(get("/api/v1/stream/staff").header("True-Client-IP", ip)));
            assertNotEquals(429, status(get("/api/v1/stream/guest").header("True-Client-IP", ip)));
            assertNotEquals(429, status(get("/api/v1/health").header("True-Client-IP", ip)));
            assertNotEquals(429, status(head("/api/v1/health").header("True-Client-IP", ip)));
            assertNotEquals(429, status(post("/api/v1/payments/webhook").header("True-Client-IP", ip).content("{}").contentType("application/json")));
        }
        assertEquals(401, status(api(ip)), "none of those used up the address's count");
    }

    @Test
    void testThatPreflightRequestsAreNotCounted() throws Exception {
        String ip = address();
        for (int i = 0; i < 20; i++) {
            assertNotEquals(429, status(options("/api/v1/tenant").header("True-Client-IP", ip).header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", "GET")));
        }
        assertEquals(401, status(api(ip)));
    }

    @Test
    void testThatWithRedisDownRequestsGoThrough() throws Exception {
        StringRedisTemplate broken = Mockito.mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RuntimeException("down"));
        Object real = ReflectionTestUtils.getField(filter, "redis");
        ReflectionTestUtils.setField(filter, "redis", broken);
        try {
            String ip = address();
            for (int i = 0; i < 12; i++) {
                assertEquals(401, status(api(ip)), "never blocked by its own limiter");
            }
        } finally {
            ReflectionTestUtils.setField(filter, "redis", real);
        }
    }
}
