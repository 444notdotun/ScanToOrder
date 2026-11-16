package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
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
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(1)).send(eq("guest@example.com"), anyString(), body.capture());
        assertTrue(body.getValue().contains("/refund/" + refund.getId() + "?t=" + refund.getGuestToken()));
        assertTrue(body.getValue().contains("₦2,000.00"));
        assertTrue(body.getValue().contains("Test Place"));
    }

    @Test
    void testThatARefundIsStillRecordedWhenTheEmailCannotBeSent() {
        doThrow(new EmailSendException("down")).when(emailSender).send(anyString(), anyString(), anyString());

        latePaymentFrom("guest@example.com");

        assertEquals(RefundStatus.DUE, refundRepo.findByPayment_Id(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    void testThatNoEmailIsSentWhenThePaymentHasNoAddress() {
        latePaymentFrom(null);

        assertTrue(refundRepo.findByPayment_Id(payment.getId()).isPresent());
        verifyNoInteractions(emailSender);
    }
}
