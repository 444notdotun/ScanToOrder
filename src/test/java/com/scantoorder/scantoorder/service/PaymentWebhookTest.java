package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.exception.InvalidWebhookSignatureException;
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
    private RefundRepo refundRepo;
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
        // The seeder spoke to the mock while the app started; only what the test does counts.
        clearInvocations(paystackClient);
        tenant = tenants.main();
        plate = support.item(tenant, "Webhook plate", 2000, true);
    }

    private String charge(String event, Payment payment) {
        long kobo = payment.getAmount().movePointRight(2).longValue();
        return "{\"event\":\"" + event + "\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":" + kobo + "}}";
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
        assertTrue(refundRepo.findByPayment_Id(payment.getId()).isEmpty());
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
        assertTrue(refundRepo.findByPayment_Id(payment.getId()).isEmpty());
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
    void testThatASuccessNoticeWithoutAnAmountIsIgnored() {
        Payment payment = pending();
        webhooks.process("{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + payment.getReference() + "\"}}");
        assertEquals(PaymentStatus.PENDING, reload(payment).getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderStatus(payment));
        assertTrue(refundRepo.findByPayment_Id(payment.getId()).isEmpty());
    }

    @Test
    void testThatPayingAnOutdatedTotalDoesNotReachTheKitchenAndIsOwedBack() {
        Payment payment = pending();
        Order order = orderRepo.findOrderByOrderId(payment.getOrder().getOrderId());
        order.setTotalAmount(order.getTotalAmount().add(new java.math.BigDecimal("500.00")));
        orderRepo.saveAndFlush(order);

        webhooks.process(charge("charge.success", payment));

        assertEquals(PaymentStatus.SUCCESSFUL, reload(payment).getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderStatus(payment));
        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();
        assertEquals(RefundReason.AMOUNT_CHANGED, refund.getReason());
        assertEquals(payment.getAmount().movePointRight(2).longValueExact(), refund.getAmountKobo());
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
    void testThatAPaymentThatLandsAfterTheGuestLeftBecomesALatePaymentRefundAndTheOrderStaysCancelled() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);

        webhooks.process(charge("charge.success", payment));

        assertEquals(PaymentStatus.SUCCESSFUL, reload(payment).getStatus());
        assertEquals(OrderStatus.CANCELED, orderStatus(payment));
        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElseThrow();
        assertEquals(RefundReason.LATE_PAYMENT, refund.getReason());
        assertEquals(RefundStatus.DUE, refund.getStatus());
        assertEquals(200000, refund.getAmountKobo());
        assertEquals(tenant.getTenantId(), refund.getTenant().getTenantId());
    }

    @Test
    void testThatASecondSuccessfulPaymentForAnOrderAlreadyPaidBecomesADoublePaymentRefund() {
        Order order = support.order(tenant, OrderStatus.PREPARING, plate, 1);
        Payment first = support.payment(order, PaymentStatus.SUCCESSFUL);
        Payment second = support.payment(order, PaymentStatus.PENDING);

        webhooks.process(charge("charge.success", second));

        assertEquals(OrderStatus.PREPARING, orderStatus(second));
        assertEquals(PaymentStatus.SUCCESSFUL, reload(second).getStatus());
        assertEquals(RefundReason.DOUBLE_PAYMENT, refundRepo.findByPayment_Id(second.getId()).orElseThrow().getReason());
        assertTrue(refundRepo.findByPayment_Id(first.getId()).isEmpty());
    }

    @Test
    void testThatALateRefundIsNotCreatedTwiceByDuplicateNotices() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);
        webhooks.process(charge("charge.success", payment));
        webhooks.process(charge("charge.success", payment));
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmount().movePointRight(2).longValue(), payment.getReference()));
        paymentService.verifyAndSyncPayment(tenant.getTenantId(), payment.getReference());

        assertEquals(1, refundRepo.findByTenant_TenantIdOrderByCreatedAtDesc(tenant.getTenantId()).stream()
                .filter(r -> r.getPayment().getId().equals(payment.getId())).count());
        assertEquals(OrderStatus.CANCELED, orderStatus(payment));
    }

    @Test
    void testThatALatePaymentFoundByTheVerifyPathAlsoBecomesARefund() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.CANCELED, plate, 1), PaymentStatus.PENDING);
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmount().movePointRight(2).longValue(), payment.getReference()));

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(tenant.getTenantId(), payment.getReference());

        assertEquals(PaymentStatus.SUCCESSFUL, response.getPaymentStatus());
        assertEquals(OrderStatus.CANCELED, response.getOrderStatus());
        assertEquals(RefundReason.LATE_PAYMENT, refundRepo.findByPayment_Id(payment.getId()).orElseThrow().getReason());
    }

    @Test
    void testThatNoRefundNoticeFromPaystackIsActedOn() {
        Payment payment = support.payment(support.order(tenant, OrderStatus.PAID, plate, 1), PaymentStatus.SUCCESSFUL);
        webhooks.process("{\"event\":\"refund.processed\",\"data\":{\"transaction_reference\":\"" + payment.getReference() + "\"}}");
        webhooks.process("{\"event\":\"refund.failed\",\"data\":{\"transaction_reference\":\"" + payment.getReference() + "\"}}");
        assertEquals(PaymentStatus.SUCCESSFUL, reload(payment).getStatus());
        assertEquals(OrderStatus.PAID, orderStatus(payment));
        verifyNoInteractions(paystackClient);
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
