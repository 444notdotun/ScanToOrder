package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test mode: a restaurant Paystack could not make a subaccount for still takes payments, with no split.
@SpringBootTest(properties = {"app.payments.allow-unsplit=true", "paystack.secret.key=sk_test_unsplit_tests"})
@Transactional
class UnsplitPaymentsTest {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @Autowired
    private ChargeCalculator chargeCalculator;
    @Autowired
    private TenantWorkspaceService workspace;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant tenant;
    private Item plate;

    @BeforeEach
    void setUp() {
        clearInvocations(paystackClient);
        tenant = tenants.main();
        plate = support.item(tenant, "Unsplit plate", 1500, true);
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new PaystackInitResponseData("https://checkout.example/pay", invocation.getArgument(2)));
    }

    private InitializePaymentRequest request(Order order) {
        return new InitializePaymentRequest(order.getOrderId(), "guest@example.com");
    }

    @Test
    void testThatARestaurantWithoutASubaccountTakesAPaymentWithNoSplitAndTheChargeIsStillWorkedOut() {
        tenant.setPaystackSubaccountCode(null);
        tenantRepo.save(tenant);
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 2);

        InitializePaymentResponse response = paymentService.initializePayment(tenant.getTenantId(), null, request(order));

        verify(paystackClient).initialize(eq("guest@example.com"), eq(300_000L), eq(response.getReference()), eq(null));
        Payment payment = paymentRepo.findByReference(response.getReference()).orElseThrow();
        assertNull(payment.getSubaccountCode());
        assertEquals(chargeCalculator.chargeKobo(300_000), payment.getPlatelyChargeKobo());
        assertEquals(300_000, payment.getPlatelyChargeKobo() + payment.getTenantAmountKobo());
    }

    @Test
    void testThatARealSubaccountStillWinsWhenThereIsOne() {
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);

        InitializePaymentResponse response = paymentService.initializePayment(tenant.getTenantId(), null, request(order));

        ArgumentCaptor<PaystackSplit> split = ArgumentCaptor.forClass(PaystackSplit.class);
        verify(paystackClient).initialize(anyString(), anyLong(), eq(response.getReference()), split.capture());
        assertEquals(TenantTestSupport.SUBACCOUNT, split.getValue().subaccountCode());
        assertEquals(TenantTestSupport.SUBACCOUNT, paymentRepo.findByReference(response.getReference()).orElseThrow().getSubaccountCode());
    }

    @Test
    void testThatTheManagersSettingsSayTestPaymentsAreInUseOnlyWithoutASubaccount() {
        assertFalse(workspace.getMyTenant(tenant.getTenantId()).isTestPaymentsUnsplit());

        tenant.setPaystackSubaccountCode(null);
        tenantRepo.save(tenant);

        assertTrue(workspace.getMyTenant(tenant.getTenantId()).isTestPaymentsUnsplit());
    }

    @Test
    void testThatTheSettingWithALiveKeyStopsTheAppFromStarting() {
        UnsplitPayments live = new UnsplitPayments();
        ReflectionTestUtils.setField(live, "allowed", true);
        ReflectionTestUtils.setField(live, "paystackSecretKey", "sk_live_abc123");

        IllegalStateException exception = assertThrows(IllegalStateException.class, live::validate);

        assertTrue(exception.getMessage().contains("PAYMENTS_ALLOW_UNSPLIT"));
        assertTrue(exception.getMessage().contains("sk_test_"));
    }

    @Test
    void testThatTheSettingOffNeverAppliesEvenWithATestKey() {
        UnsplitPayments off = new UnsplitPayments();
        ReflectionTestUtils.setField(off, "allowed", false);
        ReflectionTestUtils.setField(off, "paystackSecretKey", "sk_test_abc");

        assertDoesNotThrow(off::validate);
        assertFalse(off.active());
    }
}
