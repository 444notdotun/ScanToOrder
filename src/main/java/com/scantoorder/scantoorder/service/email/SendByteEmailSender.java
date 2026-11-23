package com.scantoorder.scantoorder.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Sends through SendByte. A key starting sk_test_ runs in SendByte's sandbox (nothing is delivered); sk_live_ delivers for real.
 * Nothing but the error code and the request id is ever logged: not the key, not the address, not any of the email.
 */
@Component
@ConditionalOnExpression(EmailSenderChoice.SENDBYTE)
public class SendByteEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SendByteEmailSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SAFE_CODE = Pattern.compile("[a-z_]{1,64}");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]{1,100}");
    private static final long MAX_WAIT_SECONDS = 10;

    private final String apiKey;
    private final URI endpoint;
    private final String from;
    private final String replyTo;
    private HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // How long to wait before the one retry when the server gave no Retry-After.
    private Duration retryPause = Duration.ofMillis(800);

    public SendByteEmailSender(@Value("${SENDBYTE_API_KEY}") String apiKey,
                               @Value("${SENDBYTE_BASE_URL:https://api.sendbyte.africa}") String baseUrl,
                               @Value("${MAIL_FROM:Plately <notify@platelyy.com>}") String from,
                               @Value("${MAIL_REPLY_TO:support@platelyy.com}") String replyTo) {
        this.apiKey = apiKey.trim();
        this.endpoint = URI.create(baseUrl.trim().replaceAll("/+$", "") + "/v1/emails");
        this.from = from;
        this.replyTo = replyTo;
    }

    void setRetryPause(Duration retryPause) {
        this.retryPause = retryPause;
    }

    @Override
    public void send(EmailMessage message) {
        String body = bodyOf(message);
        Outcome first = attempt(body);
        if (first.success) {
            return;
        }
        if (!first.retry) {
            throw failed(first);
        }
        pause(first.waitSeconds);
        Outcome second = attempt(body);
        if (!second.success) {
            throw failed(second);
        }
    }

    private String bodyOf(EmailMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("from", from);
        node.put("to", message.to());
        node.put("subject", message.subject());
        node.put("html", message.html());
        node.put("text", message.text());
        node.put("reply_to", replyTo);
        if (message.tag() != null && !message.tag().isBlank()) {
            ArrayNode tags = node.putArray("tags");
            tags.add(message.tag());
        }
        if (message.idempotencyKey() != null && !message.idempotencyKey().isBlank()) {
            String key = message.idempotencyKey();
            node.put("idempotency_key", key.length() > 256 ? key.substring(0, 256) : key);
        }
        return node.toString();
    }

    private Outcome attempt(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200 || status == 201) {
                return Outcome.ok();
            }
            String requestId = response.headers().firstValue("x-request-id").filter(id -> SAFE_ID.matcher(id).matches()).orElse("-");
            String code = codeOf(response.body());
            long wait = -1;
            if (status == 429) {
                wait = response.headers().firstValue("Retry-After").map(SendByteEmailSender::seconds).orElse(-1L);
            }
            return Outcome.refused(status, code, requestId, status == 429 || status == 500, wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.refused(-1, "interrupted", "-", false, -1);
        } catch (Exception e) {
            // A network error or a timeout: one retry, with the same idempotency key.
            return Outcome.refused(-1, "network_error", "-", true, -1);
        }
    }

    private static long seconds(String header) {
        try {
            return Math.max(0, Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String codeOf(String body) {
        try {
            JsonNode code = JSON.readTree(body).path("error").path("code");
            if (code.isTextual() && SAFE_CODE.matcher(code.asText()).matches()) {
                return code.asText();
            }
        } catch (Exception ignored) {
            // Not JSON: no code.
        }
        return "unknown";
    }

    private void pause(long waitSeconds) {
        Duration wait = waitSeconds >= 0 ? Duration.ofSeconds(Math.min(waitSeconds, MAX_WAIT_SECONDS)) : retryPause;
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EmailSendException failed(Outcome outcome) {
        log.warn("SendByte did not take an email: status={} code={} request-id={}", outcome.status, outcome.code, outcome.requestId);
        return new EmailSendException("The email could not be sent");
    }

    private record Outcome(boolean success, boolean retry, int status, String code, String requestId, long waitSeconds) {
        static Outcome ok() {
            return new Outcome(true, false, 200, "", "", -1);
        }

        static Outcome refused(int status, String code, String requestId, boolean retry, long waitSeconds) {
            return new Outcome(false, retry, status, code, requestId, waitSeconds);
        }
    }
}
