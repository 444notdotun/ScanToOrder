package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.dtos.respond.RefundQueueResponse;
import com.scantoorder.scantoorder.exception.InvalidWebhookSignatureException;
import com.scantoorder.scantoorder.exception.RefundRejectedException;
import com.scantoorder.scantoorder.service.Interface.OrderRecoveryService;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class PaymentWebhookTest {
    @Autowired
    private WebhookProcessor webhooks;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderRecoveryService recovery;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @Value("${paystack.secret.key:}")
    private String secret;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant tenant;
    private Item plate;

    @BeforeEach
    void setUp() {
        tenant = tenants.main();
        plate = support.item(tenant, "Webhook plate", 2000, true);
    }

    private String charge(String event, Payment payment) {
        long kobo = payment.getAmount().movePointRight(2).longValue();
        return "{\"event\":\"" + event + "\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":" + kobo + "}}";
    }

    private String refund(String event, Payment payment) {
        return "{\"event\":\"" + event + "\",\"data\":{\"transaction_reference\":\"" + payment.getReference() + "\",\"status\":\"x\",\"amount\":\"200000\"}}";
    }

    private Payment pending() {
        return support.payment(support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1), PaymentStatus.PENDING);
    }

    private Payment reload(Payment payment) {
        return paymentRepo.findByReference(payment.getReference()).orElseThrow();
    }

    private OrderStatus orderStatus(Payment payment) {
        return orderRepo.findOrderByOrderId(payment.getOrder().getOrderId()).getOrderStatus();
    }

    @Test
    void testThatASuccessNoticeMarksThePaymentAndSendsTheOrderToTheKitchen() {
        Payment payment = pending();
        webhooks.process(charge("charge.success", payment));
        assertEquals(PaymentStatus.SUCCESSFUL, reload(payment).getStatus());
        assertEquals(OrderStatus.PAID, orderStatus(payment));
        verify(paystackClient, never()).createRefund(anyString(), anyString());
    }

    @Test
    void testThatASecondSuccessNoticeChangesNothingAndSendsNothingTwice() {
        Payment payment = pending();
        webhooks.process(charge("charge.success", payment));
        Order order = orderRepo.findOrderByOrderId(payment.getOrder().getOrderId());
        order.setOrderStatus(OrderStatus.PREPARING);
        orderRepo.save(order);

        webhooks.process(charge("charge.success", payment));
        webhooks.process(charge("charge.success", payment));

        assertEquals(PaymentStatus.SUCCESSFUL, reload(payment).getStatus());
        assertEquals(OrderStatus.PREPARING, orderStatus(payment));
        verify(paystackClient, never()).createRefund(anyString(), anyString());
    }

    @Test
    void testThatTheVerifyPathAfterTheWebhookChangesNothingAndDoesNotAskPaystackAgain() {
        Payment payment = pending();
        webhooks.process(charge("charge.success", payment));

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(tenant.getTenantId(), payment.getReference());

        assertEquals(PaymentStatus.SUCCESSFUL, response.getPaymentStatus());
        assertEquals(OrderStatus.PAID, response.getOrderStatus());
        verify(paystackClient, never()).verify(anyString());
    }

    @Test
    void testThatTheWebhookAfterTheVerifyPathChangesNothing() {
        Payment payment = pending();
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmount().movePointRight(2).longValue(), payment.getReference()));
        paymentService.verifyAndSyncPayment(tenant.getTenantId(), payment.getReference());
        Order order = orderRepo.findOrderByOrderId(payment.getOrder().getOrderId());
        order.setOrderStatus(OrderStatus.READY);
        orderRepo.save(order);

        webhooks.process(charge("charge.success", payment));

        assertEquals(OrderStatus.READY, orderStatus(payment));
    }

    @Test
    void testThatANoticeWithTheWrongAmountIsIgnored() {
        Payment payment = pending();
        webhooks.process("{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":100}}");
        assertEquals(PaymentStatus.PENDING, reload(payment).getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderStatus(payment));
    }

    @Test
    void testThatAFailureNoticeLeavesTheOrderWaitingForPayment() {
        Payment payment = pending();
        webhooks.process(charge("charge.failed", payment));
        assertEquals(PaymentStatus.FAILED, reload(payment).getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderStatus(payment));
    }

    @Test
    void testThatNoticesForUnknownPaymentsAndUnknownEventsAreIgnored() {
        assertDoesNotThrow(() -> webhooks.process("{\"event\":\"charge.success\",\"data\":{\"reference\":\"NOPE\",\"amount\":1}}"));
        assertDoesNotThrow(() -> webhooks.process("{\"event\":\"transfer.success\",\"data\":{}}"));
        assertDoesNotThrow(() -> webhooks.process("not json"));
    }

    @Test
    void testThatARefundProcessedNoticeFinishesAPendingRefundOnce() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.REFUNDED, plate, 1), PaymentStatus.REFUND_PENDING);
        webhooks.process(refund("refund.processed", payment));
        assertEquals(PaymentStatus.REFUNDED, reload(payment).getStatus());
        webhooks.process(refund("refund.processed", payment));
        webhooks.process(refund("refund.failed", payment));
        assertEquals(PaymentStatus.REFUNDED, reload(payment).getStatus());
    }

    @Test
    void testThatARefundFailedNoticeShowsTheRefundToTheManagerAndItCanBeRetried() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.REFUNDED, plate, 1), PaymentStatus.REFUND_PENDING);
        payment.setRefundAttempts(1);
        payment.setRefundReference("RF-" + payment.getReference() + "-1");
        paymentRepo.save(payment);

        webhooks.process(refund("refund.failed", payment));

        assertEquals(PaymentStatus.REFUND_FAILED, reload(payment).getStatus());
        RefundQueueResponse queue = recovery.refundQueue(tenant.getTenantId());
        assertTrue(queue.getFailedRefunds().stream().anyMatch(f -> f.getPaymentReference().equals(payment.getReference())));

        recovery.approveRefund(tenant.getTenantId(), payment.getOrder().getOrderId());

        verify(paystackClient).createRefund(payment.getReference(), "RF-" + payment.getReference() + "-2");
        assertEquals(PaymentStatus.REFUND_PENDING, reload(payment).getStatus());
        assertEquals(OrderStatus.REFUNDED, orderStatus(payment));
        assertTrue(recovery.refundQueue(tenant.getTenantId()).getFailedRefunds().stream().noneMatch(f -> f.getPaymentReference().equals(payment.getReference())));
    }

    @Test
    void testThatARefundNoticeForAPaymentThatIsNotBeingRefundedIsIgnored() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.PAID, plate, 1), PaymentStatus.SUCCESSFUL);
        webhooks.process(refund("refund.processed", payment));
        webhooks.process(refund("refund.failed", payment));
        assertEquals(PaymentStatus.SUCCESSFUL, reload(payment).getStatus());
    }

    @Test
    void testThatARefundThatNeedsTheCustomersBankDetailsStaysPending() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.REFUNDED, plate, 1), PaymentStatus.REFUND_PENDING);
        webhooks.process(refund("refund.needs-attention", payment));
        assertEquals(PaymentStatus.REFUND_PENDING, reload(payment).getStatus());
    }

    @Test
    void testThatAPaymentThatLandsAfterTheGuestLeftIsRefundedInFullAtOnce() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);

        webhooks.process(charge("charge.success", payment));

        Payment saved = reload(payment);
        assertEquals(PaymentStatus.REFUND_PENDING, saved.getStatus());
        assertEquals("RF-" + payment.getReference() + "-1", saved.getRefundReference());
        assertEquals(OrderStatus.CANCELED, orderStatus(payment));
        verify(paystackClient, times(1)).createRefund(payment.getReference(), "RF-" + payment.getReference() + "-1");
    }

    @Test
    void testThatALateRefundIsNotStartedTwiceByDuplicateNotices() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);
        webhooks.process(charge("charge.success", payment));
        webhooks.process(charge("charge.success", payment));
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmount().movePointRight(2).longValue(), payment.getReference()));
        paymentService.verifyAndSyncPayment(tenant.getTenantId(), payment.getReference());

        verify(paystackClient, times(1)).createRefund(anyString(), anyString());
        assertEquals(OrderStatus.CANCELED, orderStatus(payment));
    }

    @Test
    void testThatALateRefundPaystackCannotStartShowsUpForTheManagerToRetry() {
        doThrow(new RefundRejectedException("Paystack could not start the refund")).when(paystackClient).createRefund(anyString(), anyString());
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);

        webhooks.process(charge("charge.success", payment));

        assertEquals(PaymentStatus.REFUND_FAILED, reload(payment).getStatus());
        assertEquals(OrderStatus.CANCELED, orderStatus(payment));
        assertTrue(recovery.refundQueue(tenant.getTenantId()).getFailedRefunds().stream().anyMatch(f -> f.getPaymentReference().equals(payment.getReference())));

        doNothing().when(paystackClient).createRefund(anyString(), anyString());
        recovery.approveRefund(tenant.getTenantId(), payment.getOrder().getOrderId());
        assertEquals(PaymentStatus.REFUND_PENDING, reload(payment).getStatus());
        assertEquals(OrderStatus.CANCELED, orderStatus(payment));
    }

    @Test
    void testThatALatePaymentFoundByTheVerifyPathIsAlsoRefundedAtOnce() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmount().movePointRight(2).longValue(), payment.getReference()));

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(tenant.getTenantId(), payment.getReference());

        assertEquals(PaymentStatus.REFUND_PENDING, response.getPaymentStatus());
        assertEquals(OrderStatus.CANCELED, response.getOrderStatus());
        verify(paystackClient).createRefund(payment.getReference(), "RF-" + payment.getReference() + "-1");
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Test
    void testThatAnUnsignedOrWronglySignedNoticeIsRefused() throws Exception {
        String body = "{\"event\":\"transfer.success\",\"data\":{}}";
        assertThrows(InvalidWebhookSignatureException.class, () -> paymentService.handleWebhook(body, null));
        assertThrows(InvalidWebhookSignatureException.class, () -> paymentService.handleWebhook(body, "deadbeef"));
        assertThrows(InvalidWebhookSignatureException.class, () -> paymentService.handleWebhook(null, sign(body)));
        assertDoesNotThrow(() -> paymentService.handleWebhook(body, sign(body)));
        assertDoesNotThrow(() -> paymentService.handleWebhook(body, sign(body).toUpperCase()));
    }
}
