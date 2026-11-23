package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.JwtFilter;
import com.scantoorder.scantoorder.data.model.PasswordResetToken;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.data.repository.PasswordResetTokenRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.StaffResponse;
import com.scantoorder.scantoorder.exception.BadSignInException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import com.scantoorder.scantoorder.service.email.OutboxMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Forgot password for the person who runs a business, the manager's reset for their staff, and signing out everywhere.
@SpringBootTest
class PasswordResetTest {

    private static final Pattern TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");
    private static final Pattern TEMPORARY = Pattern.compile("Temporary password: (\\S+)");

    @Autowired
    private PasswordResetService reset;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordResetTokenRepo tokenRepo;
    @Autowired
    private InMemoryEmailSender outbox;
    @Autowired
    private PlatformService platformService;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private Auth auth;
    @Autowired
    private JwtFilter jwtFilter;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private WebApplicationContext wac;
    @MockitoBean
    private PaystackClient paystackClient;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(paystackClient.createSubaccount(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn("ACCT_approved001");
        ReflectionTestUtils.setField(reset, "floorMillis", 0L);
        ReflectionTestUtils.setField(reset, "sender", (Executor) Runnable::run);
        Set<String> keys = redis.keys("limit:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        SecurityContextHolder.clearContext();
        outbox.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        outbox.clear();
    }

    // An approved business, its manager's temporary password in hand.
    private record Business(Tenant tenant, String username, String temporaryPassword) {
    }

    private Business business() {
        Tenant tenant = tenants.pendingTenant("Reset");
        platformService.approve(tenant.getTenantId());
        Matcher matcher = TEMPORARY.matcher(outbox.messages().get(0).body());
        assertTrue(matcher.find());
        outbox.clear();
        return new Business(tenantRepo.findById(tenant.getTenantId()).orElseThrow(), tenant.getSlug() + ".manager", matcher.group(1));
    }

    private String tokenFrom(OutboxMessage message) {
        Matcher matcher = TOKEN.matcher(message.body());
        assertTrue(matcher.find(), "the email holds a reset link");
        return matcher.group(1);
    }

    private String asked(String email) {
        return reset.ask(email, "10.0.0." + (email.hashCode() & 0xff));
    }

    private boolean runsAs(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenant");
        request.setRequestURI("/api/v1/tenant");
        request.setCookies(new Cookie("access_token", token));
        jwtFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    // ---- forgot password

    @Test
    void testThatTheAnswerIsTheSameWhoeverAsksAndOnlyAnActiveBusinessGetsALink() {
        Business active = business();
        Tenant pending = tenants.pendingTenant("Still Pending");
        Tenant declined = tenants.pendingTenant("Turned Down");
        declined.setStatus(TenantStatus.DECLINED);
        tenantRepo.save(declined);
        Business suspended = business();
        platformService.suspend(suspended.tenant().getTenantId());
        outbox.clear();

        assertEquals(PasswordResetService.ASKED, asked(active.tenant().getEmail()));
        assertEquals(PasswordResetService.ASKED, asked("nobody-" + System.nanoTime() + "@example.com"));
        assertEquals(PasswordResetService.ASKED, asked(pending.getEmail()));
        assertEquals(PasswordResetService.ASKED, asked(declined.getEmail()));
        assertEquals(PasswordResetService.ASKED, asked(suspended.tenant().getEmail()));

        assertEquals(1, outbox.messages().size());
        assertEquals(active.tenant().getEmail(), outbox.messages().get(0).to());
        assertEquals("Reset your Plately password", outbox.messages().get(0).subject());
    }

    @Test
    void testThatTheEmailIsFoundWhateverItsCaseAndSpaces() {
        Business active = business();

        asked("  " + active.tenant().getEmail().toUpperCase() + " ");

        assertEquals(1, outbox.messages().size());
    }

    @Test
    void testThatAnEmptyOrMissingEmailGetsTheSameAnswerAndNothingIsSent() {
        assertEquals(PasswordResetService.ASKED, reset.ask("   ", "10.9.9.9"));
        assertEquals(PasswordResetService.ASKED, reset.ask(null, "10.9.9.9"));
        assertTrue(outbox.messages().isEmpty());
    }

    @Test
    void testThatTheEmailSaysWhatTheBriefSaysAndLinksToTheResetPage() {
        Business active = business();

        asked(active.tenant().getEmail());

        String text = outbox.messages().get(0).body();
        assertTrue(text.contains("Reset your password"));
        assertTrue(text.contains("Someone asked to reset the password for " + active.tenant().getName() + "."));
        assertTrue(text.contains("If it was you, choose a new one here. The link works for 15 minutes, once."));
        assertTrue(text.contains("Choose a new password"));
        assertTrue(text.contains("If you didn't ask, ignore this email. Your password stays the same."));
        assertTrue(text.contains("/reset-password?token="));
        assertEquals("password-reset", outbox.messages().get(0).tag());
    }

    @Test
    void testThatEveryAnswerTakesAtLeastTheFloorWhetherOrNotAnEmailWasSent() {
        Business active = business();
        ReflectionTestUtils.setField(reset, "floorMillis", 300L);

        long known = timed(() -> asked(active.tenant().getEmail()));
        long unknown = timed(() -> asked("ghost-" + System.nanoTime() + "@example.com"));

        assertTrue(known >= 290, "known took " + known);
        assertTrue(unknown >= 290, "unknown took " + unknown);
    }

    private static long timed(Runnable work) {
        long started = System.nanoTime();
        work.run();
        return (System.nanoTime() - started) / 1_000_000;
    }

    @Test
    void testThatOnlyTheHashOfTheTokenIsStoredNeverTheToken() {
        Business active = business();
        asked(active.tenant().getEmail());
        String token = tokenFrom(outbox.messages().get(0));

        assertEquals(32, java.util.Base64.getUrlDecoder().decode(token).length);
        assertTrue(tokenRepo.findByTokenHash(token).isEmpty());
        PasswordResetToken row = tokenRepo.findByTokenHash(PasswordResetService.hash(token)).orElseThrow();
        assertEquals(64, row.getTokenHash().length());
        assertNotEquals(token, row.getTokenHash());
        assertNull(row.getUsedAt());
    }

    @Test
    void testThatALinkWorksFor15MinutesNotLonger() {
        Business active = business();
        asked(active.tenant().getEmail());
        String token = tokenFrom(outbox.messages().get(0));
        PasswordResetToken row = tokenRepo.findByTokenHash(PasswordResetService.hash(token)).orElseThrow();

        long minutes = Duration.between(row.getCreatedAt(), row.getExpiresAt()).toMinutes();
        assertEquals(15, minutes);

        row.setExpiresAt(Instant.now().minusSeconds(1));
        tokenRepo.save(row);
        InvalidRequestException late = assertThrows(InvalidRequestException.class, () -> reset.reset(token, "A brand new one"));
        assertEquals(PasswordResetService.DEAD_LINK, late.getMessage());
    }

    @Test
    void testThatALinkWorksOnceAndTheSameAnswerComesForAnyDeadLink() {
        Business active = business();
        asked(active.tenant().getEmail());
        String token = tokenFrom(outbox.messages().get(0));

        assertEquals(PasswordResetService.CHANGED, reset.reset(token, "A brand new one"));

        assertEquals(PasswordResetService.DEAD_LINK, assertThrows(InvalidRequestException.class, () -> reset.reset(token, "Another new one")).getMessage());
        assertEquals(PasswordResetService.DEAD_LINK, assertThrows(InvalidRequestException.class, () -> reset.reset("not-a-real-token", "Another new one")).getMessage());
        assertEquals(PasswordResetService.DEAD_LINK, assertThrows(InvalidRequestException.class, () -> reset.reset("  ", "Another new one")).getMessage());
        assertEquals(PasswordResetService.DEAD_LINK, assertThrows(InvalidRequestException.class, () -> reset.reset(null, "Another new one")).getMessage());
    }

    @Test
    void testThatANewRequestEndsTheOlderLinks() {
        Business active = business();
        asked(active.tenant().getEmail());
        String older = tokenFrom(outbox.messages().get(0));
        asked(active.tenant().getEmail());
        String newer = tokenFrom(outbox.messages().get(0));
        assertNotEquals(older, newer);

        assertThrows(InvalidRequestException.class, () -> reset.reset(older, "A brand new one"));
        assertEquals(PasswordResetService.CHANGED, reset.reset(newer, "A brand new one"));
    }

    @Test
    void testThatAtMostThreeRequestsPerEmailAnHourSendAnEmailAndNoneOfThemSaysSo() {
        Business active = business();

        for (int i = 0; i < 5; i++) {
            assertEquals(PasswordResetService.ASKED, reset.ask(active.tenant().getEmail(), "10.1.1." + i));
        }

        assertEquals(3, outbox.messages().size());
    }

    @Test
    void testThatAtMostTenRequestsPerAddressAnHourSendAnEmail() {
        List<Business> many = java.util.stream.IntStream.range(0, 12).mapToObj(i -> business()).toList();
        outbox.clear();

        for (Business each : many) {
            assertEquals(PasswordResetService.ASKED, reset.ask(each.tenant().getEmail(), "10.2.2.2"));
        }

        assertEquals(10, outbox.messages().size());
    }

    // ---- choosing the new password

    @Test
    void testThatTheNewPasswordWorksAndTheOldOnesDoNotAndNobodyIsSignedInByIt() {
        Business active = business();
        asked(active.tenant().getEmail());
        String token = tokenFrom(outbox.messages().get(0));

        assertEquals("Your password is changed. Sign in with it now.", reset.reset(token, "A brand new one"));

        Worker manager = workerRepo.findByUsername(active.username()).orElseThrow();
        assertFalse(manager.isMustChangePassword());
        assertNotNull(manager.getPasswordChangedAt());
        assertThrows(BadSignInException.class, () -> auth.login(new LoginRequest(active.username(), active.temporaryPassword())));
        assertNotNull(auth.login(new LoginRequest(active.username(), "A brand new one")).getToken());
    }

    @Test
    void testThatAShortPasswordIsRefusedAndTheLinkStaysGood() {
        Business active = business();
        asked(active.tenant().getEmail());
        String token = tokenFrom(outbox.messages().get(0));

        assertEquals("Your new password needs at least 8 characters.", assertThrows(InvalidRequestException.class, () -> reset.reset(token, "short")).getMessage());

        assertEquals(PasswordResetService.CHANGED, reset.reset(token, "long enough now"));
    }

    @Test
    void testThatResettingSignsTheOldTokensOutEverywhere() throws Exception {
        Business active = business();
        String old = auth.login(new LoginRequest(active.username(), active.temporaryPassword())).getToken();
        assertTrue(runsAsAllowedToChangePassword(old));
        asked(active.tenant().getEmail());
        String token = tokenFrom(outbox.messages().get(0));
        Thread.sleep(1100);

        reset.reset(token, "A brand new one");

        assertFalse(runsAs(old), "a token from before the reset is no longer any good");
        assertTrue(runsAs(auth.login(new LoginRequest(active.username(), "A brand new one")).getToken()));
    }

    // A temporary-password token may only change the password, so it is checked on that path.
    private boolean runsAsAllowedToChangePassword(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", JwtFilter.CHANGE_PASSWORD_PATH);
        request.setRequestURI(JwtFilter.CHANGE_PASSWORD_PATH);
        request.setCookies(new Cookie("access_token", token));
        jwtFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void testThatChangingYourOwnPasswordKeepsYouSignedInButEndsEveryOlderSignIn() throws Exception {
        Business active = business();
        var first = auth.login(new LoginRequest(active.username(), active.temporaryPassword()));
        Thread.sleep(1100);
        Worker manager = workerRepo.findByUsername(active.username()).orElseThrow();

        var changed = auth.changePassword(new WorkerPrincipal(manager.getWorkerId(), manager.getRole().name(), active.tenant().getTenantId()), "My very own one");

        assertTrue(runsAs(changed.getToken()), "the caller stays signed in with the token that came back");
        assertFalse(runsAsAllowedToChangePassword(first.getToken()));
    }

    @Test
    void testThatTheEndpointsAreOpenToAnyoneAndAnswerInPlainWords() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        Business active = business();

        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"" + active.tenant().getEmail() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(PasswordResetService.ASKED));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"nope\",\"newPassword\":\"A brand new one\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.DEAD_LINK));
    }

    // ---- a manager resets staff

    @Test
    void testThatAManagerResetsTheirOwnChefAndWaiterAndSeesThePasswordOnce() throws Exception {
        Business active = business();
        String tenantId = active.tenant().getTenantId();
        StaffResponse chef = workerService.createWorker(tenantId, "Chef One", "chef", "chef1");
        var first = auth.login(new LoginRequest(chef.getUsername(), chef.getTemporaryPassword()));
        Thread.sleep(1100);

        StaffResponse again = workerService.resetPassword(tenantId, chef.getWorkerId());

        assertNotNull(again.getTemporaryPassword());
        assertNotEquals(chef.getTemporaryPassword(), again.getTemporaryPassword());
        Worker saved = workerRepo.findById(chef.getWorkerId()).orElseThrow();
        assertTrue(saved.isMustChangePassword());
        assertTrue(passwordEncoder.matches(again.getTemporaryPassword(), saved.getPassword()));
        assertFalse(passwordEncoder.matches(chef.getTemporaryPassword(), saved.getPassword()));
        assertFalse(runsAsAllowedToChangePassword(first.getToken()), "their old sign-in stops working");
        assertTrue(workerService.getAllWorkers(tenantId).stream().allMatch(person -> person.getTemporaryPassword() == null), "the list never shows a password");
        assertTrue(outbox.messages().isEmpty(), "staff have no email, so nothing is sent");

        StaffResponse waiter = workerService.createWorker(tenantId, "Waiter One", "waiter", "wait1");
        assertNotNull(workerService.resetPassword(tenantId, waiter.getWorkerId()).getTemporaryPassword());
    }

    @Test
    void testThatAManagerCannotResetSomeoneElsesWorkerAManagerOrARemovedPerson() {
        Business mine = business();
        Business theirs = business();
        String myId = mine.tenant().getTenantId();
        StaffResponse theirChef = workerService.createWorker(theirs.tenant().getTenantId(), "Their Chef", "chef", "tchef");
        Worker myManager = workerRepo.findByUsername(mine.username()).orElseThrow();
        StaffResponse removed = workerService.createWorker(myId, "Gone Chef", "chef", "gone1");
        workerService.deleteWorker(myId, removed.getWorkerId());

        for (String id : List.of(theirChef.getWorkerId(), myManager.getWorkerId(), removed.getWorkerId(), "no-such-worker")) {
            assertEquals("We cannot find that person", assertThrows(ResourceNotFoundException.class, () -> workerService.resetPassword(myId, id)).getMessage());
        }
        Worker untouched = workerRepo.findById(theirChef.getWorkerId()).orElseThrow();
        assertTrue(passwordEncoder.matches(theirChef.getTemporaryPassword(), untouched.getPassword()));
    }
}
