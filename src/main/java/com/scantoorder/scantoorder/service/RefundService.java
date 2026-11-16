package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.exception.PaymentGatewayException;
import com.scantoorder.scantoorder.exception.RefundRejectedException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Asks Paystack to give a payment back in full. The caller holds the payment row's lock.
 */
@Slf4j
@Component
public class RefundService {

    @Autowired
    private PaystackClient paystackClient;
    @Autowired
    private PaymentRepo paymentRepo;

    // A new reference for every attempt, derived from the payment, so asking twice for the same attempt
    // cannot refund twice: Paystack answers 409 and the client treats that as the same refund.
    public void start(Payment payment) {
        int attempt = payment.getRefundAttempts() + 1;
        String refundReference = "RF-" + payment.getReference() + "-" + attempt;
        paystackClient.createRefund(payment.getReference(), refundReference);
        payment.setRefundAttempts(attempt);
        payment.setRefundReference(refundReference);
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        paymentRepo.save(payment);
    }

    // For a payment nobody asked to refund (it arrived after the guest left): if Paystack cannot take the
    // request now, it is marked REFUND_FAILED so it shows to the manager, who retries.
    public void startOrMarkFailed(Payment payment) {
        try {
            start(payment);
            log.info("Refund started for payment {} that arrived after its order was cancelled", payment.getReference());
        } catch (RefundRejectedException | PaymentGatewayException e) {
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            paymentRepo.save(payment);
            log.warn("Refund could not be started for payment {}. The manager can retry it.", payment.getReference());
        }
    }
}
