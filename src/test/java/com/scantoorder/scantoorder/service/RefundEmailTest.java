package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scantoorder.scantoorder.dtos.request.GuestEmailRequest;
import com.scantoorder.scantoorder.dtos.request.SettleRefundRequest;
import com.scantoorder.scantoorder.service.Interface.RefundTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The refund email goes out only once the refund is saved for good, so these tests really commit
 * and clean up after themselves.
 */
@SpringBootTest
class RefundEmailTest {
    @Autowired
    private WebhookProcessor webhooks;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @Autowired
    private RefundTrackingService refunds;
    @MockitoSpyBean
    private InMemoryEmailSender emailSender;
    @MockitoBean
    private PaystackClient paystackClient;

    private Payment payment;

    @AfterEach
    void cleanUp() {
        reset(emailSender);
        if (payment == null) return;
        refundRepo.findByPayment_Id(payment.getId()).ifPresent(refundRepo::delete);
        paymentRepo.deleteById(payment.getId());
        orderItemRepo.deleteAll(orderItemRepo.findByOrder_OrderId(payment.getOrder().getOrderId()));
        orderRepo.deleteById(payment.getOrder().getOrderId());
    }

    private void latePaymentFrom(String email) {
        Tenant tenant = tenants.main();
        Item plate = support.item(tenant, "Email plate", 2000, true);
        payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);
        payment.setCustomerEmail(email);
        payment = paymentRepo.save(payment);
        webhooks.process("{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":200000}}");
    }

    @Test
    void testThatTheGuestGetsTheRefundLinkAtTheAddressThePaymentWasMadeWith() {
        latePaymentFrom("guest@example.com");

        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();
        ArgumentCaptor<EmailMessage> body = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(1)).send(body.capture());
        assertEquals("guest@example.com", body.getValue().to());
        assertTrue(body.getValue().text().contains("/refund/" + refund.getId() + "?t=" + refund.getGuestToken()));
        assertTrue(body.getValue().text().contains("₦2,000.00"));
        assertTrue(body.getValue().text().contains("Test Place"));
    }

    @Test
    void testThatARefundIsStillRecordedWhenTheEmailCannotBeSent() {
        doThrow(new EmailSendException("down")).when(emailSender).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));

        latePaymentFrom("guest@example.com");

        assertEquals(RefundStatus.DUE, refundRepo.findByPayment_Id(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    void testThatNoEmailIsSentWhenThePaymentHasNoAddress() {
        latePaymentFrom(null);

        assertTrue(refundRepo.findByPayment_Id(payment.getId()).isPresent());
        verifyNoInteractions(emailSender);
    }

    private SettleRefundRequest settle(RefundMethod method) {
        SettleRefundRequest request = new SettleRefundRequest();
        request.setMethod(method);
        request.setTransferReference("TRF-1");
        return request;
    }

    @Test
    void testThatSettlingEmailsTheGuestWhoGaveAnEmailWithTheMethodDateAndLink() {
        latePaymentFrom("guest@example.com");
        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();
        clearInvocations(emailSender);

        refunds.settle(tenants.main().getTenantId(), "w", refund.getId(), settle(RefundMethod.TRANSFER));

        ArgumentCaptor<EmailMessage> body = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(1)).send(body.capture());
        assertEquals("guest@example.com", body.getValue().to());
        assertTrue(body.getValue().text().contains("Test Place has sent your refund of ₦2,000.00 by transfer on " + java.time.LocalDate.now()));
        assertTrue(body.getValue().text().contains("If it hasn't reached you, tell us here:"));
        assertTrue(body.getValue().text().contains("/refund/" + refund.getId() + "?t=" + refund.getGuestToken()));
    }

    @Test
    void testThatSettlingAGuestWithoutAnEmailSendsNothingAndStillSettles() {
        latePaymentFrom(null);
        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();

        refunds.settle(tenants.main().getTenantId(), "w", refund.getId(), settle(RefundMethod.CASH));

        assertEquals(RefundStatus.SETTLED, refundRepo.findById(refund.getId()).orElseThrow().getStatus());
        verifyNoInteractions(emailSender);
    }

    @Test
    void testThatAnEmailGivenOnTheRefundPageGetsTheLinkOnceAndAChangeDoesNot() {
        latePaymentFrom(null);
        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();
        GuestEmailRequest first = new GuestEmailRequest();
        first.setEmail("new@example.com");

        refunds.guestEmail(refund.getId(), refund.getGuestToken(), first);
        first.setEmail("newer@example.com");
        refunds.guestEmail(refund.getId(), refund.getGuestToken(), first);

        verify(emailSender, times(1)).send(org.mockito.ArgumentMatchers.argThat(m -> m.to().equals("new@example.com") && m.text().contains("/refund/" + refund.getId())));
        verify(emailSender, never()).send(org.mockito.ArgumentMatchers.argThat(m -> m.to().equals("newer@example.com")));
    }

    @Test
    void testThatNoEmailAddressEverReachesTheLogs() {
        Logger root = (Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            doThrow(new EmailSendException("down for guest@example.com")).when(emailSender).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));
            latePaymentFrom("guest@example.com");
            Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();
            refunds.settle(tenants.main().getTenantId(), "w", refund.getId(), settle(RefundMethod.CASH));
            GuestEmailRequest request = new GuestEmailRequest();
            request.setEmail("changed@example.com");
            refunds.guestEmail(refund.getId(), refund.getGuestToken(), request);
        } finally {
            root.detachAppender(appender);
        }
        String logged = String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertFalse(logged.contains("guest@example.com"));
        assertFalse(logged.contains("changed@example.com"));
        assertFalse(logged.contains("@example.com"));
        assertTrue(logged.contains("could not be sent"));
    }
}
