package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.Refund;
import com.scantoorder.scantoorder.data.model.RefundReason;
import com.scantoorder.scantoorder.data.model.RefundStatus;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Writes down that a restaurant owes a guest their money back. Plately moves no money: the restaurant
 * pays the guest and records it. The caller holds the payment's (or its order's) lock.
 */
@Slf4j
@Component
public class RefundService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private EmailSender emailSender;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    // One refund per payment: asking again for the same payment returns the refund already there.
    public Refund createDue(Payment payment, RefundReason reason) {
        return refundRepo.findByPayment_Id(payment.getId()).orElseGet(() -> {
            Refund refund = new Refund();
            refund.setTenant(payment.getTenant());
            refund.setOrder(payment.getOrder());
            refund.setPayment(payment);
            refund.setAmountKobo(payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact());
            refund.setReason(reason);
            refund.setStatus(RefundStatus.DUE);
            refund.setGuestToken(newToken());
            refund = refundRepo.save(refund);
            log.info("A {} refund is due on payment {}", reason, payment.getReference());
            tellTheGuest(refund, payment.getCustomerEmail());
            return refund;
        });
    }

    public String linkFor(Refund refund) {
        return frontendUrl + "/refund/" + refund.getId() + "?t=" + refund.getGuestToken();
    }

    // Sent once the refund is safely saved, so a rolled-back payment never sends a link to nothing.
    private void tellTheGuest(Refund refund, String email) {
        if (email == null || email.isBlank()) return;
        Runnable send = () -> {
            try {
                emailSender.send(email, "Your refund from " + refund.getTenant().getName(),
                        "Hello,\n\n"
                                + refund.getTenant().getName() + " owes you " + Money.naira(refund.getAmountKobo())
                                + " for order " + refund.getOrder().getOrderNumber() + ".\n\n"
                                + "See the refund, add your bank details, or tell us you have been paid:\n"
                                + linkFor(refund) + "\n\n"
                                + "The Plately team");
            } catch (EmailSendException e) {
                log.warn("A refund email could not be sent for order {}", refund.getOrder().getOrderNumber());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
