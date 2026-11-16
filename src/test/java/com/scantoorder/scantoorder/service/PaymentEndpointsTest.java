package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentEndpointsTest {
    private record Endpoint(String label, HttpMethod method, String path, String body, Set allowed, OrderStatus rightState, OrderStatus wrongState) {
    }

    private enum Set { CHEF_MANAGER, WAITER_MANAGER, MANAGER }

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @Autowired
    private OrderRepo orderRepo;
    @MockitoBean
    private PaystackClient paystackClient;

    private MockMvc mockMvc;
    private Tenant tenant;
    private Item plate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        tenant = tenants.main();
        plate = support.item(tenant, "Endpoint plate", 1200, true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String role, Tenant forTenant) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", forTenant.getTenantId())
                : new WorkerPrincipal("worker-" + role, role, role.equals("PLATFORM_ADMIN") ? null : forTenant.getTenantId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }

    private int call(Endpoint endpoint, String orderId) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path().replace("{id}", orderId));
        if (endpoint.body() != null) builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private boolean permitted(Endpoint endpoint, String role) {
        return switch (endpoint.allowed()) {
            case CHEF_MANAGER -> role.equals("CHEF") || role.equals("MANAGER");
            case WAITER_MANAGER -> role.equals("WAITER") || role.equals("MANAGER");
            case MANAGER -> role.equals("MANAGER");
        };
    }

    private List<Endpoint> endpoints() {
        String swap = "{\"items\":[{\"itemName\":\"Endpoint plate\",\"quantity\":1,\"specialInstructions\":\"x\"}]}";
        return List.of(
                new Endpoint("unavailable", HttpMethod.POST, "/api/v1/orders/{id}/unavailable", "{\"reason\":\"RAN_OUT\"}", Set.CHEF_MANAGER, OrderStatus.PAID, OrderStatus.DELIVERED),
                new Endpoint("swap", HttpMethod.POST, "/api/v1/orders/{id}/swap", swap, Set.WAITER_MANAGER, OrderStatus.UNAVAILABLE, OrderStatus.PAID),
                new Endpoint("refund request", HttpMethod.POST, "/api/v1/orders/{id}/refund-request", null, Set.WAITER_MANAGER, OrderStatus.UNAVAILABLE, OrderStatus.PAID));
    }

    @Test
    void testThatEachActionIsOpenOnlyToItsRolesAndWorksInTheRightState() throws Exception {
        for (Endpoint endpoint : endpoints()) {
            for (String role : List.of("CHEF", "WAITER", "MANAGER", "CUSTOMER", "PLATFORM_ADMIN")) {
                // "Ran out" takes the plate off the menu, so it goes back on before each try.
                plate = support.item(tenant, "Endpoint plate", 1200, true);
                String orderId = support.paidOrder(tenant, endpoint.rightState(), plate).getOrder().getOrderId();
                String wrongStateId = support.paidOrder(tenant, endpoint.wrongState(), plate).getOrder().getOrderId();
                signIn(role, tenant);
                if (permitted(endpoint, role)) {
                    assertEquals(200, call(endpoint, orderId), endpoint.label() + " as " + role);
                    assertEquals(400, call(endpoint, wrongStateId), endpoint.label() + " wrong state as " + role);
                } else {
                    assertEquals(403, call(endpoint, orderId), endpoint.label() + " as " + role);
                    assertEquals(endpoint.rightState(), orderRepo.findOrderByOrderId(orderId).getOrderStatus(), endpoint.label() + " must not change as " + role);
                }
            }
        }
    }

    @Test
    void testThatAnotherBusinessesOrderIsNotFoundLikeAMissingOneForEveryAllowedRole() throws Exception {
        Tenant other = tenants.other();
        Item theirs = support.item(other, "Their endpoint plate", 800, true);
        for (Endpoint endpoint : endpoints()) {
            String theirOrder = support.paidOrder(other, endpoint.rightState(), theirs).getOrder().getOrderId();
            for (String role : List.of("CHEF", "WAITER", "MANAGER")) {
                signIn(role, tenant);
                int theirs404 = call(endpoint, theirOrder);
                int missing = call(endpoint, "no-such-order");
                assertEquals(missing, theirs404, endpoint.label() + " as " + role);
                if (permitted(endpoint, role)) {
                    assertEquals(404, theirs404, endpoint.label() + " as " + role);
                }
            }
            assertEquals(endpoint.rightState(), orderRepo.findOrderByOrderId(theirOrder).getOrderStatus());
        }
    }
}
