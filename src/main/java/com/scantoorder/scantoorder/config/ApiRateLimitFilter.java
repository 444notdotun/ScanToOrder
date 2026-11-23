package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.service.RateLimiter;
import com.scantoorder.scantoorder.utils.ClientAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts requests per client address, a minute at a time, across the whole API. Over the limit: 429 with Retry-After. The live-updates
 * stream, the health check and the Paystack webhook are never counted, and neither are browsers' preflight requests. If Redis cannot be
 * reached the request goes through, and the problem is logged at most once a minute: the app is never blocked by its own limiter.
 */
@Slf4j
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    public static final String TOO_MANY = "Too many requests. Try again in a minute.";
    private static final long MINUTE_MS = 60_000L;

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private RateSettings rates;

    private final AtomicLong lastLogged = new AtomicLong(0);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/api/v1/stream") || path.startsWith("/api/v1/stream/")
                || path.equals("/api/v1/health")
                || path.equals("/api/v1/payments/webhook");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        long count;
        try {
            String key = "limit:api:" + RateLimiter.digest(ClientAddress.of(request)) + ":" + now / MINUTE_MS;
            Long counted = redis.opsForValue().increment(key);
            if (counted != null && counted == 1L) {
                redis.expire(key, Duration.ofSeconds(90));
            }
            count = counted == null ? 0 : counted;
        } catch (RuntimeException e) {
            logOncePerMinute(now);
            chain.doFilter(request, response);
            return;
        }
        if (count > rates.getApiPerMinute()) {
            long wait = Math.max(1, (MINUTE_MS - now % MINUTE_MS + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(wait));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"" + TOO_MANY + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private void logOncePerMinute(long now) {
        long last = lastLogged.get();
        if (now - last >= MINUTE_MS && lastLogged.compareAndSet(last, now)) {
            log.warn("The request limit could not be checked, so requests are going through");
        }
    }
}
