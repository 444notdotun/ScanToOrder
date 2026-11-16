package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.JwtFilter;
import com.scantoorder.scantoorder.controller.AuthController;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import com.scantoorder.scantoorder.dtos.respond.StaffResponse;
import com.scantoorder.scantoorder.exception.BadSignInException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.SuspendedTenantException;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthFlowTest {
    private static final String BAD = "That username and password don't match";

    @Autowired
    private Auth auth;
    @Autowired
    private AuthController authController;
    @Autowired
    private PlatformService platformService;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JwtFilter jwtFilter;
    @Autowired
    private TenantTestSupport tenants;
    @MockitoBean
    private PaystackClient paystackClient;
    @Autowired
    private InMemoryEmailSender emailSender;

    private Tenant tenant;
    private String managerUsername;
    private String temporaryPassword;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(paystackClient.createSubaccount(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn("ACCT_approved001");
        SecurityContextHolder.clearContext();
        emailSender.clear();
        tenant = tenants.pendingTenant("Sign In");
        platformService.approve(tenant.getTenantId());
        managerUsername = tenant.getSlug() + ".manager";
        Matcher matcher = Pattern.compile("Temporary password: (\\S+)").matcher(emailSender.messages().get(0).body());
        assertTrue(matcher.find());
        temporaryPassword = matcher.group(1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthResponse signIn(String username, String password) {
        return auth.login(new LoginRequest(username, password));
    }

    private Authentication runFilter(String token, String path, MockHttpServletResponse response) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.setCookies(new Cookie("access_token", token));
        jwtFilter.doFilter(request, response, new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void testThatAManagerSignsInWithTheTemporaryPasswordAndMustChooseANewOne() {
        AuthResponse response = signIn(managerUsername, temporaryPassword);
        assertTrue(response.getMustChangePassword());
        assertNotNull(response.getToken());
        Claims claims = jwtService.extractClaims(response.getToken());
        assertEquals(tenant.getTenantId(), claims.get("tenantId", String.class));
        assertEquals(true, claims.get("mustChangePassword", Boolean.class));
    }

    @Test
    void testThatSigningInNamesTheBusinessAndThePerson() {
        AuthResponse response = signIn(managerUsername, temporaryPassword);
        assertEquals(tenant.getName(), response.getBusinessName());
        assertNotNull(response.getFullName());
    }

    @Test
    void testThatSigningOutEndsTheCookieAndEvenATemporaryPasswordTokenMayDoIt() throws Exception {
        String token = signIn(managerUsername, temporaryPassword).getToken();

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        assertNotNull(runFilter(token, "/api/v1/auth/logout", allowed));
        assertEquals(200, allowed.getStatus());

        String cookie = authController.logout().getHeaders().getFirst("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("access_token=;"));
        assertTrue(cookie.contains("Max-Age=0"));
    }

    @Test
    void testThatATemporaryPasswordTokenCanOnlyChangeThePassword() throws Exception {
        String token = signIn(managerUsername, temporaryPassword).getToken();

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertNull(runFilter(token, "/api/v1/tenant", blocked));
        assertEquals(403, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("Choose a new password before you carry on."));

        MockHttpServletResponse alsoBlocked = new MockHttpServletResponse();
        assertNull(runFilter(token, "/api/v1/tables", alsoBlocked));
        assertEquals(403, alsoBlocked.getStatus());

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        Authentication authentication = runFilter(token, "/api/v1/auth/change-password", allowed);
        assertNotNull(authentication);
        assertEquals(200, allowed.getStatus());
        assertEquals(tenant.getTenantId(), ((WorkerPrincipal) authentication.getPrincipal()).getTenantId());
    }

    @Test
    void testThatTheNewPasswordMustBeLongEnoughAndDifferentFromTheTemporaryOne() {
        AuthResponse response = signIn(managerUsername, temporaryPassword);
        WorkerPrincipal caller = new WorkerPrincipal(response.getWorkerId(), "MANAGER", tenant.getTenantId());

        assertThrows(InvalidRequestException.class, () -> auth.changePassword(caller, "short"));
        assertThrows(InvalidRequestException.class, () -> auth.changePassword(caller, temporaryPassword));
        assertTrue(workerRepo.findByUsername(managerUsername).orElseThrow().isMustChangePassword());
    }

    @Test
    void testThatChangingThePasswordClearsTheFlagAndGivesANormalToken() throws Exception {
        AuthResponse first = signIn(managerUsername, temporaryPassword);
        WorkerPrincipal caller = new WorkerPrincipal(first.getWorkerId(), "MANAGER", tenant.getTenantId());

        AuthResponse changed = auth.changePassword(caller, "my-own-password");

        assertFalse(changed.getMustChangePassword());
        assertFalse(workerRepo.findByUsername(managerUsername).orElseThrow().isMustChangePassword());
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertNotNull(runFilter(changed.getToken(), "/api/v1/tenant", response));
        assertEquals(200, response.getStatus());
        assertFalse(signIn(managerUsername, "my-own-password").getMustChangePassword());
        assertThrows(BadSignInException.class, () -> signIn(managerUsername, temporaryPassword));
        assertThrows(InvalidRequestException.class, () -> auth.changePassword(caller, "another-password"));
    }

    @Test
    void testThatAFailedSignInNeverSaysWhichPartWasWrong() {
        BadSignInException wrongPassword = assertThrows(BadSignInException.class, () -> signIn(managerUsername, "wrong-password"));
        BadSignInException unknownUser = assertThrows(BadSignInException.class, () -> signIn("nobody.nowhere", temporaryPassword));
        BadSignInException blank = assertThrows(BadSignInException.class, () -> signIn(managerUsername, "x"));
        assertEquals(BAD, wrongPassword.getMessage());
        assertEquals(BAD, unknownUser.getMessage());
        assertEquals(BAD, blank.getMessage());
    }

    @Test
    void testThatASuspendedBusinessCannotSignInUntilItIsReinstated() {
        platformService.suspend(tenant.getTenantId());
        SuspendedTenantException exception = assertThrows(SuspendedTenantException.class, () -> signIn(managerUsername, temporaryPassword));
        assertEquals("This business is suspended on Plately. Contact us to sort it out.", exception.getMessage());
        assertThrows(BadSignInException.class, () -> signIn(managerUsername, "wrong-password"));

        platformService.reinstate(tenant.getTenantId());
        assertNotNull(signIn(managerUsername, temporaryPassword).getToken());
    }

    @Test
    void testThatAnApplicationThatWasNeverApprovedHasNoSignIn() {
        Tenant waiting = tenants.pendingTenant("Waiting");
        assertThrows(BadSignInException.class, () -> signIn(waiting.getSlug() + ".manager", "anything-at-all"));
    }

    @Test
    void testThatARemovedWorkerCannotSignInAndTheirTokenStopsAtOnce() throws Exception {
        StaffResponse staff = workerService.createWorker(tenant.getTenantId(), "Soon Gone", "waiter", "gone");
        String token = signIn(staff.getUsername(), staff.getTemporaryPassword()).getToken();
        Worker worker = workerRepo.findById(staff.getWorkerId()).orElseThrow();
        WorkerPrincipal caller = new WorkerPrincipal(worker.getWorkerId(), "WAITER", tenant.getTenantId());
        String normalToken = auth.changePassword(caller, "a-fine-password").getToken();
        assertNotNull(runFilter(normalToken, "/api/v1/tables", new MockHttpServletResponse()));

        workerService.deleteWorker(tenant.getTenantId(), staff.getWorkerId());

        assertNull(runFilter(normalToken, "/api/v1/tables", new MockHttpServletResponse()));
        assertNull(runFilter(token, "/api/v1/auth/change-password", new MockHttpServletResponse()));
        BadSignInException exception = assertThrows(BadSignInException.class, () -> signIn(staff.getUsername(), "a-fine-password"));
        assertEquals(BAD, exception.getMessage());
    }

    @Test
    void testThatAStaffMemberAlsoStartsWithATemporaryPassword() throws Exception {
        StaffResponse staff = workerService.createWorker(tenant.getTenantId(), "New Chef", "chef", "newchef");
        AuthResponse response = signIn(staff.getUsername(), staff.getTemporaryPassword());
        assertTrue(response.getMustChangePassword());
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertNull(runFilter(response.getToken(), "/api/v1/kitchen/orders", blocked));
        assertEquals(403, blocked.getStatus());
    }

    @Test
    void testThatThePlatformAdminSignsInWithNoTenantInTheToken() {
        AuthResponse response = signIn("platformadmin", "platform-password11");
        assertEquals("PLATFORM_ADMIN", response.getRole().name());
        assertFalse(response.getMustChangePassword());
        assertNull(jwtService.extractClaims(response.getToken()).get("tenantId"));
    }
}
