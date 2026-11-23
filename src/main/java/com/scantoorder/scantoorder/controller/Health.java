package com.scantoorder.scantoorder.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Public, for an outside scheduler that calls it every few minutes to keep the host and the database awake. It asks the database a
 * question and pings Redis, giving each two seconds. Both answer: 200 "ok". Either does not: 503 "unavailable". The answer never says
 * which, or why: no names, counts, versions or error text. A HEAD request is answered like a GET, without the body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
public class Health {

    private static final long SECONDS = 2;

    @Autowired
    private JdbcTemplate database;
    @Autowired
    private StringRedisTemplate redis;

    private final ExecutorService checks = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "health-check");
        thread.setDaemon(true);
        return thread;
    });

    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return answer();
    }

    // The same check, answered with the status alone.
    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> healthHead() {
        return ResponseEntity.status(answer().getStatusCode()).build();
    }

    private ResponseEntity<String> answer() {
        boolean databaseUp = within("database", () -> database.queryForObject("SELECT 1", Integer.class) != null);
        boolean redisUp = within("redis", () -> redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping()) != null);
        if (databaseUp && redisUp) {
            return ResponseEntity.ok("ok");
        }
        return ResponseEntity.status(503).body("unavailable");
    }

    private boolean within(String what, Callable<Boolean> check) {
        Future<Boolean> result = checks.submit(check);
        try {
            return Boolean.TRUE.equals(result.get(SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.cancel(true);
        } catch (Exception e) {
            result.cancel(true);
        }
        log.warn("A health check failed: {}", what);
        return false;
    }
}
