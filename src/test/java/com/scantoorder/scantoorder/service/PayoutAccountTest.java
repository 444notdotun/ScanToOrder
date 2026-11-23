package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.PayoutAccountRequest;
import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;
import com.scantoorder.scantoorder.exception.AccountVerificationException;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.PayoutAccountRejectedException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import com.scantoorder.scantoorder.service.email.OutboxMessage;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class PayoutAccountTest {
    private static final String PASSWORD = "Right-Pass-1";
    private static final String NEW_ACCOUNT = "0011223344";

    @Autowired
    private TenantWorkspaceService workspace;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private WebApplicationContext wac;
    @MockitoBean
    private PaystackClient paystackClient;
    @MockitoBean
    private BankResolver bankResolver;
    @MockitoSpyBean
    private InMemoryEmailSender emailSender;

    private Tenant tenant;
    private Worker manager;

    @BeforeEach
    void setUp() {
        clearInvocations(paystackClient);
        emailSender.clear();
        tenant = tenants.main();
        tenant.setBankCode("058");
        tenant.setBankName("Guaranty Trust Bank");
        tenant.setAccountNumber("0123456789");
        tenant.setAccountName("TEST PLACE");
        tenant.setPaystackSubaccountCode(TenantTestSupport.SUBACCOUNT);
        tenant = tenantRepo.save(tenant);
        manager = new Worker();
        manager.setTenant(tenant);
        manager.setUsername("payout.manager." + System.nanoTime());
        manager.setFullName("Payout Manager");
        manager.setRole(WorkerRole.MANAGER);
        manager.setPassword(passwordEncoder.encode(PASSWORD));
        manager = workerRepo.save(manager);
        when(bankResolver.resolve("057", NEW_ACCOUNT)).thenReturn(new BankResolver.Resolved("Zenith Bank", "TEST PLACE LTD"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        reset(emailSender);
    }

    private PayoutAccountRequest request(String password) {
        PayoutAccountRequest request = new PayoutAccountRequest();
        request.setCurrentPassword(password);
        request.setBankCode("057");
        request.setAccountNumber(NEW_ACCOUNT);
        return request;
    }

    private Tenant saved() {
        return tenantRepo.findById(tenant.getTenantId()).orElseThrow();
    }

    private void assertNothingChanged() {
        Tenant now = saved();
        assertEquals("058", now.getBankCode());
        assertEquals("0123456789", now.getAccountNumber());
        assertEquals("Guaranty Trust Bank", now.getBankName());
        assertTrue(emailSender.messages().isEmpty());
    }

    @Test
    void testThatAWrongPasswordChangesNothingAndNeverReachesPaystack() {
        InvalidRequestException refused = assertThrows(InvalidRequestException.class,
                () -> workspace.changePayoutAccount(tenant.getTenantId(), manager.getWorkerId(), request("Wrong-Pass-9")));

        assertEquals("That password isn't right.", refused.getMessage());
        assertNothingChanged();
        verifyNoInteractions(paystackClient);
        verifyNoInteractions(bankResolver);
    }

    @Test
    void testThatWhenPaystackAcceptsTheSubaccountAndTheBusinessAreUpdatedAndTheOwnerIsToldByEmail() {
        MyTenantResponse response = workspace.changePayoutAccount(tenant.getTenantId(), manager.getWorkerId(), request(PASSWORD));

        verify(paystackClient).updateSubaccount(TenantTestSupport.SUBACCOUNT, "057", NEW_ACCOUNT);
        Tenant now = saved();
        assertEquals("057", now.getBankCode());
        assertEquals("Zenith Bank", now.getBankName());
        assertEquals(NEW_ACCOUNT, now.getAccountNumber());
        assertEquals("TEST PLACE LTD", now.getAccountName());
        assertEquals("Zenith Bank", response.getBankName());
        assertEquals("3344", response.getAccountLast4());

        // The email goes out only once the change is saved, so nothing is sent while it is still open.
        assertTrue(emailSender.messages().isEmpty());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        // This one test really commits. The manager made for it is removed again, so no other test sees it.
        TestTransaction.start();
        workerRepo.deleteById(manager.getWorkerId());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        List<OutboxMessage> mail = emailSender.messages();
        assertEquals(1, mail.size());
        assertEquals(tenant.getEmail(), mail.get(0).to());
        String body = mail.get(0).body();
        assertTrue(body.contains("Test Place"));
        assertTrue(body.contains("Zenith Bank"));
        assertTrue(body.contains("3344"));
        assertFalse(body.contains(NEW_ACCOUNT));
        assertTrue(body.contains("If this wasn't you, contact Plately straight away."));
        assertTrue(body.matches("(?s).*When: \\d{1,2} \\w{3} \\d{4}, \\d{1,2}:\\d{2} (am|pm).*"));
    }

    @Test
    void testThatWhenPaystackRefusesNothingIsSavedAndNobodyIsEmailed() {
        doThrow(new PayoutAccountRejectedException(PaystackClientImpl.PAYOUT_UPDATE_REJECTED))
                .when(paystackClient).updateSubaccount(anyString(), anyString(), anyString());

        PayoutAccountRejectedException refused = assertThrows(PayoutAccountRejectedException.class,
                () -> workspace.changePayoutAccount(tenant.getTenantId(), manager.getWorkerId(), request(PASSWORD)));

        assertEquals(PaystackClientImpl.PAYOUT_UPDATE_REJECTED, refused.getMessage());
        assertNothingChanged();
    }

    @Test
    void testThatAnAccountTheBankCannotFindIsNeverSentToPaystack() {
        when(bankResolver.resolve("057", NEW_ACCOUNT)).thenThrow(new AccountVerificationException("The bank couldn't find that account."));

        assertThrows(AccountVerificationException.class,
                () -> workspace.changePayoutAccount(tenant.getTenantId(), manager.getWorkerId(), request(PASSWORD)));

        verify(paystackClient, never()).updateSubaccount(anyString(), anyString(), anyString());
        assertNothingChanged();
    }

    @Test
    void testThatABusinessWithoutASubaccountHasNothingToChange() {
        tenant.setPaystackSubaccountCode(null);
        tenantRepo.save(tenant);

        ConflictException refused = assertThrows(ConflictException.class,
                () -> workspace.changePayoutAccount(tenant.getTenantId(), manager.getWorkerId(), request(PASSWORD)));

        assertEquals(TenantWorkspaceServiceImpl.PAYOUT_NOT_READY, refused.getMessage());
        verify(paystackClient, never()).updateSubaccount(anyString(), anyString(), anyString());
        assertNothingChanged();
    }

    @Test
    void testThatTheChangeStandsEvenIfTheEmailCannotBeSent() {
        doThrow(new EmailSendException("down")).when(emailSender).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));

        workspace.changePayoutAccount(tenant.getTenantId(), manager.getWorkerId(), request(PASSWORD));

        assertEquals(NEW_ACCOUNT, saved().getAccountNumber());
    }

    @Test
    void testThatAManagerOfAnotherBusinessCannotChangeThisOnesAccount() {
        Tenant other = tenants.other();
        Worker theirs = new Worker();
        theirs.setTenant(other);
        theirs.setUsername("payout.other." + System.nanoTime());
        theirs.setRole(WorkerRole.MANAGER);
        theirs.setPassword(passwordEncoder.encode(PASSWORD));
        Worker savedTheirs = workerRepo.save(theirs);

        assertThrows(ResourceNotFoundException.class,
                () -> workspace.changePayoutAccount(tenant.getTenantId(), savedTheirs.getWorkerId(), request(PASSWORD)));

        verifyNoInteractions(paystackClient);
        assertNothingChanged();
    }

    @Test
    void testThatOnlyAManagerCanChangeThePayoutAccount() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"bankCode\":\"057\",\"accountNumber\":\"" + NEW_ACCOUNT + "\"}";

        for (String role : List.of("CHEF", "WAITER", "CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role, "worker-" + role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/payout-account")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(), role);
        }
        assertNothingChanged();
        verifyNoInteractions(paystackClient);

        signIn("MANAGER", manager.getWorkerId());
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/payout-account")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        assertEquals(NEW_ACCOUNT, saved().getAccountNumber());
    }

    @Test
    void testThatABadAccountNumberOrMissingPasswordIsRefusedBeforeAnythingHappens() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        signIn("MANAGER", manager.getWorkerId());

        assertEquals(400, mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/payout-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"bankCode\":\"057\",\"accountNumber\":\"12\"}")).andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/payout-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankCode\":\"057\",\"accountNumber\":\"" + NEW_ACCOUNT + "\"}")).andReturn().getResponse().getStatus());
        verifyNoInteractions(paystackClient);
        assertNothingChanged();
    }

    private void signIn(String role, String workerId) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", tenant.getTenantId())
                : new WorkerPrincipal(workerId, role, role.equals("PLATFORM_ADMIN") ? null : tenant.getTenantId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }
}
