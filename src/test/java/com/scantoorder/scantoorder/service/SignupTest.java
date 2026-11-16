package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.SignupRequest;
import com.scantoorder.scantoorder.dtos.request.VerifyAccountRequest;
import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.SignupResponse;
import com.scantoorder.scantoorder.exception.AccountVerificationException;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class SignupTest {
    private static final String ACCOUNT_NAME = "THE NEW PLACE LTD";

    @Autowired
    private SignupService signupService;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @MockitoBean
    private PaystackClient paystackClient;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("signup:banks");
        when(paystackClient.listBanks()).thenReturn(List.of(new BankData("Guaranty Trust Bank", "058"), new BankData("Access Bank", "044")));
        when(paystackClient.resolveAccountName("058", "0123456789")).thenReturn(ACCOUNT_NAME);
    }

    private String email() {
        return UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    private SignupRequest restaurant(String name, String email) {
        SignupRequest request = new SignupRequest();
        request.setType(TenantType.RESTAURANT);
        request.setBusinessName(name);
        request.setOwnerName("Ada Owner");
        request.setEmail(email);
        request.setPhone("08012345678");
        request.setBankCode("058");
        request.setAccountNumber("0123456789");
        request.setAccountName(ACCOUNT_NAME);
        return request;
    }

    @Test
    void testThatARestaurantCanSendTheFormAndStaysPendingWithNoAccount() {
        String name = tenants.uniqueName("The Corner Table");
        SignupResponse response = signupService.signup(restaurant(name, email()));

        String expectedSlug = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        assertEquals(expectedSlug, response.getSlug());
        assertEquals("PENDING_APPROVAL", response.getStatus());
        Tenant tenant = tenantRepo.findBySlug(expectedSlug).orElseThrow();
        assertEquals(TenantStatus.PENDING_APPROVAL, tenant.getStatus());
        assertFalse(tenant.isOpenForOrders());
        assertTrue(tenant.isHoldsEnabled());
        assertEquals("Guaranty Trust Bank", tenant.getBankName());
        assertEquals(ACCOUNT_NAME, tenant.getAccountName());
        assertFalse(workerRepo.existsByUsername(expectedSlug + ".manager"));
    }

    @Test
    void testThatTheAccountIsCheckedAgainOnSubmit() {
        signupService.signup(restaurant(tenants.uniqueName("Checked"), email()));
        verify(paystackClient, times(1)).resolveAccountName("058", "0123456789");
    }

    // Two different names can still make the same web address; the next free number is added.
    @Test
    void testThatATakenSlugGetsTheNextFreeNumber() {
        String stem = "Slug" + System.nanoTime();
        String base = stem.toLowerCase() + "4";
        assertEquals(base, signupService.signup(restaurant(stem + "-4", email())).getSlug());
        assertEquals(base + "2", signupService.signup(restaurant(stem + ".4", email())).getSlug());
        assertEquals(base + "3", signupService.signup(restaurant(stem + "_4", email())).getSlug());
    }

    @Test
    void testThatAnEventPlannerSendsNoBankDetailsAndHasHoldsOff() {
        SignupRequest request = restaurant(tenants.uniqueName("Events"), email());
        request.setType(TenantType.EVENT_PLANNER);

        Tenant tenant = tenantRepo.findBySlug(signupService.signup(request).getSlug()).orElseThrow();

        assertEquals(TenantType.EVENT_PLANNER, tenant.getType());
        assertNull(tenant.getBankCode());
        assertNull(tenant.getAccountNumber());
        assertNull(tenant.getAccountName());
        assertFalse(tenant.isHoldsEnabled());
        verify(paystackClient, never()).resolveAccountName(anyString(), anyString());
    }

    @Test
    void testThatARestaurantWithoutAPayoutAccountIsTurnedBack() {
        SignupRequest request = restaurant(tenants.uniqueName("No Bank"), email());
        request.setBankCode(null);
        assertThrows(InvalidRequestException.class, () -> signupService.signup(request));
    }

    @Test
    void testThatAnAccountNameThatDoesNotMatchTheBanksRecordIsTurnedBack() {
        SignupRequest request = restaurant(tenants.uniqueName("Wrong Name"), email());
        request.setAccountName("SOMEONE ELSE");
        assertThrows(InvalidRequestException.class, () -> signupService.signup(request));
        assertTrue(tenantRepo.findByEmailIgnoreCase(request.getEmail()).isEmpty());
    }

    @Test
    void testThatAnUnknownBankIsTurnedBack() {
        SignupRequest request = restaurant(tenants.uniqueName("Odd Bank"), email());
        request.setBankCode("999");
        assertThrows(InvalidRequestException.class, () -> signupService.signup(request));
    }

    @Test
    void testThatAnAccountTheBankCannotFindGivesTheKindMessage() {
        when(paystackClient.resolveAccountName("058", "9999999999"))
                .thenThrow(new AccountVerificationException(PaystackClientImpl.ACCOUNT_NOT_FOUND));
        VerifyAccountRequest request = new VerifyAccountRequest();
        request.setBankCode("058");
        request.setAccountNumber("9999999999");
        AccountVerificationException exception = assertThrows(AccountVerificationException.class, () -> signupService.verifyAccount(request));
        assertEquals("The bank couldn't find that account. Check the number and the bank, then try again.", exception.getMessage());
    }

    @Test
    void testThatVerifyAccountReturnsTheAccountName() {
        VerifyAccountRequest request = new VerifyAccountRequest();
        request.setBankCode("058");
        request.setAccountNumber("0123456789");
        assertEquals(ACCOUNT_NAME, signupService.verifyAccount(request).getAccountName());
    }

    @Test
    void testThatTheBankListIsCachedAfterTheFirstCall() {
        assertEquals(2, signupService.banks().size());
        assertEquals(2, signupService.banks().size());
        verify(paystackClient, times(1)).listBanks();
    }

    @Test
    void testThatAnEmailAlreadyOnPlatelyIsRefusedInAnyCase() {
        String email = email();
        signupService.signup(restaurant(tenants.uniqueName("First"), email));
        ConflictException exception = assertThrows(ConflictException.class,
                () -> signupService.signup(restaurant(tenants.uniqueName("Second"), email.toUpperCase())));
        assertEquals(SignupServiceImpl.EMAIL_TAKEN, exception.getMessage());
    }

    @Test
    void testThatABusinessNameAlreadyOnPlatelyIsRefusedIgnoringCaseAndSpaces() {
        String unique = "Triple" + System.nanoTime();
        signupService.signup(restaurant(unique + "4", email()));

        ConflictException spaced = assertThrows(ConflictException.class, () -> signupService.signup(restaurant(unique.toLowerCase() + " 4", email())));
        ConflictException shouted = assertThrows(ConflictException.class, () -> signupService.signup(restaurant(" " + unique.toUpperCase() + "4 ", email())));

        assertEquals(SignupServiceImpl.NAME_TAKEN, spaced.getMessage());
        assertEquals(SignupServiceImpl.NAME_TAKEN, shouted.getMessage());
        assertTrue(SignupServiceImpl.NAME_TAKEN.contains("Triple4 Ikeja"));
    }

    @Test
    void testThatTheNameCheckAnswersFromTheSameRecordsTheSignUpGuards() {
        String unique = "Check" + System.nanoTime();
        assertTrue(signupService.nameAvailable(unique + " Grill"));

        signupService.signup(restaurant(unique + " Grill", email()));

        assertFalse(signupService.nameAvailable(unique + " Grill"));
        assertFalse(signupService.nameAvailable(unique.toLowerCase() + "grill"));
        assertFalse(signupService.nameAvailable("   "));
        assertTrue(signupService.nameAvailable(unique + " Grill Ikeja"));
    }

    @Test
    void testThatTheDatabaseItselfRefusesASecondBusinessWithTheSameNormalisedName() {
        String unique = "Indexed" + System.nanoTime();
        Tenant first = tenants.pendingTenant(unique);
        Tenant second = new Tenant();
        second.setName(first.getName().toUpperCase().replace("-", " - "));
        second.setSlug("other" + System.nanoTime());
        second.setType(first.getType());
        second.setEmail(email());

        assertEquals(com.scantoorder.scantoorder.utils.BusinessNames.normalise(first.getName()), tenantRepo.findBySlug(first.getSlug()).orElseThrow().getNormalizedName());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> tenantRepo.saveAndFlush(second));
    }

    @Test
    void testThatADeclinedOwnerWhoSendsTheFormAgainReplacesTheOldApplication() {
        String email = email();
        String name = tenants.uniqueName("Second Chance");
        String slug = signupService.signup(restaurant(name, email)).getSlug();
        Tenant declined = tenantRepo.findBySlug(slug).orElseThrow();
        declined.setStatus(TenantStatus.DECLINED);
        declined.setDeclineReason("Blurry details");
        tenantRepo.save(declined);
        long before = tenantRepo.count();

        SignupRequest again = restaurant(name + " Revised", email);
        again.setOwnerName("Ada Fixed");
        SignupResponse response = signupService.signup(again);

        assertEquals(slug, response.getSlug());
        assertEquals(before, tenantRepo.count());
        Tenant replaced = tenantRepo.findBySlug(slug).orElseThrow();
        assertEquals(TenantStatus.PENDING_APPROVAL, replaced.getStatus());
        assertNull(replaced.getDeclineReason());
        assertEquals("Ada Fixed", replaced.getOwnerName());
        assertEquals(name + " Revised", replaced.getName());
    }

    @Test
    void testThatAFormWithNothingToMakeASlugFromIsTurnedBack() {
        assertThrows(InvalidRequestException.class, () -> signupService.signup(restaurant("!!!", email())));
        Mockito.verifyNoMoreInteractions(Mockito.ignoreStubs(paystackClient));
    }
}
