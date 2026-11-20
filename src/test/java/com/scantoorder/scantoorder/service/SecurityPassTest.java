package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.PlaceClosedException;
import com.scantoorder.scantoorder.exception.SeatOwnershipException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the gaps a pass over the guest, staff, and payment flows kept tripping on:
 * the public health check, role boundaries, another table's order, join codes, a closed
 * restaurant, a sold-out dish, and an order that has not been paid.
 */
@SpringBootTest
class SecurityPassTest {

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private SeatService seatService;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private OrderService orderService;
    @Value("${paystack.secret.key:}")
    private String paystackSecret;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authentication = null;
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        authentication = null;
        SecurityContextHolder.clearContext();
        tenants.resetMain();
    }

    @Test
    void testThatThePublicHealthCheckLeaksNothingAndDiscouragesSniffing() throws Exception {
        var health = perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertEquals("nosniff", health.getHeader("X-Content-Type-Options"),
                "headers were " + health.getHeaderNames());
        String body = health.getContentAsString();
        assertEquals("ok", body);
        String lower = body.toLowerCase();
        assertFalse(lower.contains("session"));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("secret"));
        assertFalse(lower.contains("jdbc"));
        assertFalse(lower.contains("sk_"));

        perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void testThatAForgedOrMissingWebhookSignatureIsRejected() throws Exception {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"PAY-FORGED\",\"amount\":1}}";
        // A missing signature is rejected by validation before the signature check. A wrong one fails the check.
        perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", "deadbeef")
                        .content(body))
                .andExpect(status().isUnauthorized());
        perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void testThatAWaiterOrChefCannotDoManagerWork() throws Exception {
        support.clearFloor();
        signIn("WAITER");
        perform(post("/api/v1/workers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Chef\",\"role\":\"CHEF\",\"handle\":\"newchef\"}"))
                .andExpect(status().isForbidden());
        perform(get("/api/v1/workers")).andExpect(status().isForbidden());
        perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Secret dish\",\"itemDescription\":\"x\",\"CategoryName\":\"x\",\"itemPrice\":10}"))
                .andExpect(status().isForbidden());

        signIn("CHEF");
        perform(get("/api/v1/workers")).andExpect(status().isForbidden());
        perform(post("/api/v1/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatCapacity\":4}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testThatAGuestCannotReadOrPayAnotherTablesOrder() throws Exception {
        support.clearFloor();
        support.saveItem("Security stew", 2500, true);
        RestaurantTable tableA = support.newTable(2);
        RestaurantTable tableB = support.newTable(2);
        CustomerPrincipal guestA = support.claim(support.seatsOf(tableA).get(0), "Ada");
        CustomerPrincipal guestB = support.claim(support.seatsOf(tableB).get(0), "Bola");
        CreateOrderResponse order = orderService.createOrder(orderFor("Security stew"), guestA);

        signIn(guestB);
        perform(get("/api/v1/orders/" + order.getOrderId() + "/status"))
                .andExpect(status().isNotFound());
        perform(post("/api/v1/payments/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + order.getOrderId() + "\",\"amount\":100}"))
                .andExpect(status().isNotFound());

        signIn(guestA);
        perform(get("/api/v1/orders/" + order.getOrderId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING_PAYMENT")));
        assertTrue(orderService.getAllPaidOrders(support.tenantId()).stream()
                .noneMatch(row -> order.getOrderId().equals(row.getOrderId())));
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getOrderStatus());
    }

    @Test
    void testThatGuessingJoinCodesDoesNotOpenAHeldSeat() {
        support.clearFloor();
        RestaurantTable table = support.newTable(4);
        List<Seat> seats = support.seatsOf(table);
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        String heldId = seats.get(1).getSeatId();
        String joinCode = diningSessionService.holdSeats(host, List.of(heldId)).getJoinCode();

        for (int attempt = 0; attempt < 40; attempt++) {
            String guess = String.format("ZZ%04d", attempt);
            if (guess.equalsIgnoreCase(joinCode)) continue;
            assertThrows(SeatOwnershipException.class, () -> seatService.joinSeat(heldId, join(guess), null));
        }
        assertEquals(SeatStatus.HELD, support.reload(seats.get(1)).getStatus());
        assertTrue(seatService.joinSeat(heldId, join(joinCode), null).getToken() != null);
    }

    @Test
    void testThatAnExpiredHoldCannotBeJoinedAndAClosedRestaurantTakesNoNewGuest() {
        support.clearFloor();
        RestaurantTable table = support.newTable(4);
        List<Seat> seats = support.seatsOf(table);
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        String heldId = seats.get(1).getSeatId();
        String joinCode = diningSessionService.holdSeats(host, List.of(heldId)).getJoinCode();
        support.expireHold(heldId);
        assertThrows(SeatStatusException.class, () -> seatService.joinSeat(heldId, join(joinCode), null));

        var tenant = tenants.main();
        tenant.setOpenForOrders(false);
        tenants.save(tenant);
        assertThrows(PlaceClosedException.class, () -> support.claim(seats.get(2), "late"));
    }

    @Test
    void testThatASoldOutDishCannotBeOrdered() {
        support.clearFloor();
        support.saveItem("Last chop", 1800, false);
        CustomerPrincipal guest = support.claim(support.seatsOf(support.newTable(2)).get(0), "Ada");
        assertThrows(ConflictException.class, () -> orderService.createOrder(orderFor("Last chop"), guest));
    }

    private CreateOrderRequest orderFor(String itemName) {
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName(itemName);
        line.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.getOrderItems().add(line);
        return request;
    }

    private JoinSeatRequest join(String code) {
        JoinSeatRequest request = new JoinSeatRequest();
        request.setJoinCode(code);
        request.setCustomerName("friend");
        request.setCustomerPhoneNumber("08012345678");
        return request;
    }

    private void signIn(String role) {
        signIn(new WorkerPrincipal("worker-" + role, role, tenants.mainId()), "ROLE_" + role);
    }

    private void signIn(CustomerPrincipal guest) {
        signIn(guest, "ROLE_CUSTOMER");
    }

    private void signIn(Object principal, String role) {
        authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        if (authentication != null) {
            builder.with(authentication(authentication));
        }
        return mockMvc.perform(builder);
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(paystackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte value : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
