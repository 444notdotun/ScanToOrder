package com.scantoorder.scantoorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AsyncPaymentService {

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private OrderRepo orderRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    @Transactional
    public void processWebhookPayload(String payload) {
        try {
            JsonNode payloadJson = objectMapper.readTree(payload);
            String event = payloadJson.path("event").asText();
            JsonNode dataNode = payloadJson.path("data");
            String reference = dataNode.path("reference").asText();

            Payment payment = paymentRepo.findByReference(reference).orElse(null);
            if (payment == null) {
                log.warn("Payment not found with reference: {}", reference);
                return;
            }

            Order order = payment.getOrder();
            if (order.getOrderStatus() == OrderStatus.PAID) {
                log.info("Idempotency check: Order {} is already PAID", order.getOrderId());
                return; // Skip processing
            }

            if ("charge.success".equals(event)) {
                payment.setGatewayResponse(payload);
                payment.setStatus(PaymentStatus.SUCCESSFUL);
                order.setOrderStatus(OrderStatus.PAID);
                orderRepo.save(order);
                paymentRepo.save(payment);
                log.info("Successfully processed charge.success for order {}", order.getOrderId());
            } else if ("charge.failed".equals(event)) {
                payment.setGatewayResponse(payload);
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepo.save(payment);
                log.info("Processed charge.failed for order {}, left in PENDING_PAYMENT", order.getOrderId());
            }

        } catch (Exception e) {
            log.error("Error processing webhook payload asynchronously", e);
        }
    }
}
