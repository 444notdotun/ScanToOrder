package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CodePrefix;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PaymentImplementation implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentImplementation.class);

    public static final String CANNOT_TAKE_PAYMENTS = "This place can't take payments yet. Ask a waiter.";

    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private PaystackClient paystackClient;
    @Autowired
    private ChargeCalculator chargeCalculator;
    @Autowired
    private PaymentSettlement settlement;
    @Autowired
    private AsyncPaymentService asyncPaymentService;

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
        Tenant tenant = order.getTenant();
        String subaccount = tenant.getPaystackSubaccountCode();
        if (subaccount == null || subaccount.isBlank()) {
            throw new ConflictException(CANNOT_TAKE_PAYMENTS);
        }

        // Whole kobo, never a fraction: the split is worked out once, here, and recorded.
        long amountKobo = order.getTotalAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        ChargeCalculator.Split split = chargeCalculator.split(amountKobo);
        String reference = CodeGenerator.generate(CodePrefix.PAYMENT);

        // Call Paystack first; only a payment Paystack accepted is saved.
        // Paystack needs an email on every payment. Without the guest's own, its receipt goes to the restaurant.
        String guestEmail = request.getCustomerEmail() == null || request.getCustomerEmail().isBlank()
                ? null : request.getCustomerEmail().trim();
        PaystackInitResponseData paystackResponse = paystackClient.initialize(
                guestEmail != null ? guestEmail : tenant.getEmail(),
                amountKobo,
                reference,
                new PaystackSplit(subaccount, split.platelyChargeKobo(), chargeCalculator.feeBearer())
        );

        Payment payment = new Payment();
        payment.setTenant(tenant);
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPlatelyChargeKobo(split.platelyChargeKobo());
        payment.setTenantAmountKobo(split.tenantAmountKobo());
        payment.setSubaccountCode(subaccount);
        // Only what the guest gave: the restaurant's stand-in must never receive a refund email.
        payment.setCustomerEmail(guestEmail);
        payment = paymentRepo.save(payment);
        payment.setReference(reference);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepo.saveAndFlush(payment);

        return new InitializePaymentResponse(paystackResponse.getAuthorizationUrl(), reference);
    }

    @Transactional
    @Override
    public PaymentStatusResponse verifyAndSyncPayment(String tenantId, String reference) {
        Payment payment = paymentRepo.findForUpdateByReferenceAndTenant(reference, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that payment"));

        // Already settled, refunded or being refunded: a second look changes nothing and sends nothing.
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            return response(payment);
        }

        try {
            PaystackVerifyResponseData verifyData = paystackClient.verify(reference);

            if ("success".equalsIgnoreCase(verifyData.getStatus())) {
                long expectedAmountKobo = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
                if (verifyData.getAmount() == expectedAmountKobo) {
                    settlement.success(payment, null);
                } else {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepo.save(payment);
                    throw new PaymentValidationException("Amount mismatch detected");
                }
            } else if ("failed".equalsIgnoreCase(verifyData.getStatus()) || "abandoned".equalsIgnoreCase(verifyData.getStatus())) {
                settlement.failure(payment, null);
            }
        } catch (PaymentGatewayTimeoutException | PaymentGatewayUnavailableException e) {
            log.warn("Paystack timeout/unavailable occurred during verification for reference {}", reference);
            // No change: the answer is still pending.
        } catch (PaymentValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Error verifying payment: " + e.getMessage());
        }

        return response(payment);
    }

    private PaymentStatusResponse response(Payment payment) {
        return new PaymentStatusResponse(
                payment.getReference(),
                payment.getStatus(),
                payment.getOrder().getOrderStatus(),
                payment.getAmount()
        );
    }

    @Override
    public void handleWebhook(String payload, String signatureHeader) {
        if (payload == null || signatureHeader == null
                || !MessageDigest.isEqual(calculateHMAC512(payload, paystackSecretKey).getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException("Invalid webhook signature");
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
