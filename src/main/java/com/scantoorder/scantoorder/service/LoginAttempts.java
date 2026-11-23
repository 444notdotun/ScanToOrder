package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.RateSettings;
import com.scantoorder.scantoorder.exception.TryAgainLaterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Wrong passwords, counted per username (trimmed, lower case) in Redis. Unknown usernames are counted the same way, so the answer never
 * shows whether an account exists. After too many wrong ones the username waits: even the right password is refused until the wait ends.
 * The account itself is never locked. If Redis cannot be reached, signing in carries on and a line is logged.
 */
@Slf4j
@Component
public class LoginAttempts {

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private RateSettings rates;

    private static String key(String kind, String username) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "login:" + kind + ":" + RateLimiter.digest(name);
    }

    String waitMessage() {
        return "Too many tries. Wait " + rates.getLoginWindowMinutes() + " minutes, then try again.";
    }

    // Called before the password is checked, so a right password cannot get in while the username is waiting.
    public void requireNotWaiting(String username) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(key("lock", username)))) {
                throw new TryAgainLaterException(HttpStatus.TOO_MANY_REQUESTS, waitMessage());
            }
        } catch (TryAgainLaterException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("The sign-in limit could not be checked");
        }
    }

    public void recordWrong(String username) {
        try {
            Duration window = Duration.ofMinutes(rates.getLoginWindowMinutes());
            String tries = key("tries", username);
            Long count = redis.opsForValue().increment(tries);
            if (count != null && count == 1L) {
                redis.expire(tries, window);
            }
            if (count != null && count >= rates.getLoginMax()) {
                redis.opsForValue().set(key("lock", username), "1", window);
                redis.delete(tries);
            }
        } catch (RuntimeException e) {
            log.warn("A wrong password could not be counted");
        }
    }

    public void clear(String username) {
        try {
            redis.delete(key("tries", username));
        } catch (RuntimeException e) {
            log.warn("The sign-in count could not be cleared");
        }
    }
}
