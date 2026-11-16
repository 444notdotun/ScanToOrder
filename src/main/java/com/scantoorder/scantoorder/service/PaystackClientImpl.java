package com.scantoorder.scantoorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import com.scantoorder.scantoorder.dtos.respond.SubaccountData;
import com.scantoorder.scantoorder.exception.AccountVerificationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class PaystackClientImpl implements PaystackClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Value("${paystack.base.url:https://api.paystack.co}")
    private String paystackBaseUrl;

    @Value("${paystack.callback.url}")
    private String paystackCallbackUrl;

    @Setter
    private HttpClient httpClient = HttpClient.newBuilder()
//            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static final String RECEIPT_EMAIL_REFUSED = "Paystack couldn't use that email for the receipt. Check it and try again.";
    public static final String PAYMENT_REFUSED = "Paystack couldn't take that payment. Check the details and try again.";

    // Paystack's own words are for the log. A person is told what to do in ours.
    static String inOurVoice(String raw) {
        log.warn("Paystack refused a request: {}", raw);
        return raw != null && raw.toLowerCase().contains("email") ? RECEIPT_EMAIL_REFUSED : PAYMENT_REFUSED;
    }

    @Override
    public PaystackInitResponseData initialize(String email, long amountKobo, String reference, PaystackSplit split) {
        try {
            Map<String, Object> bodyMap = new java.util.LinkedHashMap<>();
            bodyMap.put("email", email != null ? email : "customer@scantoorder.com");
            bodyMap.put("amount", amountKobo);
            bodyMap.put("reference", reference);
            bodyMap.put("callback_url", paystackCallbackUrl);
            if (split != null) {
                bodyMap.put("subaccount", split.subaccountCode());
                bodyMap.put("transaction_charge", split.platelyChargeKobo());
                bodyMap.put("bearer", split.bearer());
            }
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
                String raw = "";
                try {
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    if (responseJson.has("message")) {
                        raw = responseJson.path("message").asText();
                    }
                } catch (Exception e) {
                }
                throw new PaymentValidationException(inOurVoice(raw));
            }
            if (statusCode != 200 && statusCode != 201) {
                throw new PaymentGatewayException("Paystack payment initialization failed with status " + statusCode);
            }
            JsonNode responseJson = objectMapper.readTree(response.body());
            if (!responseJson.path("status").asBoolean()) {
                throw new PaymentValidationException(inOurVoice(responseJson.path("message").asText()));
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
                throw new PaymentGatewayException(inOurVoice(responseJson.path("message").asText()));
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

    @Override
    public List<BankData> listBanks() {
        JsonNode root = get(paystackBaseUrl + "/bank?country=nigeria&perPage=200&use_cursor=false");
        List<BankData> banks = new ArrayList<>();
        for (JsonNode bank : root.path("data")) {
            String name = bank.path("name").asText();
            String code = bank.path("code").asText();
            if (!name.isBlank() && !code.isBlank()) {
                banks.add(new BankData(name, code));
            }
        }
        return banks;
    }

    @Override
    public String resolveAccountName(String bankCode, String accountNumber) {
        String url = paystackBaseUrl + "/bank/resolve?account_number=" + URLEncoder.encode(accountNumber, StandardCharsets.UTF_8)
                + "&bank_code=" + URLEncoder.encode(bankCode, StandardCharsets.UTF_8);
        try {
            JsonNode root = get(url);
            String name = root.path("data").path("account_name").asText();
            if (name.isBlank()) {
                throw new AccountVerificationException(ACCOUNT_NOT_FOUND);
            }
            return name;
        } catch (PaymentValidationException e) {
            throw new AccountVerificationException(ACCOUNT_NOT_FOUND);
        }
    }

    public static final String ACCOUNT_NOT_FOUND =
            "The bank couldn't find that account. Check the number and the bank, then try again.";

    // A 4xx from Paystack means our request was not accepted; the account number is never put in a message.
    private JsonNode get(String url) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + paystackSecretKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode >= 500 && statusCode < 600) {
                throw new PaymentGatewayUnavailableException("Paystack service is currently down");
            }
            if (statusCode >= 400 && statusCode < 500) {
                throw new PaymentValidationException("Paystack did not accept the request");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("status").asBoolean()) {
                throw new PaymentValidationException("Paystack did not accept the request");
            }
            return root;
        } catch (PaymentValidationException | PaymentGatewayException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new PaymentGatewayTimeoutException("Timeout connecting to Paystack");
        } catch (java.io.IOException e) {
            throw new PaymentGatewayException("Error communicating with Paystack");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Communication with Paystack interrupted");
        }
    }

    public static final String PAYOUT_REJECTED =
            "Paystack rejected this business's payout account. Ask them to check it and send the form again.";

    @Override
    public String createSubaccount(String businessName, String bankCode, String accountNumber, java.math.BigDecimal percentageCharge) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("business_name", businessName);
        body.put("bank_code", bankCode);
        body.put("account_number", accountNumber);
        body.put("percentage_charge", percentageCharge);
        HttpResponse<String> response = post("/subaccount", body);
        int status = response.statusCode();
        if (status >= 400 && status < 500) {
            // Never put the account number in a message or a log.
            throw new PayoutAccountRejectedException(PAYOUT_REJECTED);
        }
        JsonNode root = readJson(response, status);
        String code = root.path("data").path("subaccount_code").asText();
        if (!root.path("status").asBoolean() || code.isBlank()) {
            throw new PayoutAccountRejectedException(PAYOUT_REJECTED);
        }
        return code;
    }

    public static final String PAYOUT_UPDATE_REJECTED =
            "Paystack didn't accept that account, so nothing was changed. Check the details and try again.";

    @Override
    public void updateSubaccount(String subaccountCode, String bankCode, String accountNumber) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("settlement_bank", bankCode);
        body.put("account_number", accountNumber);
        HttpResponse<String> response = send("PUT", "/subaccount/" + URLEncoder.encode(subaccountCode, StandardCharsets.UTF_8), body);
        int status = response.statusCode();
        if (status >= 400 && status < 500) {
            // Never put the account number in a message or a log.
            throw new PayoutAccountRejectedException(PAYOUT_UPDATE_REJECTED);
        }
        JsonNode root = readJson(response, status);
        if (!root.path("status").asBoolean()) {
            throw new PayoutAccountRejectedException(PAYOUT_UPDATE_REJECTED);
        }
    }

    @Override
    public List<SubaccountData> listSubaccounts() {
        List<SubaccountData> found = new ArrayList<>();
        for (int page = 1; page <= 20; page++) {
            JsonNode root = get(paystackBaseUrl + "/subaccount?perPage=100&page=" + page);
            JsonNode data = root.path("data");
            for (JsonNode node : data) {
                String code = node.path("subaccount_code").asText();
                if (!code.isBlank()) {
                    found.add(new SubaccountData(code, node.path("business_name").asText()));
                }
            }
            if (data.size() < 100) break;
        }
        return found;
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) {
        return send("POST", path, body);
    }

    private HttpResponse<String> send(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(paystackBaseUrl + path))
                    .header("Authorization", "Bearer " + paystackSecretKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new PaymentGatewayTimeoutException("Timeout connecting to Paystack");
        } catch (java.io.IOException e) {
            throw new PaymentGatewayException("Error communicating with Paystack");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Communication with Paystack interrupted");
        }
    }

    private JsonNode readJson(HttpResponse<String> response, int status) {
        if (status >= 500 && status < 600) {
            throw new PaymentGatewayUnavailableException("Paystack service is currently down");
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new PaymentGatewayException("Paystack sent an answer we could not read");
        }
    }
}
