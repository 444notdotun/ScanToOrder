package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventPayment;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.EventPaymentRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.exception.PaymentGatewayTimeoutException;
import com.scantoorder.scantoorder.exception.PaymentGatewayUnavailableException;
import com.scantoorder.scantoorder.exception.PaymentValidationException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * What a planner's payment does once Paystack confirms it. The webhook and the verify call both come here,
 * and the payment row is locked first, so a second notice for the same reference adds nothing.
 */
@Slf4j
@Component
public class EventPaymentService {

    public static final String REFERENCE_PREFIX = "EVENT-";

    @Autowired
    private EventPaymentRepo paymentRepo;
    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private PaystackClient paystackClient;

    public static boolean isEventReference(String reference) {
        return reference != null && reference.startsWith(REFERENCE_PREFIX);
    }

    // The first payment makes a draft event ACTIVE; every later one is a top-up. Returns false when there was nothing to do.
    @Transactional
    public boolean success(String reference, Long amountKobo) {
        EventPayment payment = paymentRepo.findForUpdateByReference(reference).orElse(null);
        if (payment == null) {
            log.warn("A Paystack notice named an event payment we do not know");
            return false;
        }
        if (amountKobo != null && amountKobo != payment.getAmountKobo()) {
            log.warn("Notice for event payment {} carries a different amount. Ignored.", payment.getReference());
            return false;
        }
        return settle(payment);
    }

    @Transactional
    public boolean failure(String reference) {
        EventPayment payment = paymentRepo.findForUpdateByReference(reference).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepo.save(payment);
        return true;
    }

    // The planner's screen asks after paying: Paystack is asked only while the payment is not settled yet.
    @Transactional
    public PaymentStatusResponse verify(String tenantId, String reference) {
        EventPayment payment = paymentRepo.findForUpdateByReferenceAndTenant(reference, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that payment"));
        if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.FAILED) {
            try {
                PaystackVerifyResponseData data = paystackClient.verify(reference);
                if ("success".equalsIgnoreCase(data.getStatus())) {
                    if (data.getAmount() != payment.getAmountKobo()) {
                        throw new PaymentValidationException("Amount mismatch detected");
                    }
                    settle(payment);
                } else if ("failed".equalsIgnoreCase(data.getStatus()) || "abandoned".equalsIgnoreCase(data.getStatus())) {
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepo.save(payment);
                    }
                }
            } catch (PaymentGatewayTimeoutException | PaymentGatewayUnavailableException e) {
                log.warn("Paystack was slow or down while checking event payment {}", reference);
            }
        }
        return new PaymentStatusResponse(payment.getReference(), payment.getStatus(), null,
                BigDecimal.valueOf(payment.getAmountKobo(), 2));
    }

    private boolean settle(EventPayment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            return false;
        }
        Event event = eventRepo.findForUpdate(payment.getEvent().getId(), payment.getTenant().getTenantId()).orElse(payment.getEvent());
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        paymentRepo.save(payment);
        event.setPaidGuests(event.getPaidGuests() + payment.getGuests());
        if (event.getStatus() == EventStatus.DRAFT) {
            event.setStatus(EventStatus.ACTIVE);
        }
        eventRepo.save(event);
        return true;
    }
}
