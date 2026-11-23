package com.scantoorder.scantoorder.service.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

// SendByte is stood in for by a small server on this machine. Nothing here reaches the real service.
class SendByteEmailSenderTest {

    private static final String KEY = "sk_test_super_secret_key_123";
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Canned(int status, String body, String retryAfter) {
    }

    private record Seen(String method, String path, String authorization, String contentType, JsonNode body) {
    }

    private HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private final Deque<Canned> replies = new ArrayDeque<>();
    private SendByteEmailSender sender;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        sender = new SendByteEmailSender(KEY, "http://127.0.0.1:" + server.getAddress().getPort() + "/", "Plately <notify@platelyy.com>", "support@platelyy.com");
        sender.setRetryPause(Duration.ZERO);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        byte[] request = exchange.getRequestBody().readAllBytes();
        seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"), JSON.readTree(request)));
        Canned reply = replies.isEmpty() ? new Canned(201, "{}", null) : replies.poll();
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("x-request-id", "req_abc123");
        if (reply.retryAfter() != null) {
            exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
        }
        exchange.sendResponseHeaders(reply.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static EmailMessage message() {
        return new EmailMessage("guest@example.com", "Your receipt", "plain words", "<p>html words</p>", "receipt", "receipt:abc:1");
    }

    private static String error(String code) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"secret text guest@example.com\",\"docs_url\":\"https://x\"}}";
    }

    @Test
    void testThatTheRequestHasTheShapeSendByteExpects() {
        sender.send(message());

        assertEquals(1, seen.size());
        Seen request = seen.get(0);
        assertEquals("POST", request.method());
        assertEquals("/v1/emails", request.path());
        assertEquals("Bearer " + KEY, request.authorization());
        assertEquals("application/json", request.contentType());
        JsonNode body = request.body();
        assertEquals("Plately <notify@platelyy.com>", body.get("from").asText());
        assertEquals("guest@example.com", body.get("to").asText());
        assertEquals("Your receipt", body.get("subject").asText());
        assertEquals("<p>html words</p>", body.get("html").asText());
        assertEquals("plain words", body.get("text").asText());
        assertEquals("support@platelyy.com", body.get("reply_to").asText());
        assertEquals(1, body.get("tags").size());
        assertEquals("receipt", body.get("tags").get(0).asText());
        assertEquals("receipt:abc:1", body.get("idempotency_key").asText());
    }

    @Test
    void testThatAReplayedKeyCountsAsSent() {
        replies.add(new Canned(200, "{}", null));
        assertDoesNotThrow(() -> sender.send(message()));
        assertEquals(1, seen.size());
    }

    @Test
    void testThatTheIdempotencyKeyIsLeftOutWhenThereIsNoneAndCutAtTwoHundredFiftySixCharacters() {
        sender.send(new EmailMessage("a@example.com", "s", "t", "h", "tag", null));
        sender.send(new EmailMessage("a@example.com", "s", "t", "h", "tag", "k".repeat(300)));

        assertFalse(seen.get(0).body().has("idempotency_key"));
        assertEquals(256, seen.get(1).body().get("idempotency_key").asText().length());
    }

    @Test
    void testThatATooBusyAnswerIsRetriedOnceWithTheSameKey() {
        replies.add(new Canned(429, error("rate_limit_exceeded"), "0"));
        replies.add(new Canned(201, "{}", null));

        sender.send(message());

        assertEquals(2, seen.size());
        assertEquals("receipt:abc:1", seen.get(0).body().get("idempotency_key").asText());
        assertEquals("receipt:abc:1", seen.get(1).body().get("idempotency_key").asText());
    }

    @Test
    void testThatAServerErrorIsRetriedOnceWithTheSameKey() {
        replies.add(new Canned(500, error("internal_error"), null));
        replies.add(new Canned(201, "{}", null));

        sender.send(message());

        assertEquals(2, seen.size());
        assertEquals(seen.get(0).body().get("idempotency_key"), seen.get(1).body().get("idempotency_key"));
    }

    @Test
    void testThatItGivesUpAfterTheOneRetry() {
        replies.add(new Canned(500, error("internal_error"), null));
        replies.add(new Canned(500, error("internal_error"), null));
        replies.add(new Canned(201, "{}", null));

        assertThrows(EmailSendException.class, () -> sender.send(message()));
        assertEquals(2, seen.size());
    }

    @Test
    void testThatAnAnswerThatCannotBeFixedByTryingAgainIsNotRetried() {
        for (int status : new int[]{422, 401, 403, 409, 402}) {
            seen.clear();
            replies.clear();
            replies.add(new Canned(status, error("validation_error"), null));
            assertThrows(EmailSendException.class, () -> sender.send(message()), "status " + status);
            assertEquals(1, seen.size(), "status " + status);
        }
    }

    @Test
    void testThatANetworkErrorIsRetriedOnceThenReportedAsAFailureToSend() {
        server.stop(0);

        EmailSendException failed = assertThrows(EmailSendException.class, () -> sender.send(message()));

        assertEquals("The email could not be sent", failed.getMessage());
        assertEquals(0, seen.size());
    }

    @Test
    void testThatOnlyTheCodeAndTheRequestIdAreLoggedNeverTheKeyTheAddressOrTheText() {
        Logger logger = (Logger) LoggerFactory.getLogger(SendByteEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            replies.add(new Canned(422, error("suppressed_recipient"), null));
            assertThrows(EmailSendException.class, () -> sender.send(message()));

            List<String> lines = new ArrayList<>();
            appender.list.forEach(entry -> lines.add(entry.getFormattedMessage()));
            String logged = String.join("\n", lines);
            assertTrue(logged.contains("suppressed_recipient"));
            assertTrue(logged.contains("req_abc123"));
            assertFalse(logged.contains(KEY));
            assertFalse(logged.contains("guest@example.com"));
            assertFalse(logged.contains("secret text"));
            assertFalse(logged.contains("plain words"));
            assertFalse(logged.contains("html words"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void testThatTheFailureMessageNeverCarriesAnythingFromSendByte() {
        replies.add(new Canned(403, error("domain_not_verified"), null));

        EmailSendException failed = assertThrows(EmailSendException.class, () -> sender.send(message()));

        assertFalse(failed.getMessage().contains("domain"));
        assertFalse(failed.getMessage().contains("secret"));
    }
}
