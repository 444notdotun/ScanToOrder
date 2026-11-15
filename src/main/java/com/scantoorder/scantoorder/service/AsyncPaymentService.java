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
            if (payment.getStatus() == PaymentStatus.SUCCESSFUL || order.getOrderStatus() == OrderStatus.PAID) {
                log.info("Idempotency check: payment for order {} is already recorded", order.getOrderId());
                return; // Skip processing
            }

            if ("charge.success".equals(event)) {
                payment.setGatewayResponse(payload);
                payment.setStatus(PaymentStatus.SUCCESSFUL);
                paymentRepo.save(payment);
                if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.setOrderStatus(OrderStatus.PAID);
                    orderRepo.save(order);
                    log.info("Successfully processed charge.success for order {}", order.getOrderId());
                } else {
                    // The guest left or was closed out before the money arrived: keep the payment, never send to the kitchen.
                    log.warn("Payment {} succeeded for order {} which is {}. The order stays as it is and needs a refund.",
                            payment.getReference(), order.getOrderId(), order.getOrderStatus());
                }
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
