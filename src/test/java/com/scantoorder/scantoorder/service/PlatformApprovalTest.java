package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.TenantActionResponse;
import com.scantoorder.scantoorder.dtos.respond.TenantSummaryResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import com.scantoorder.scantoorder.service.email.OutboxMessage;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PlatformApprovalTest {
    @Autowired
    private PlatformService platformService;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TenantTestSupport tenants;
    @MockitoBean
    private PaystackClient paystackClient;
    @Autowired
    private MenuService menuService;
    @Autowired
    private WebApplicationContext wac;
    @MockitoSpyBean
    private InMemoryEmailSender emailSender;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(paystackClient.createSubaccount(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn("ACCT_approved001");
        emailSender.clear();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(emailSender);
        SecurityContextHolder.clearContext();
    }

    private OutboxMessage lastEmailTo(String address) {
        return emailSender.messages().stream().filter(m -> m.to().equals(address)).findFirst().orElseThrow();
    }

    private String passwordIn(OutboxMessage message) {
        Matcher matcher = Pattern.compile("Temporary password: (\\S+)").matcher(message.body());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    @Test
    void testThatApprovalMakesTheBusinessActiveAndCreatesItsManagerWithATemporaryPassword() {
        Tenant pending = tenants.pendingTenant("Approve Me");

        TenantActionResponse response = platformService.approve(pending.getTenantId());

        assertEquals("ACTIVE", response.getStatus());
        assertTrue(response.isEmailSent());
        assertEquals(pending.getSlug() + ".manager", response.getManagerUsername());
        Tenant approved = tenantRepo.findById(pending.getTenantId()).orElseThrow();
        assertEquals(TenantStatus.ACTIVE, approved.getStatus());
        assertNotNull(approved.getApprovedAt());
        assertFalse(approved.isOpenForOrders());

        Worker manager = workerRepo.findByUsername(pending.getSlug() + ".manager").orElseThrow();
        assertEquals(WorkerRole.MANAGER, manager.getRole());
        assertTrue(manager.isMustChangePassword());
        assertEquals(pending.getTenantId(), manager.getTenant().getTenantId());

        OutboxMessage email = lastEmailTo(pending.getEmail());
        String password = passwordIn(email);
        assertTrue(password.length() >= 12);
        assertNotEquals(password, manager.getPassword());
        assertTrue(passwordEncoder.matches(password, manager.getPassword()));
        assertTrue(email.body().contains("Username: " + pending.getSlug() + ".manager"));
        assertTrue(email.body().contains("/login"));
        assertTrue(email.body().contains("choose your own password"));
        assertFalse(response.getMessage().contains(password));
    }

    @Test
    void testThatApprovingARestaurantCreatesItsPaystackSubaccountFromItsBankDetails() {
        Tenant pending = tenants.pendingTenant("Subaccount Ok");

        platformService.approve(pending.getTenantId());

        Mockito.verify(paystackClient).createSubaccount(Mockito.eq(pending.getName()), Mockito.eq("058"), Mockito.eq("0123456789"),
                Mockito.argThat(percent -> percent.compareTo(new java.math.BigDecimal("2.0")) == 0));
        assertEquals("ACCT_approved001", tenantRepo.findById(pending.getTenantId()).orElseThrow().getPaystackSubaccountCode());
    }

    @Test
    void testThatWhenPaystackRefusesThePayoutAccountTheApprovalIsRefusedAndNothingChanges() {
        Tenant pending = tenants.pendingTenant("Subaccount No");
        Mockito.when(paystackClient.createSubaccount(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenThrow(new com.scantoorder.scantoorder.exception.PayoutAccountRejectedException(PaystackClientImpl.PAYOUT_REJECTED));

        com.scantoorder.scantoorder.exception.PayoutAccountRejectedException exception = assertThrows(com.scantoorder.scantoorder.exception.PayoutAccountRejectedException.class,
                () -> platformService.approve(pending.getTenantId()));

        assertEquals("Paystack rejected this business's payout account. Ask them to check it and send the form again.", exception.getMessage());
        Tenant unchanged = tenantRepo.findById(pending.getTenantId()).orElseThrow();
        assertEquals(TenantStatus.PENDING_APPROVAL, unchanged.getStatus());
        assertNull(unchanged.getPaystackSubaccountCode());
        assertNull(unchanged.getApprovedAt());
        assertFalse(workerRepo.existsByUsername(pending.getSlug() + ".manager"));
        assertTrue(emailSender.messages().stream().noneMatch(m -> m.to().equals(pending.getEmail())));
    }

    @Test
    void testThatARestaurantWithNoBankDetailsOnFileCannotBeApproved() {
        Tenant pending = tenants.pendingTenant("No Bank On File");
        pending.setBankCode(null);
        pending.setAccountNumber(null);
        tenantRepo.save(pending);
        assertThrows(com.scantoorder.scantoorder.exception.PayoutAccountRejectedException.class, () -> platformService.approve(pending.getTenantId()));
        assertEquals(TenantStatus.PENDING_APPROVAL, tenantRepo.findById(pending.getTenantId()).orElseThrow().getStatus());
        Mockito.verify(paystackClient, Mockito.never()).createSubaccount(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testThatAnEventPlannerGetsNoSubaccount() {
        Tenant pending = tenants.pendingTenant("Events Co");
        pending.setType(com.scantoorder.scantoorder.data.model.TenantType.EVENT_PLANNER);
        pending.setBankCode(null);
        pending.setAccountNumber(null);
        tenantRepo.save(pending);

        platformService.approve(pending.getTenantId());

        Mockito.verify(paystackClient, Mockito.never()).createSubaccount(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        assertNull(tenantRepo.findById(pending.getTenantId()).orElseThrow().getPaystackSubaccountCode());
        assertEquals(TenantStatus.ACTIVE, tenantRepo.findById(pending.getTenantId()).orElseThrow().getStatus());
    }

    @Test
    void testThatAnAlreadyApprovedApplicationDoesNotCreateASecondSubaccount() {
        Tenant pending = tenants.pendingTenant("Once Sub");
        platformService.approve(pending.getTenantId());
        assertThrows(ConflictException.class, () -> platformService.approve(pending.getTenantId()));
        Mockito.verify(paystackClient, Mockito.times(1)).createSubaccount(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testThatOnlyAWaitingApplicationCanBeApproved() {
        Tenant pending = tenants.pendingTenant("Once Only");
        platformService.approve(pending.getTenantId());
        assertThrows(ConflictException.class, () -> platformService.approve(pending.getTenantId()));

        Tenant declined = tenants.pendingTenant("Declined First");
        platformService.decline(declined.getTenantId(), "Not enough detail");
        assertThrows(ConflictException.class, () -> platformService.approve(declined.getTenantId()));
        assertFalse(workerRepo.existsByUsername(declined.getSlug() + ".manager"));
    }

    @Test
    void testThatDecliningNeedsAReasonAndTellsTheOwner() {
        Tenant pending = tenants.pendingTenant("Decline Me");
        assertThrows(InvalidRequestException.class, () -> platformService.decline(pending.getTenantId(), " "));

        TenantActionResponse response = platformService.decline(pending.getTenantId(), "We could not read your phone number");

        assertEquals("DECLINED", response.getStatus());
        Tenant declined = tenantRepo.findById(pending.getTenantId()).orElseThrow();
        assertEquals("We could not read your phone number", declined.getDeclineReason());
        OutboxMessage email = lastEmailTo(pending.getEmail());
        assertTrue(email.body().contains("We could not read your phone number"));
        assertTrue(email.body().contains("send the form again"));
        assertFalse(workerRepo.existsByUsername(pending.getSlug() + ".manager"));
        assertThrows(ConflictException.class, () -> platformService.decline(pending.getTenantId(), "again"));
    }

    @Test
    void testThatApprovalStandsWhenTheEmailCannotBeSentAndSignInCanBeResent() {
        Tenant pending = tenants.pendingTenant("Mail Down");
        Mockito.doThrow(new EmailSendException("The email could not be sent")).when(emailSender).send(Mockito.eq(pending.getEmail()), Mockito.anyString(), Mockito.anyString());

        TenantActionResponse response = platformService.approve(pending.getTenantId());

        assertEquals("ACTIVE", response.getStatus());
        assertFalse(response.isEmailSent());
        assertTrue(response.getMessage().contains("email could not be sent"));
        assertTrue(workerRepo.existsByUsername(pending.getSlug() + ".manager"));
        String oldHash = workerRepo.findByUsername(pending.getSlug() + ".manager").orElseThrow().getPassword();

        Mockito.doCallRealMethod().when(emailSender).send(Mockito.eq(pending.getEmail()), Mockito.anyString(), Mockito.anyString());
        TenantActionResponse resent = platformService.resendSignIn(pending.getTenantId());

        assertTrue(resent.isEmailSent());
        Worker manager = workerRepo.findByUsername(pending.getSlug() + ".manager").orElseThrow();
        assertNotEquals(oldHash, manager.getPassword());
        assertTrue(manager.isMustChangePassword());
        assertTrue(passwordEncoder.matches(passwordIn(lastEmailTo(pending.getEmail())), manager.getPassword()));
    }

    @Test
    void testThatAResentSignInReplacesTheOldPassword() {
        Tenant pending = tenants.pendingTenant("Resend");
        platformService.approve(pending.getTenantId());
        String first = passwordIn(lastEmailTo(pending.getEmail()));

        platformService.resendSignIn(pending.getTenantId());

        String second = passwordIn(lastEmailTo(pending.getEmail()));
        assertNotEquals(first, second);
        Worker manager = workerRepo.findByUsername(pending.getSlug() + ".manager").orElseThrow();
        assertFalse(passwordEncoder.matches(first, manager.getPassword()));
        assertTrue(passwordEncoder.matches(second, manager.getPassword()));
    }

    @Test
    void testThatABusinessWithNoSignInYetCannotHaveOneResent() {
        Tenant pending = tenants.pendingTenant("Not Yet");
        assertThrows(ConflictException.class, () -> platformService.resendSignIn(pending.getTenantId()));
    }

    @Test
    void testThatSuspendAndReinstateOnlyMoveBetweenActiveAndSuspended() {
        Tenant pending = tenants.pendingTenant("Suspend Me");
        assertThrows(ConflictException.class, () -> platformService.suspend(pending.getTenantId()));
        platformService.approve(pending.getTenantId());

        assertThrows(ConflictException.class, () -> platformService.reinstate(pending.getTenantId()));
        assertEquals("SUSPENDED", platformService.suspend(pending.getTenantId()).getStatus());
        assertThrows(ConflictException.class, () -> platformService.suspend(pending.getTenantId()));
        assertEquals("ACTIVE", platformService.reinstate(pending.getTenantId()).getStatus());
        assertEquals(TenantStatus.ACTIVE, tenantRepo.findById(pending.getTenantId()).orElseThrow().getStatus());
    }

    @Test
    void testThatTheListCanBeFilteredByStatusAndNeverCarriesAccountNumbers() {
        Tenant pending = tenants.pendingTenant("Listed");
        pending.setAccountNumber("0123456789");
        tenantRepo.save(pending);

        List<TenantSummaryResponse> waiting = platformService.listTenants("pending_approval");

        assertTrue(waiting.stream().anyMatch(t -> t.getTenantId().equals(pending.getTenantId())));
        assertTrue(waiting.stream().allMatch(t -> t.getStatus().equals("PENDING_APPROVAL")));
        assertTrue(platformService.listTenants(null).size() >= waiting.size());
        assertThrows(InvalidRequestException.class, () -> platformService.listTenants("nonsense"));
        assertTrue(Arrays.stream(TenantSummaryResponse.class.getDeclaredFields()).noneMatch(f -> f.getName().toLowerCase().contains("account")));
        assertTrue(Arrays.stream(TenantSummaryResponse.class.getDeclaredFields()).noneMatch(f -> f.getName().toLowerCase().contains("bank")));
    }

    @Test
    void testThatApprovedBusinessesGetAnActiveMenuToBuildOn() {
        Tenant pending = tenants.pendingTenant("Menu Ready");
        platformService.approve(pending.getTenantId());
        assertNotNull(menuService.ensureActiveMenu(tenantRepo.findById(pending.getTenantId()).orElseThrow()));
    }

    private int statusFor(String path, String role, String tenantId) throws Exception {
        WorkerPrincipal principal = new WorkerPrincipal("w-" + role, role, tenantId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        return mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.GET, path)).andReturn().getResponse().getStatus();
    }

    @Test
    void testThatPlatformEndpointsBelongToThePlatformAdminAndTenantEndpointsToTheTenant() throws Exception {
        assertEquals(200, statusFor("/api/v1/platform/tenants", "PLATFORM_ADMIN", null));
        assertEquals(200, statusFor("/api/v1/platform/outbox", "PLATFORM_ADMIN", null));
        for (String role : List.of("MANAGER", "WAITER", "CHEF")) {
            assertEquals(403, statusFor("/api/v1/platform/tenants", role, tenants.mainId()), role);
            assertEquals(403, statusFor("/api/v1/platform/outbox", role, tenants.mainId()), role);
        }
        assertEquals(403, statusFor("/api/v1/tenant", "PLATFORM_ADMIN", null));
        assertEquals(403, statusFor("/api/v1/tables", "PLATFORM_ADMIN", null));
        assertEquals(403, statusFor("/api/v1/workers", "PLATFORM_ADMIN", null));
        assertEquals(403, statusFor("/api/v1/categories", "PLATFORM_ADMIN", null));
        assertEquals(200, statusFor("/api/v1/tenant", "MANAGER", tenants.mainId()));
    }
}
