package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.PaymentGatewayUnavailableException;
import com.scantoorder.scantoorder.exception.PaymentValidationException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class PaymentSplitTest {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @Autowired
    private ChargeCalculator chargeCalculator;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant tenant;
    private Item plate;

    @BeforeEach
    void setUp() {
        tenant = tenants.main();
        plate = support.item(tenant, "Split plate", 1500, true);
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new PaystackInitResponseData("https://checkout.example/pay", invocation.getArgument(2)));
    }

    private InitializePaymentRequest request(Order order) {
        return new InitializePaymentRequest(order.getOrderId(), "guest@example.com");
    }

    @Test
    void testThatInitializeSendsTheSubaccountTheChargeAndTheBearer() {
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 2);

        InitializePaymentResponse response = paymentService.initializePayment(tenant.getTenantId(), null, request(order));

        ArgumentCaptor<PaystackSplit> split = ArgumentCaptor.forClass(PaystackSplit.class);
        verify(paystackClient).initialize(eq("guest@example.com"), eq(300_000L), eq(response.getReference()), split.capture());
        assertEquals(TenantTestSupport.SUBACCOUNT, split.getValue().subaccountCode());
        assertEquals(chargeCalculator.chargeKobo(300_000), split.getValue().platelyChargeKobo());
        assertEquals(chargeCalculator.feeBearer(), split.getValue().bearer());
    }

    @Test
    void testThatThePaymentRecordsTheSplitItWasStartedWith() {
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);

        InitializePaymentResponse response = paymentService.initializePayment(tenant.getTenantId(), null, request(order));

        Payment payment = paymentRepo.findByReference(response.getReference()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(TenantTestSupport.SUBACCOUNT, payment.getSubaccountCode());
        long charge = chargeCalculator.chargeKobo(150_000);
        assertEquals(charge, payment.getPlatelyChargeKobo());
        assertEquals(150_000 - charge, payment.getTenantAmountKobo());
        assertEquals(150_000, payment.getPlatelyChargeKobo() + payment.getTenantAmountKobo());
    }

    @Test
    void testThatARestaurantWithoutASubaccountCannotTakeAPayment() {
        tenant.setPaystackSubaccountCode(null);
        tenantRepo.save(tenant);
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> paymentService.initializePayment(tenant.getTenantId(), null, request(order)));

        assertEquals("This place can't take payments yet. Ask a waiter.", exception.getMessage());
        verifyNoInteractions(paystackClient);
        assertTrue(paymentRepo.findAll().stream().noneMatch(p -> p.getOrder().getOrderId().equals(order.getOrderId())));
    }

    @Test
    void testThatNothingIsSavedWhenPaystackRefusesOrIsDown() {
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any())).thenThrow(new PaymentGatewayUnavailableException("down"));

        assertThrows(PaymentGatewayUnavailableException.class, () -> paymentService.initializePayment(tenant.getTenantId(), null, request(order)));
        assertTrue(paymentRepo.findAll().stream().noneMatch(p -> p.getOrder().getOrderId().equals(order.getOrderId())));
    }

    @Test
    void testThatAnOrderOfAnotherBusinessOrAnAlreadyCancelledOrderCannotBePaid() {
        Tenant other = tenants.other();
        Order theirs = support.order(other, OrderStatus.PENDING_PAYMENT, support.item(other, "Their plate", 900, true), 1);
        assertThrows(ResourceNotFoundException.class, () -> paymentService.initializePayment(tenant.getTenantId(), null, request(theirs)));

        Order cancelled = support.order(tenant, OrderStatus.CANCELED, plate, 1);
        assertThrows(PaymentValidationException.class, () -> paymentService.initializePayment(tenant.getTenantId(), null, request(cancelled)));
        verifyNoInteractions(paystackClient);
    }

    @Test
    void testThatAGuestCanOnlyPayForOrdersOfTheirOwnSession() {
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);
        assertThrows(ResourceNotFoundException.class, () -> paymentService.initializePayment(tenant.getTenantId(), "someone-elses-session", request(order)));
    }
}
