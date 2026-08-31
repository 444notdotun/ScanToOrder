package com.scantoorder.scantoorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class PaystackClientImpl implements PaystackClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Value("${paystack.base.url:https://api.paystack.co}")
    private String paystackBaseUrl;

    @Value("${paystack.callback.url:http://localhost:3000/order-success}")
    private String paystackCallbackUrl;

    @Setter
    private HttpClient httpClient = HttpClient.newBuilder()
//            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public PaystackInitResponseData initialize(String email, BigDecimal amount, String reference) {
        try {
            int amountInKobo = amount.multiply(BigDecimal.valueOf(100)).intValue();
            Map<String, Object> bodyMap = Map.of(
                    "email", email != null ? email : "customer@scantoorder.com",
                    "amount", amountInKobo,
                    "reference", reference,
                    "callback_url", paystackCallbackUrl
            );
            String requestBody = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(paystackBaseUrl + "/transaction/initialize"))
                    .header("Authorization", "Bearer " + paystackSecretKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 500 && statusCode < 600) {
                throw new PaymentGatewayUnavailableException("Paystack service is currently down");
            }

            if (statusCode == 400 || statusCode == 422) {
                String errorMsg = "Payment validation failed";
                try {
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    if (responseJson.has("message")) {
                        errorMsg = responseJson.path("message").asText();
                    }
                } catch (Exception e) {

                }
                throw new PaymentValidationException(errorMsg);
            }

            if (statusCode != 200 && statusCode != 201) {
                throw new PaymentGatewayException("Paystack payment initialization failed with status " + statusCode);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            if (!responseJson.path("status").asBoolean()) {
                throw new PaymentGatewayException("Paystack payment initialization rejected: " + responseJson.path("message").asText());
            }

            JsonNode dataNode = responseJson.path("data");
            String authUrl = dataNode.path("authorization_url").asText();

            return new PaystackInitResponseData(authUrl, reference);

        } catch (PaymentValidationException | PaymentGatewayException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new PaymentGatewayTimeoutException("Timeout connecting to Paystack");
        } catch (java.io.IOException e) {
            if (e.getClass().getName().contains("Timeout") || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
                throw new PaymentGatewayTimeoutException("Timeout connecting to Paystack");
            }
            throw new PaymentGatewayException("Error communicating with Paystack: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Communication with Paystack interrupted");
        }
    }

    @Override
    public PaystackVerifyResponseData verify(String reference) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(paystackBaseUrl + "/transaction/verify/" + URLEncoder.encode(reference, StandardCharsets.UTF_8)))
                    .header("Authorization", "Bearer " + paystackSecretKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 500 && statusCode < 600) {
                throw new PaymentGatewayUnavailableException("Paystack service is currently down");
            }

            if (statusCode != 200) {
                throw new PaymentGatewayException("Paystack verification failed with status " + statusCode);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            if (!responseJson.path("status").asBoolean()) {
                throw new PaymentGatewayException("Paystack verification rejected: " + responseJson.path("message").asText());
            }

            JsonNode dataNode = responseJson.path("data");
            String status = dataNode.path("status").asText();
            long amount = dataNode.path("amount").asLong();

            return new PaystackVerifyResponseData(status, amount, reference);

        } catch (PaymentGatewayException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new PaymentGatewayTimeoutException("Timeout connecting to Paystack");
        } catch (java.io.IOException e) {
            if (e.getClass().getName().contains("Timeout") || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
                throw new PaymentGatewayTimeoutException("Timeout connecting to Paystack");
            }
            throw new PaymentGatewayException("Error communicating with Paystack: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Communication with Paystack interrupted");
        }
    }
}
