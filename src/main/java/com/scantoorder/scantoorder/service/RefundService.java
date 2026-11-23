package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.Refund;
import com.scantoorder.scantoorder.data.model.RefundMethod;
import com.scantoorder.scantoorder.data.model.RefundReason;
import com.scantoorder.scantoorder.data.model.RefundStatus;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.service.email.EmailTemplate;
import com.scantoorder.scantoorder.service.email.Mailer;
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
    private Mailer mailer;
    @Autowired
    private LiveUpdates liveUpdates;

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
            refund.setGuestEmail(payment.getCustomerEmail());
            refund = refundRepo.save(refund);
            log.info("A {} refund is due on payment {}", reason, payment.getReference());
            tellTheGuest(refund);
            liveUpdates.publish(refund.getTenant().getTenantId(), sessionOf(refund), "refund", refund.getId(), "DUE");
            return refund;
        });
    }

    String sessionOf(Refund refund) {
        return refund.getOrder().getDinningSession() == null ? null : refund.getOrder().getDinningSession().getSessionId();
    }

    public String linkFor(Refund refund) {
        return frontendUrl + "/refund/" + refund.getId() + "?t=" + refund.getGuestToken();
    }

    // Both emails go out only once the change is safely saved, so a rolled-back change never sends a
    // link to nothing, and a failed email never undoes the change. Addresses are never logged.
    private void tellTheGuest(Refund refund) {
        String business = refund.getTenant().getName();
        EmailTemplate.Rendered body = EmailTemplate.of(business, "Your refund")
                .paragraph("Hello,")
                .paragraph(business + " owes you " + Money.naira(refund.getAmountKobo()) + " for order " + refund.getOrder().getOrderNumber() + ".")
                .paragraph("See the refund, add your bank details, or tell us you have been paid:")
                .button("See your refund", linkFor(refund))
                .render();
        // A guest who adds an email later asks for this one again, so the key moves on every minute.
        sendAfterCommit(refund, "Your refund from " + business, body, "refund-due", "refund-due:" + refund.getId() + ":" + System.currentTimeMillis() / 60_000);
    }

    public void tellTheGuestItWasSent(Refund refund) {
        String business = refund.getTenant().getName();
        EmailTemplate.Rendered body = EmailTemplate.of(business, "Your refund is on its way")
                .paragraph("Hello,")
                .paragraph(business + " has sent your refund of " + Money.naira(refund.getAmountKobo())
                        + " by " + (refund.getMethod() == RefundMethod.CASH ? "cash" : "transfer")
                        + " on " + refund.getSettledAt().toLocalDate() + ".")
                .paragraph("If it hasn't reached you, tell us here:")
                .button("Tell us", linkFor(refund))
                .render();
        sendAfterCommit(refund, "Your refund from " + business, body, "refund-sent", "refund-sent:" + refund.getId());
    }

    // Also used when the guest adds their email to a refund that is already due.
    public void sendLink(Refund refund) {
        tellTheGuest(refund);
    }

    private void sendAfterCommit(Refund refund, String subject, EmailTemplate.Rendered body, String tag, String key) {
        String email = refund.getGuestEmail();
        if (email == null || email.isBlank()) return;
        mailer.sendAfterCommit(EmailMessage.of(email, subject, body, tag, key));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
