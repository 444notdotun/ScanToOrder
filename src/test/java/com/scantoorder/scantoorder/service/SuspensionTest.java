package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.JwtFilter;
import com.scantoorder.scantoorder.config.SuspendedTenants;
import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.exception.PlaceClosedException;
import com.scantoorder.scantoorder.exception.SuspendedTenantException;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Suspending a business signs its staff out at once, and its guests cannot start or pay for orders. Reinstating undoes both.
@SpringBootTest
@Transactional
class SuspensionTest {
    private static final String SET = "suspended:tenants";

    @Autowired
    private PlatformService platformService;
    @Autowired
    private Auth auth;
    @Autowired
    private JwtFilter jwtFilter;
    @Autowired
    private SuspendedTenants suspendedTenants;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private InMemoryEmailSender outbox;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant tenant;
    private String token;
    private String temporaryPassword;

    @BeforeEach
    void setUp() {
        when(paystackClient.createSubaccount(anyString(), anyString(), anyString(), any())).thenReturn("ACCT_approved001");
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new PaystackInitResponseData("https://checkout.example/pay", invocation.getArgument(2)));
        SecurityContextHolder.clearContext();
        outbox.clear();
        Tenant pending = tenants.pendingTenant("Suspend");
        platformService.approve(pending.getTenantId());
        Matcher password = Pattern.compile("Temporary password: (\\S+)").matcher(outbox.messages().get(0).body());
        assertTrue(password.find());
        tenant = tenantRepo.findById(pending.getTenantId()).orElseThrow();
        temporaryPassword = password.group(1);
        token = auth.login(new LoginRequest(pending.getSlug() + ".manager", temporaryPassword)).getToken();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        redis.opsForSet().remove(SET, tenant.getTenantId());
    }

    private MockHttpServletResponse call(String path) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setCookies(new Cookie("access_token", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        jwtFilter.doFilter(request, response, chain);
        response.setForwardedUrl(chain.getRequest() == null ? null : "passed");
        return response;
    }

    // ---- staff

    @Test
    void testThatASuspendedBusinessesStaffAreRefusedAtOnceWithTheExistingMessage() throws Exception {
        assertNotNull(call(JwtFilter.CHANGE_PASSWORD_PATH).getForwardedUrl(), "before suspending, the token is good");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        platformService.suspend(tenant.getTenantId());
        MockHttpServletResponse refused = call(JwtFilter.CHANGE_PASSWORD_PATH);

        assertEquals(401, refused.getStatus());
        assertEquals("{\"status\":\"ERROR\",\"message\":\"This business is suspended on Plately. Contact us to sort it out.\"}", refused.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(refused.getForwardedUrl(), "the request never reached the app");
    }

    @Test
    void testThatReinstatingLetsTheSameTokenWorkAgain() throws Exception {
        platformService.suspend(tenant.getTenantId());
        assertEquals(401, call(JwtFilter.CHANGE_PASSWORD_PATH).getStatus());

        platformService.reinstate(tenant.getTenantId());
        MockHttpServletResponse allowed = call(JwtFilter.CHANGE_PASSWORD_PATH);

        assertNotEquals(401, allowed.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testThatSigningOutStillWorksForASuspendedBusinessesStaff() throws Exception {
        platformService.suspend(tenant.getTenantId());

        MockHttpServletResponse out = call(JwtFilter.LOGOUT_PATH);

        assertNotEquals(401, out.getStatus());
        assertEquals("passed", out.getForwardedUrl(), "the request went on to the sign-out endpoint");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "but nobody is signed in by it");
    }

    @Test
    void testThatSigningInIsStillRefusedForASuspendedBusinessAndAWrongPasswordIsStillJustWrong() {
        platformService.suspend(tenant.getTenantId());

        assertThrows(SuspendedTenantException.class, () -> auth.login(new LoginRequest(tenant.getSlug() + ".manager", temporaryPassword)));
        assertThrows(com.scantoorder.scantoorder.exception.BadSignInException.class,
                () -> auth.login(new LoginRequest(tenant.getSlug() + ".manager", "wrong-password")), "the password is checked first");
    }

    // ---- the set in Redis

    @Test
    void testThatSuspendingAndReinstatingChangeTheSetAndAStartFillsItFromTheDatabase() {
        assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(SET, tenant.getTenantId())));

        platformService.suspend(tenant.getTenantId());
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(SET, tenant.getTenantId())));

        platformService.reinstate(tenant.getTenantId());
        assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(SET, tenant.getTenantId())));

        // Suspended in the database behind the set's back, as it would be after a restart with an empty Redis.
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepo.save(tenant);
        tenantRepo.flush();
        suspendedTenants.refill();
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(SET, tenant.getTenantId())));
    }

    @Test
    void testThatWithRedisDownTheBusinessesOwnStatusDecides() {
        StringRedisTemplate broken = Mockito.mock(StringRedisTemplate.class);
        when(broken.opsForSet()).thenThrow(new RuntimeException("down"));
        Object real = ReflectionTestUtils.getField(suspendedTenants, "redis");
        ReflectionTestUtils.setField(suspendedTenants, "redis", broken);
        try {
            assertFalse(suspendedTenants.blocks(tenant), "active");
            tenant.setStatus(TenantStatus.SUSPENDED);
            assertTrue(suspendedTenants.blocks(tenant), "suspended");
            assertFalse(suspendedTenants.blocks(null));
        } finally {
            ReflectionTestUtils.setField(suspendedTenants, "redis", real);
        }
    }

    // ---- guests

    private CreateOrderRequest oneDish(Item dish) {
        CreateOrderRequest request = new CreateOrderRequest();
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName(dish.getItemName());
        line.setQuantity(1);
        request.getOrderItems().add(line);
        return request;
    }

    @Test
    void testThatASeatedGuestCannotStartOrPayForAnOrderWhileTheBusinessIsSuspended() {
        tenant.setOpenForOrders(true);
        tenant = tenants.save(tenant);
        RestaurantTable table = support.newTable(tenant.getTenantId(), 2);
        Item dish = pay.item(tenant, "Paused dish", 1500, true);
        CustomerPrincipal guest = support.claim(support.seatsOf(table).get(0), "Ada");
        String orderId = orderService.createOrder(oneDish(dish), guest).getOrderId();
        InitializePaymentRequest request = new InitializePaymentRequest();
        request.setOrderId(orderId);

        platformService.suspend(tenant.getTenantId());

        PlaceClosedException noOrder = assertThrows(PlaceClosedException.class, () -> orderService.createOrder(oneDish(dish), guest));
        assertEquals(TenantAccess.PLACE_PAUSED, noOrder.getMessage());
        assertEquals(tenant.getName(), noOrder.getBusinessName());
        assertThrows(PlaceClosedException.class, () -> paymentService.initializePayment(tenant.getTenantId(), null, request));

        platformService.reinstate(tenant.getTenantId());

        assertNotNull(orderService.createOrder(oneDish(dish), guest).getOrderId());
        assertNotNull(paymentService.initializePayment(tenant.getTenantId(), null, request));
    }

    @Test
    void testThatAClosedBusinessStillLetsSeatedGuestsOrderButASuspendedOneDoesNot() {
        tenant.setOpenForOrders(true);
        tenant = tenants.save(tenant);
        RestaurantTable table = support.newTable(tenant.getTenantId(), 2);
        Item dish = pay.item(tenant, "Closed dish", 1500, true);
        CustomerPrincipal guest = support.claim(support.seatsOf(table).get(0), "Ada");

        tenant.setOpenForOrders(false);
        tenant = tenants.save(tenant);

        assertNotNull(orderService.createOrder(oneDish(dish), guest).getOrderId(), "closed: seated guests finish");
        platformService.suspend(tenant.getTenantId());
        assertThrows(PlaceClosedException.class, () -> orderService.createOrder(oneDish(dish), guest));
    }
}
