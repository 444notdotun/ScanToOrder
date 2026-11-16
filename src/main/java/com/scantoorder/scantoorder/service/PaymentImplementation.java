package com.scantoorder.scantoorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.data.model.CodePrefix;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.utils.CodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class PaymentImplementation implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentImplementation.class);

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private PaystackClient paystackClient;

    @Autowired
    private org.modelmapper.ModelMapper modelMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Override
    public InitializePaymentResponse initializePayment(String tenantId, String sessionId, InitializePaymentRequest request) {
        Order order = orderRepo.findByOrderIdAndTenant_TenantId(request.getOrderId(), tenantId)
                .filter(found -> sessionId == null
                        || (found.getDinningSession() != null && sessionId.equals(found.getDinningSession().getSessionId())))
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that order"));

        if (order.getOrderStatus() == OrderStatus.CANCELED) {
            throw new PaymentValidationException("This order was cancelled. Place a new order to pay.");
        }
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentAlreadyProcessedException("Order is already paid");
        }

        // Generate reference
        String reference = CodeGenerator.generate(CodePrefix.PAYMENT);

        // Call Paystack Client FIRST
        PaystackInitResponseData paystackResponse = paystackClient.initialize(
                request.getCustomerEmail(),
                order.getTotalAmount(),
                reference
        );

        // Only upon successful gateway response, instantiate and save Payment
        Payment payment = new Payment();
        payment.setTenant(order.getTenant());
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);

        // Save first (triggers @PrePersist which assigns a random reference)
        payment = paymentRepo.save(payment);

        // Overwrite status/reference to our pre-generated reference and save again to update the DB
        payment.setReference(reference);
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.saveAndFlush(payment);

        return new InitializePaymentResponse(paystackResponse.getAuthorizationUrl(), reference);
    }

    @Transactional
    @Override
    public PaymentStatusResponse verifyAndSyncPayment(String tenantId, String reference) {
        Payment payment = paymentRepo.findByReferenceAndTenant_TenantId(reference, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that payment"));

        if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
            return new PaymentStatusResponse(
                    payment.getReference(),
                    payment.getStatus(),
                    payment.getOrder().getOrderStatus(),
                    payment.getAmount()
            );
        }

        try {
            PaystackVerifyResponseData verifyData = paystackClient.verify(reference);

            if ("success".equalsIgnoreCase(verifyData.getStatus())) {
                long gatewayAmountKobo = verifyData.getAmount();
                long expectedAmountKobo = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
                if (gatewayAmountKobo == expectedAmountKobo) {
                    payment.setStatus(PaymentStatus.SUCCESSFUL);
                    Order order = payment.getOrder();
                    if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
                        order.setOrderStatus(OrderStatus.PAID);
                        orderRepo.save(order);
                    } else if (order.getOrderStatus() == OrderStatus.CANCELED) {
                        // The guest left or was closed out before the money arrived: keep the payment, never send to the kitchen.
                        log.warn("Payment {} succeeded for cancelled order {}. The order stays cancelled and needs a refund.",
                                payment.getReference(), order.getOrderId());
                    }
                } else {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepo.save(payment);
                    throw new PaymentValidationException("Amount mismatch detected");
                }
            } else if ("failed".equalsIgnoreCase(verifyData.getStatus()) || "abandoned".equalsIgnoreCase(verifyData.getStatus())) {
                payment.setStatus(PaymentStatus.FAILED);
            }

            paymentRepo.save(payment);

        } catch (PaymentGatewayTimeoutException | PaymentGatewayUnavailableException e) {
            log.warn("Paystack timeout/unavailable occurred during verification for reference {}", reference, e);
            // No DB mutation, return current status as PENDING
        } catch (PaymentValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Error verifying payment: " + e.getMessage());
        }

        return new PaymentStatusResponse(
                payment.getReference(),
                payment.getStatus(),
                payment.getOrder().getOrderStatus(),
                payment.getAmount()
        );
    }

    @Autowired
    private AsyncPaymentService asyncPaymentService;

    @Override
    public void handleWebhook(String payload, String signatureHeader) {
        String computedSignature = calculateHMAC512(payload, paystackSecretKey);
        if (signatureHeader == null || !signatureHeader.equalsIgnoreCase(computedSignature)) {
            throw new IllegalArgumentException("Invalid webhook signature");
        }

        asyncPaymentService.processWebhookPayload(payload);
    }

    private String calculateHMAC512(String data, String key) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new PaymentGatewayException("Failed to calculate HMAC-SHA512");
        }
    }
}
