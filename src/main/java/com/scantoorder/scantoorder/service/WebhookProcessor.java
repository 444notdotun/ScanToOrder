package com.scantoorder.scantoorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Handles one Paystack notice. Every branch only moves a payment forward from the state it expects,
 * so a notice that arrives twice, or late, changes nothing.
 */
@Slf4j
@Service
public class WebhookProcessor {

    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private PaymentSettlement settlement;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void process(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            JsonNode data = root.path("data");
            switch (event) {
                case "charge.success" -> chargeSucceeded(data, payload);
                case "charge.failed" -> chargeFailed(data, payload);
                case "refund.processed" -> refundProcessed(data);
                case "refund.failed" -> refundFailed(data);
                case "refund.needs-attention" ->
                        log.warn("Refund for payment {} needs the customer's bank details. It stays pending.", data.path("transaction_reference").asText());
                default -> log.debug("Ignored a {} notice", event);
            }
        } catch (Exception e) {
            log.error("Error processing a Paystack notice", e);
        }
    }

    private void chargeSucceeded(JsonNode data, String payload) {
        Payment payment = lock(data.path("reference").asText());
        if (payment == null) return;
        if (data.hasNonNull("amount")) {
            long expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
            if (data.path("amount").asLong() != expected) {
                log.warn("Notice for payment {} carries a different amount. Ignored.", payment.getReference());
                return;
            }
        }
        if (!settlement.success(payment, payload)) {
            log.info("Payment {} was already settled. Nothing to do.", payment.getReference());
        }
    }

    private void chargeFailed(JsonNode data, String payload) {
        Payment payment = lock(data.path("reference").asText());
        if (payment != null) {
            settlement.failure(payment, payload);
        }
    }

    private void refundProcessed(JsonNode data) {
        Payment payment = lock(data.path("transaction_reference").asText());
        if (payment != null && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepo.save(payment);
        }
    }

    private void refundFailed(JsonNode data) {
        Payment payment = lock(data.path("transaction_reference").asText());
        if (payment != null && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            paymentRepo.save(payment);
        }
    }

    private Payment lock(String reference) {
        if (reference == null || reference.isBlank()) return null;
        Payment payment = paymentRepo.findForUpdateByReference(reference).orElse(null);
        if (payment == null) {
            log.warn("A Paystack notice named a payment we do not know");
        }
        return payment;
    }
}
