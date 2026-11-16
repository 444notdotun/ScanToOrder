package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.dtos.request.GuestBankDetailsRequest;
import com.scantoorder.scantoorder.dtos.request.GuestEmailRequest;
import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.GuestRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.ManagerRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.RefundTrackingService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class GuestEmailTest {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RefundTrackingService refunds;
    @Autowired
    private RefundService refundService;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @MockitoBean
    private PaystackClient paystackClient;
    @MockitoBean
    private BankResolver bankResolver;

    private Tenant tenant;
    private Item plate;

    @BeforeEach
    void setUp() {
        clearInvocations(paystackClient);
        tenant = tenants.main();
        plate = support.item(tenant, "Email plate two", 1500, true);
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new PaystackInitResponseData("https://checkout.example/pay", invocation.getArgument(2)));
        when(bankResolver.resolve(anyString(), anyString())).thenReturn(new BankResolver.Resolved("Zenith Bank", "ADA OKAFOR"));
    }

    private Payment startPayment(String email) {
        Order order = support.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);
        String reference = paymentService.initializePayment(tenant.getTenantId(), null, new InitializePaymentRequest(order.getOrderId(), email)).getReference();
        return paymentRepo.findByReference(reference).orElseThrow();
    }

    private Refund refundWithoutEmail() {
        return refundService.createDue(support.paidOrder(tenant, OrderStatus.REFUND_DUE, plate), RefundReason.KITCHEN_UNAVAILABLE);
    }

    private GuestEmailRequest email(String address) {
        GuestEmailRequest request = new GuestEmailRequest();
        request.setEmail(address);
        return request;
    }

    private GuestBankDetailsRequest bank() {
        GuestBankDetailsRequest request = new GuestBankDetailsRequest();
        request.setBankCode("057");
        request.setAccountNumber("0123456789");
        return request;
    }

    // ---- paying

    @Test
    void testThatPayingWithAnEmailSendsTheGuestsToPaystackAndStoresIt() {
        Payment payment = startPayment("guest@example.com");

        verify(paystackClient).initialize(eq("guest@example.com"), anyLong(), eq(payment.getReference()), any());
        assertEquals("guest@example.com", payment.getCustomerEmail());
    }

    @Test
    void testThatPayingWithoutAnEmailSendsTheRestaurantsAndStoresNothing() {
        for (String none : new String[]{null, "", "   "}) {
            clearInvocations(paystackClient);
            Payment payment = startPayment(none);

            verify(paystackClient).initialize(eq(tenant.getEmail()), anyLong(), eq(payment.getReference()), any());
            assertNull(payment.getCustomerEmail(), "stored for [" + none + "]");
        }
    }

    @Test
    void testThatTheEmailOnAPaymentRequestIsCheckedOnlyWhenThereIsOne() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(new InitializePaymentRequest("order-1", null)).isEmpty());
        assertTrue(validator.validate(new InitializePaymentRequest("order-1", "")).isEmpty());
        assertTrue(validator.validate(new InitializePaymentRequest("order-1", "guest@example.com")).isEmpty());
        assertFalse(validator.validate(new InitializePaymentRequest("order-1", "not-an-email")).isEmpty());
    }

    // ---- the refund takes the guest's email

    @Test
    void testThatAStoredGuestEmailIsCopiedToTheRefundAndTheRestaurantsStandInNeverIs() {
        Payment withEmail = startPayment("guest@example.com");
        Payment without = startPayment(null);

        Refund copied = refundService.createDue(withEmail, RefundReason.DOUBLE_PAYMENT);
        Refund bare = refundService.createDue(without, RefundReason.DOUBLE_PAYMENT);

        assertEquals("guest@example.com", copied.getGuestEmail());
        assertNull(bare.getGuestEmail());
        assertNotEquals(tenant.getEmail(), bare.getGuestEmail());
    }

    // ---- the email endpoint

    @Test
    void testThatTheGuestCanGiveAndChangeTheirEmailWithTheRightTokenOnly() {
        Refund refund = refundWithoutEmail();
        GuestRefundResponse before = refunds.guestView(refund.getId(), refund.getGuestToken());
        assertTrue(before.isNeedsEmail());
        assertEquals("Where should we tell you about your refund?", before.getWhatToDo());
        assertFalse(before.isCanAddBankDetails());
        assertFalse(before.isCanConfirmReceived());

        GuestRefundResponse after = refunds.guestEmail(refund.getId(), refund.getGuestToken(), email("  me@example.com "));
        assertFalse(after.isNeedsEmail());
        assertTrue(after.isCanAddBankDetails());
        assertEquals("me@example.com", refundRepo.findById(refund.getId()).orElseThrow().getGuestEmail());

        refunds.guestEmail(refund.getId(), refund.getGuestToken(), email("other@example.com"));
        assertEquals("other@example.com", refundRepo.findById(refund.getId()).orElseThrow().getGuestEmail());

        assertThrows(ResourceNotFoundException.class, () -> refunds.guestEmail(refund.getId(), "wrong-token", email("x@example.com")));
        assertThrows(ResourceNotFoundException.class, () -> refunds.guestEmail("no-such-refund", refund.getGuestToken(), email("x@example.com")));
        assertEquals("other@example.com", refundRepo.findById(refund.getId()).orElseThrow().getGuestEmail());
    }

    @Test
    void testThatAClosedRefundTakesNoNewEmail() {
        Refund refund = refundWithoutEmail();
        refunds.guestEmail(refund.getId(), refund.getGuestToken(), email("me@example.com"));
        refunds.guestReceived(refund.getId(), refund.getGuestToken());
        assertThrows(ConflictException.class, () -> refunds.guestEmail(refund.getId(), refund.getGuestToken(), email("new@example.com")));
        assertEquals("me@example.com", refundRepo.findById(refund.getId()).orElseThrow().getGuestEmail());
    }

    @Test
    void testThatBankDetailsReceivedAndNotReceivedAreRefusedUntilAnEmailIsGiven() {
        Refund refund = refundWithoutEmail();
        refund.setStatus(RefundStatus.SETTLED);
        refund.setSettledAt(java.time.LocalDateTime.now());
        refundRepo.save(refund);

        for (Runnable action : new Runnable[]{
                () -> refunds.guestBankDetails(refund.getId(), refund.getGuestToken(), bank()),
                () -> refunds.guestReceived(refund.getId(), refund.getGuestToken()),
                () -> refunds.guestNotReceived(refund.getId(), refund.getGuestToken())}) {
            ConflictException refused = assertThrows(ConflictException.class, action::run);
            assertEquals("Add your email first.", refused.getMessage());
        }
        assertNull(refundRepo.findById(refund.getId()).orElseThrow().getGuestAccountNumber());
        assertNull(refundRepo.findById(refund.getId()).orElseThrow().getGuestReportedMissingAt());
        assertEquals(RefundStatus.SETTLED, refundRepo.findById(refund.getId()).orElseThrow().getStatus());

        refunds.guestEmail(refund.getId(), refund.getGuestToken(), email("me@example.com"));
        refunds.guestBankDetails(refund.getId(), refund.getGuestToken(), bank());
        refunds.guestNotReceived(refund.getId(), refund.getGuestToken());
        refunds.guestReceived(refund.getId(), refund.getGuestToken());
        assertEquals(RefundStatus.CONFIRMED, refundRepo.findById(refund.getId()).orElseThrow().getStatus());
    }

    // ---- the manager

    @Test
    void testThatTheManagersListCarriesTheGuestLinkAndEmailAndTheManagerCanSettleWithoutAnEmail() {
        Refund refund = refundWithoutEmail();

        ManagerRefundResponse before = refunds.list(tenant.getTenantId(), "open").stream()
                .filter(r -> r.getRefundId().equals(refund.getId())).findFirst().orElseThrow();
        assertTrue(before.getGuestLink().endsWith("/refund/" + refund.getId() + "?t=" + refund.getGuestToken()));
        assertNull(before.getGuestEmail());

        var settleRequest = new com.scantoorder.scantoorder.dtos.request.SettleRefundRequest();
        settleRequest.setMethod(RefundMethod.CASH);
        refunds.settle(tenant.getTenantId(), "w", refund.getId(), settleRequest);
        assertEquals(RefundStatus.SETTLED, refundRepo.findById(refund.getId()).orElseThrow().getStatus());

        refunds.guestEmail(refund.getId(), refund.getGuestToken(), email("me@example.com"));
        assertEquals("me@example.com", refunds.list(tenant.getTenantId(), "closed").stream()
                .filter(r -> r.getRefundId().equals(refund.getId())).findFirst().orElseThrow().getGuestEmail());
    }
}
