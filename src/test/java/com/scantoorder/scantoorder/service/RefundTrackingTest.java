package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.dtos.request.GuestBankDetailsRequest;
import com.scantoorder.scantoorder.dtos.request.SettleRefundRequest;
import com.scantoorder.scantoorder.dtos.respond.GuestRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.ManagerRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.PlatformRefundResponse;
import com.scantoorder.scantoorder.exception.AccountVerificationException;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.RefundTrackingService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class RefundTrackingTest {
    private static final String ACCOUNT = "0123456789";

    @Autowired
    private RefundTrackingService service;
    @Autowired
    private RefundService refundService;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @Autowired
    private WebApplicationContext wac;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("springSecurityFilterChain")
    private Filter securityFilters;
    @MockitoBean
    private PaystackClient paystackClient;
    @MockitoBean
    private BankResolver bankResolver;

    private Tenant tenant;
    private String tenantId;
    private Item plate;

    @BeforeEach
    void setUp() {
        // The seeder spoke to the mock while the app started; only what the test does counts.
        clearInvocations(paystackClient);
        tenant = tenants.main();
        tenantId = tenant.getTenantId();
        plate = support.item(tenant, "Refund plate", 2500, true);
        when(bankResolver.resolve(anyString(), anyString())).thenReturn(new BankResolver.Resolved("Zenith Bank", "ADA OKAFOR"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Refund due(OrderStatus orderStatus, RefundReason reason) {
        Payment payment = support.paidOrder(tenant, orderStatus, plate);
        Refund refund = refundService.createDue(payment, reason);
        // Most refunds here already have the guest's email; the email tests make their own without.
        refund.setGuestEmail("guest@example.com");
        return refundRepo.save(refund);
    }

    private Refund reload(Refund refund) {
        return refundRepo.findById(refund.getId()).orElseThrow();
    }

    private OrderStatus orderStatus(Refund refund) {
        return orderRepo.findOrderByOrderId(refund.getOrder().getOrderId()).getOrderStatus();
    }

    private SettleRefundRequest settleWith(RefundMethod method, String reference) {
        SettleRefundRequest request = new SettleRefundRequest();
        request.setMethod(method);
        request.setTransferReference(reference);
        return request;
    }

    private GuestBankDetailsRequest bank() {
        GuestBankDetailsRequest request = new GuestBankDetailsRequest();
        request.setBankCode("057");
        request.setAccountNumber(ACCOUNT);
        return request;
    }

    // ---- the guest's link

    @Test
    void testThatTheGuestLinkShowsTheRestaurantOrderAmountAndWhatToDoWithTheRightToken() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);

        GuestRefundResponse view = service.guestView(refund.getId(), refund.getGuestToken());

        assertEquals("Test Place", view.getRestaurant());
        assertEquals(refund.getOrder().getOrderNumber(), view.getOrderNumber());
        assertEquals(2500, view.getAmount().intValue());
        assertEquals("DUE", view.getStatus());
        assertEquals(List.of("1 x Refund plate"), view.getItems());
        assertTrue(view.getWhatToDo().contains("Test Place owes you ₦2,500.00"));
        assertTrue(view.isCanAddBankDetails());
        assertTrue(view.isCanConfirmReceived());
        assertFalse(view.isCanReportMissing());
    }

    @Test
    void testThatAWrongTokenOrAWrongIdAnswersNotFoundForEveryGuestAction() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        Refund other = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);

        for (String[] wrong : new String[][]{{refund.getId(), "wrong-token"}, {refund.getId(), other.getGuestToken()}, {"no-such-refund", refund.getGuestToken()}}) {
            assertThrows(ResourceNotFoundException.class, () -> service.guestView(wrong[0], wrong[1]));
            assertThrows(ResourceNotFoundException.class, () -> service.guestBankDetails(wrong[0], wrong[1], bank()));
            assertThrows(ResourceNotFoundException.class, () -> service.guestReceived(wrong[0], wrong[1]));
            assertThrows(ResourceNotFoundException.class, () -> service.guestNotReceived(wrong[0], wrong[1]));
        }
        assertEquals(RefundStatus.DUE, reload(refund).getStatus());
    }

    @Test
    void testThatTheGuestLinkWorksWithNoSignInAndAWrongTokenIsNotFoundOverHttp() throws Exception {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        MockMvc open = MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilters).build();

        assertEquals(200, open.perform(MockMvcRequestBuilders.get("/api/v1/refunds/{id}/guest", refund.getId())
                .param("t", refund.getGuestToken())).andReturn().getResponse().getStatus());
        assertEquals(404, open.perform(MockMvcRequestBuilders.get("/api/v1/refunds/{id}/guest", refund.getId())
                .param("t", "nope")).andReturn().getResponse().getStatus());
        assertEquals(404, open.perform(MockMvcRequestBuilders.post("/api/v1/refunds/{id}/guest/received", refund.getId())
                .param("t", "nope")).andReturn().getResponse().getStatus());
        // The manager's side is not open to the public.
        assertTrue(open.perform(MockMvcRequestBuilders.get("/api/v1/refunds")).andReturn().getResponse().getStatus() >= 401);
        assertTrue(open.perform(MockMvcRequestBuilders.post("/api/v1/refunds/{id}/settle", refund.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}")).andReturn().getResponse().getStatus() >= 401);
        assertEquals(RefundStatus.DUE, reload(refund).getStatus());
    }

    // ---- bank details

    @Test
    void testThatBankDetailsAreCheckedWithTheBankAndTheAccountNameIsKept() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);

        GuestRefundResponse view = service.guestBankDetails(refund.getId(), refund.getGuestToken(), bank());

        verify(bankResolver).resolve("057", ACCOUNT);
        Refund saved = reload(refund);
        assertEquals("057", saved.getGuestBankCode());
        assertEquals("Zenith Bank", saved.getGuestBankName());
        assertEquals("ADA OKAFOR", saved.getGuestAccountName());
        assertEquals(ACCOUNT, saved.getGuestAccountNumber());
        // The guest's own page shows the last four digits only.
        assertEquals("6789", view.getAccountLast4());
        assertFalse(view.toString().contains(ACCOUNT));
    }

    @Test
    void testThatBankDetailsTheBankRefusesAreNotKept() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        when(bankResolver.resolve(anyString(), anyString())).thenThrow(new AccountVerificationException("The bank couldn't find that account."));

        assertThrows(AccountVerificationException.class, () -> service.guestBankDetails(refund.getId(), refund.getGuestToken(), bank()));

        assertNull(reload(refund).getGuestAccountNumber());
        assertNull(reload(refund).getGuestAccountName());
    }

    @Test
    void testThatBankDetailsCannotBeAddedToARefundThatIsClosed() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        service.guestReceived(refund.getId(), refund.getGuestToken());
        assertThrows(ConflictException.class, () -> service.guestBankDetails(refund.getId(), refund.getGuestToken(), bank()));
        assertNull(reload(refund).getGuestAccountNumber());
    }

    // ---- settling

    @Test
    void testThatTheManagerSettlesByCashAndAKitchenFailureOrderBecomesRefunded() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);

        ManagerRefundResponse response = service.settle(tenantId, "worker-1", refund.getId(), settleWith(RefundMethod.CASH, "ignored"));

        Refund saved = reload(refund);
        assertEquals(RefundStatus.SETTLED, saved.getStatus());
        assertEquals(RefundMethod.CASH, saved.getMethod());
        assertNull(saved.getTransferReference());
        assertEquals("worker-1", saved.getSettledBy());
        assertEquals(RefundClosedBy.MANAGER, saved.getClosedBy());
        assertNotNull(saved.getSettledAt());
        assertEquals("SETTLED", response.getStatus());
        assertEquals(OrderStatus.REFUNDED, orderStatus(refund));
        verifyNoInteractions(paystackClient);
    }

    @Test
    void testThatTheManagerSettlesByTransferWithTheBanksReference() {
        Refund refund = due(OrderStatus.CANCELED, RefundReason.LATE_PAYMENT);

        service.settle(tenantId, "worker-1", refund.getId(), settleWith(RefundMethod.TRANSFER, "  TRF-998877  "));

        Refund saved = reload(refund);
        assertEquals(RefundMethod.TRANSFER, saved.getMethod());
        assertEquals("TRF-998877", saved.getTransferReference());
        // A late payment's order stays cancelled: only a kitchen failure ends as refunded.
        assertEquals(OrderStatus.CANCELED, orderStatus(refund));
    }

    @Test
    void testThatATransferWithoutAReferenceIsRefusedAndNothingChanges() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);

        assertThrows(InvalidRequestException.class, () -> service.settle(tenantId, "w", refund.getId(), settleWith(RefundMethod.TRANSFER, null)));
        assertThrows(InvalidRequestException.class, () -> service.settle(tenantId, "w", refund.getId(), settleWith(RefundMethod.TRANSFER, "   ")));

        assertEquals(RefundStatus.DUE, reload(refund).getStatus());
        assertEquals(OrderStatus.REFUND_DUE, orderStatus(refund));
    }

    @Test
    void testThatSettlingTwiceOrSettlingAConfirmedRefundIsRefused() {
        Refund settled = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        service.settle(tenantId, "w", settled.getId(), settleWith(RefundMethod.CASH, null));
        assertThrows(ConflictException.class, () -> service.settle(tenantId, "w", settled.getId(), settleWith(RefundMethod.CASH, null)));

        Refund confirmed = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        service.guestReceived(confirmed.getId(), confirmed.getGuestToken());
        assertThrows(ConflictException.class, () -> service.settle(tenantId, "w", confirmed.getId(), settleWith(RefundMethod.CASH, null)));
        assertEquals(RefundClosedBy.GUEST, reload(confirmed).getClosedBy());
    }

    // ---- the guest confirms

    @Test
    void testThatTheGuestConfirmingWhileDueClosesItAsAGuestClosedCashRefund() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);

        GuestRefundResponse view = service.guestReceived(refund.getId(), refund.getGuestToken());

        Refund saved = reload(refund);
        assertEquals(RefundStatus.CONFIRMED, saved.getStatus());
        assertEquals(RefundClosedBy.GUEST, saved.getClosedBy());
        assertNotNull(saved.getGuestConfirmedAt());
        assertEquals(OrderStatus.REFUNDED, orderStatus(refund));
        assertEquals("CONFIRMED", view.getStatus());
        assertTrue(service.list(tenantId, "open").stream().noneMatch(r -> r.getRefundId().equals(refund.getId())));
        assertTrue(service.list(tenantId, "closed").stream().anyMatch(r -> r.getRefundId().equals(refund.getId())));
    }

    @Test
    void testThatTheGuestConfirmingAfterTheManagerSettledKeepsTheManagerAsWhoClosedIt() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        service.settle(tenantId, "w", refund.getId(), settleWith(RefundMethod.CASH, null));

        service.guestReceived(refund.getId(), refund.getGuestToken());

        Refund saved = reload(refund);
        assertEquals(RefundStatus.CONFIRMED, saved.getStatus());
        assertEquals(RefundClosedBy.MANAGER, saved.getClosedBy());
        assertNotNull(saved.getGuestConfirmedAt());
        assertThrows(ConflictException.class, () -> service.guestReceived(refund.getId(), refund.getGuestToken()));
    }

    @Test
    void testThatAGuestCanOnlySayNothingArrivedOnceTheRestaurantSaysItSent() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        assertThrows(ConflictException.class, () -> service.guestNotReceived(refund.getId(), refund.getGuestToken()));
        assertNull(reload(refund).getGuestReportedMissingAt());

        service.settle(tenantId, "w", refund.getId(), settleWith(RefundMethod.TRANSFER, "TRF-1"));
        GuestRefundResponse view = service.guestNotReceived(refund.getId(), refund.getGuestToken());

        assertNotNull(reload(refund).getGuestReportedMissingAt());
        assertTrue(view.isReportedMissing());
        assertFalse(view.isCanReportMissing());
        Refund confirmed = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        service.guestReceived(confirmed.getId(), confirmed.getGuestToken());
        assertThrows(ConflictException.class, () -> service.guestNotReceived(confirmed.getId(), confirmed.getGuestToken()));
    }

    @Test
    void testThatARefundReportedMissingIsOpenAgainOnTheManagersListAndOnThePlatformListUntilTheGuestConfirms() {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        service.settle(tenantId, "w", refund.getId(), settleWith(RefundMethod.TRANSFER, "TRF-2"));
        assertTrue(service.list(tenantId, "open").stream().noneMatch(r -> r.getRefundId().equals(refund.getId())));
        assertTrue(service.platformProblems().stream().noneMatch(r -> r.getRefundId().equals(refund.getId())));

        service.guestNotReceived(refund.getId(), refund.getGuestToken());

        ManagerRefundResponse open = service.list(tenantId, "open").stream().filter(r -> r.getRefundId().equals(refund.getId())).findFirst().orElseThrow();
        assertTrue(open.isReportedMissing());
        assertEquals("SETTLED", open.getStatus());
        assertTrue(service.list(tenantId, "closed").stream().noneMatch(r -> r.getRefundId().equals(refund.getId())));
        PlatformRefundResponse problem = service.platformProblems().stream().filter(r -> r.getRefundId().equals(refund.getId())).findFirst().orElseThrow();
        assertTrue(problem.isReportedMissing());
        assertEquals("Test Place", problem.getTenantName());
        assertEquals(2500, problem.getAmount().intValue());

        service.guestReceived(refund.getId(), refund.getGuestToken());

        assertTrue(service.platformProblems().stream().noneMatch(r -> r.getRefundId().equals(refund.getId())));
        assertTrue(service.list(tenantId, "open").stream().noneMatch(r -> r.getRefundId().equals(refund.getId())));
    }

    // ---- the manager's list

    @Test
    void testThatTheOpenAndClosedFiltersSplitTheListAndAllShowsEverythingNewestFirst() {
        Refund isDue = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        isDue.setCreatedAt(LocalDateTime.now().minusHours(3));
        refundRepo.save(isDue);
        Refund settled = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        settled.setCreatedAt(LocalDateTime.now().minusHours(2));
        refundRepo.save(settled);
        service.settle(tenantId, "w", settled.getId(), settleWith(RefundMethod.CASH, null));
        Refund confirmed = due(OrderStatus.CANCELED, RefundReason.LATE_PAYMENT);
        confirmed.setCreatedAt(LocalDateTime.now().minusHours(1));
        refundRepo.save(confirmed);
        service.guestReceived(confirmed.getId(), confirmed.getGuestToken());

        List<String> open = service.list(tenantId, null).stream().map(ManagerRefundResponse::getRefundId).toList();
        List<String> closed = service.list(tenantId, "closed").stream().map(ManagerRefundResponse::getRefundId).toList();
        List<String> all = service.list(tenantId, "all").stream().map(ManagerRefundResponse::getRefundId).toList();

        assertTrue(open.contains(isDue.getId()));
        assertFalse(open.contains(settled.getId()) || open.contains(confirmed.getId()));
        assertTrue(closed.containsAll(List.of(settled.getId(), confirmed.getId())));
        assertFalse(closed.contains(isDue.getId()));
        assertTrue(all.containsAll(List.of(isDue.getId(), settled.getId(), confirmed.getId())));
        assertTrue(all.indexOf(confirmed.getId()) < all.indexOf(settled.getId()));
        assertTrue(all.indexOf(settled.getId()) < all.indexOf(isDue.getId()));
        assertThrows(InvalidRequestException.class, () -> service.list(tenantId, "sideways"));
    }

    @Test
    void testThatTheManagersListShowsTheGuestTheOrderAndTheBankDetailsInFull() {
        Payment payment = support.paidOrder(tenant, OrderStatus.REFUND_DUE, plate);
        Refund refund = refundService.createDue(payment, RefundReason.KITCHEN_UNAVAILABLE);
        refund.setGuestEmail("guest@example.com");
        refundRepo.save(refund);
        service.guestBankDetails(refund.getId(), refund.getGuestToken(), bank());

        ManagerRefundResponse row = service.list(tenantId, "open").stream().filter(r -> r.getRefundId().equals(refund.getId())).findFirst().orElseThrow();

        assertEquals(payment.getReference(), row.getPaymentReference());
        assertEquals(payment.getOrder().getOrderNumber(), row.getOrderNumber());
        assertEquals(List.of("1 x Refund plate"), row.getItems());
        assertEquals("KITCHEN_UNAVAILABLE", row.getReason());
        assertEquals("DUE", row.getStatus());
        assertEquals(2500, row.getAmount().intValue());
        assertEquals("Zenith Bank", row.getGuestBankName());
        assertEquals("ADA OKAFOR", row.getGuestAccountName());
        assertEquals(ACCOUNT, row.getGuestAccountNumber());
        assertFalse(row.isOverdue());
    }

    @Test
    void testThatARefundDueForMoreThanFortyEightHoursIsOverdueAndOneYoungerIsNot() {
        Refund old = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        old.setCreatedAt(LocalDateTime.now().minusHours(49));
        refundRepo.save(old);
        Refund recent = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        recent.setCreatedAt(LocalDateTime.now().minusHours(47));
        refundRepo.save(recent);

        List<ManagerRefundResponse> open = service.list(tenantId, "open");

        assertTrue(open.stream().filter(r -> r.getRefundId().equals(old.getId())).findFirst().orElseThrow().isOverdue());
        assertFalse(open.stream().filter(r -> r.getRefundId().equals(recent.getId())).findFirst().orElseThrow().isOverdue());
        List<String> problems = service.platformProblems().stream().map(PlatformRefundResponse::getRefundId).toList();
        assertTrue(problems.contains(old.getId()));
        assertFalse(problems.contains(recent.getId()));
        // Once settled it is no longer overdue.
        service.settle(tenantId, "w", old.getId(), settleWith(RefundMethod.CASH, null));
        assertFalse(service.platformProblems().stream().anyMatch(r -> r.getRefundId().equals(old.getId())));
    }

    @Test
    void testThatThePlatformListSpansBusinessesAndNeverCarriesAccountNumbers() throws Exception {
        Tenant other = tenants.other();
        Item theirs = support.item(other, "Their refund plate", 900, true);
        Refund theirRefund = refundService.createDue(support.paidOrder(other, OrderStatus.CANCELED, theirs), RefundReason.LATE_PAYMENT);
        theirRefund.setCreatedAt(LocalDateTime.now().minusHours(60));
        refundRepo.save(theirRefund);
        Refund mine = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        mine.setCreatedAt(LocalDateTime.now().minusHours(60));
        refundRepo.save(mine);
        service.guestBankDetails(mine.getId(), mine.getGuestToken(), bank());

        List<PlatformRefundResponse> problems = service.platformProblems();

        assertTrue(problems.stream().anyMatch(r -> r.getRefundId().equals(theirRefund.getId()) && r.getTenantName().equals("Other Place")));
        assertTrue(problems.stream().anyMatch(r -> r.getRefundId().equals(mine.getId()) && r.getTenantName().equals("Test Place")));
        assertFalse(problems.toString().contains(ACCOUNT));
        assertTrue(java.util.Arrays.stream(PlatformRefundResponse.class.getDeclaredFields()).noneMatch(f -> f.getName().toLowerCase().contains("account")));
    }

    // ---- other businesses and roles

    @Test
    void testThatAnotherBusinessesRefundIsNotFoundToSettleAndNeverListed() {
        Tenant other = tenants.other();
        Item theirs = support.item(other, "Hidden refund plate", 900, true);
        Refund theirRefund = refundService.createDue(support.paidOrder(other, OrderStatus.REFUND_DUE, theirs), RefundReason.KITCHEN_UNAVAILABLE);

        assertThrows(ResourceNotFoundException.class, () -> service.settle(tenantId, "w", theirRefund.getId(), settleWith(RefundMethod.CASH, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.settle(tenantId, "w", "no-such-refund", settleWith(RefundMethod.CASH, null)));
        assertTrue(service.list(tenantId, "all").stream().noneMatch(r -> r.getRefundId().equals(theirRefund.getId())));
        assertEquals(RefundStatus.DUE, reload(theirRefund).getStatus());
    }

    @Test
    void testThatOnlyTheManagerListsAndSettlesAndOnlyThePlatformAdminSeesTheProblemList() throws Exception {
        Refund refund = due(OrderStatus.REFUND_DUE, RefundReason.KITCHEN_UNAVAILABLE);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        String settle = "{\"method\":\"CASH\"}";

        for (String role : List.of("CHEF", "WAITER", "CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/refunds")).andReturn().getResponse().getStatus(), role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/refunds/{id}/settle", refund.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(settle)).andReturn().getResponse().getStatus(), role);
        }
        assertEquals(RefundStatus.DUE, reload(refund).getStatus());

        for (String role : List.of("CHEF", "WAITER", "MANAGER", "CUSTOMER")) {
            signIn(role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/platform/refunds")).andReturn().getResponse().getStatus(), role);
        }
        signIn("PLATFORM_ADMIN");
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/platform/refunds?problem=true")).andReturn().getResponse().getStatus());

        signIn("MANAGER");
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/refunds")).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/refunds/{id}/settle", refund.getId())
                .contentType(MediaType.APPLICATION_JSON).content(settle)).andReturn().getResponse().getStatus());
        assertEquals(RefundStatus.SETTLED, reload(refund).getStatus());
        assertEquals("worker-MANAGER", reload(refund).getSettledBy());
    }

    private void signIn(String role) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", tenantId)
                : new WorkerPrincipal("worker-" + role, role, role.equals("PLATFORM_ADMIN") ? null : tenantId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }
}
