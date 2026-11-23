package com.scantoorder.scantoorder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Counts things in Redis, one counter per key and window. Keys are hashed, so an email address never sits in Redis in the clear.
 * If Redis cannot be reached the answer is "no": a limit that cannot be checked is treated as reached.
 */
@Slf4j
@Component
public class RateLimiter {

    @Autowired
    private StringRedisTemplate redis;

    // True when this is still within `max` for the window. Every call counts, allowed or not.
    public boolean allow(String purpose, String subject, int max, Duration window) {
        try {
            String key = "limit:" + purpose + ":" + digest(subject);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
            return count != null && count <= max;
        } catch (RuntimeException e) {
            log.warn("A limit ({}) could not be checked", purpose);
            return false;
        }
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
