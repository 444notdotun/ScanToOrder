package com.scantoorder.scantoorder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.dtos.request.*;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.service.Interface.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class ControllerSecurityValidationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext wac;

    @MockitoBean
    private SeatService seatService;

    @MockitoBean
    private DiningSessionService diningSessionService;

    @MockitoBean
    private Auth auth;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderItemService orderItemService;

    @MockitoBean
    private RestaurantTableService restaurantTableService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void signInAs(String role) {
        org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        Object principal = "ROLE_CUSTOMER".equals(role)
                ? new com.scantoorder.scantoorder.data.model.CustomerPrincipal("sess-1", "seat-1", "table-1", "member-1", "tenant-1")
                : new com.scantoorder.scantoorder.data.model.WorkerPrincipal("worker-1", role.replace("ROLE_", ""), "tenant-1");
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }

    @org.junit.jupiter.api.AfterEach
    public void signOut() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .build();
    }

    @Test
    public void testInvalidPathVariableValidation_shouldReturn4xx() throws Exception {
        // reference path variables containing invalid characters (e.g. script tags, special symbols)
        // should be rejected by validation with 400 Bad Request or 4xx due to validation constraints
        mockMvc.perform(get("/api/v1/payments/verify/<script>alert(1)</script>")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/v1/receipts/<script>")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testSeatControllerEndpoints() throws Exception {
        // Test Claim Seat
        ClaimSeatRequest claimRequest = new ClaimSeatRequest();
        claimRequest.setSeatId("seat-123");
        claimRequest.setTableId("table-456");
        claimRequest.setCustomerEmail("customer@example.com");
        claimRequest.setCustomerName("John Doe");
        claimRequest.setCustomerPhoneNumber("12345678901");

        SeatClaimedResponse claimResponse = new SeatClaimedResponse();
        claimResponse.setToken("mock-jwt-token");
        claimResponse.setMessage("Seat claimed successfully");

        Mockito.when(seatService.claimSeat(any(ClaimSeatRequest.class), any())).thenReturn(claimResponse);

        mockMvc.perform(post("/api/v1/seats/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claimRequest)))
                .andExpect(status().isCreated());

        // Seat overrides are staff only
        signInAs("ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/seats/seat-123/release"))
                .andExpect(status().isForbidden());
        signInAs("ROLE_WAITER");

        // Test Update Seat
        UpdateSeatRequest updateRequest = new UpdateSeatRequest();
        updateRequest.setSeatId("seat-123");
        updateRequest.setNewState("VACANT");

        UpdateSeatResponse updateResponse = new UpdateSeatResponse("Updated", "seat-123", "S1", "OCCUPIED");
        Mockito.when(seatService.updateSeat(any(), any(UpdateSeatRequest.class))).thenReturn(updateResponse);

        mockMvc.perform(patch("/api/v1/seats/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // Test View Seat Status
        Mockito.when(seatService.viewAllSeatStatus(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/seats/status"))
                .andExpect(status().isOk());

        // Test Release Seat (Valid and Invalid)
        ReleaseSeatResponse releaseResponse = new ReleaseSeatResponse("Released", "seat-123", "S1", "VACANT");
        Mockito.when(seatService.releaseSeat(any(), eq("seat-123"))).thenReturn(releaseResponse);

        mockMvc.perform(post("/api/v1/seats/seat-123/release"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/seats/invalid<id>/release"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testSessionControllerEndpoints() throws Exception {
        // Closing a session is staff only
        signInAs("ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/sessions/sess-123/close"))
                .andExpect(status().isForbidden());
        signInAs("ROLE_WAITER");

        // Test Close Session (Valid and Invalid)
        CloseSessionResponse closeResponse = new CloseSessionResponse("Closed", "sess-123", "CLOSED");
        Mockito.when(diningSessionService.closeSession(any(), eq("sess-123"))).thenReturn(closeResponse);

        mockMvc.perform(post("/api/v1/sessions/sess-123/close"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/invalid<id>/close"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testAuthControllerEndpoints() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("worker@example.com");
        loginRequest.setPassword("password123");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken("worker-jwt-token");
//        authResponse.setMessage("Login successful");

        Mockito.when(auth.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    public void testMenuControllerEndpoints() throws Exception {
        signInAs("ROLE_MANAGER");
        // Test Generate Menu
        MenuResponse menuResponse = new MenuResponse();
        menuResponse.setCategoryAndItemResponse(Collections.emptyList());
        Mockito.when(menuService.generateMenu(any())).thenReturn(menuResponse);

        mockMvc.perform(get("/api/v1/menu"))
                .andExpect(status().isOk());
    }

    @Test
    public void testItemControllerEndpoints() throws Exception {
        // Test Create Item
        CreateItemRequest createItemRequest = new CreateItemRequest();
        createItemRequest.setItemName("Pizza");
        createItemRequest.setItemDescription("Cheese Pizza");
        createItemRequest.setItemPrice(10);
        createItemRequest.setCategoryName("Main Course");

        CreateItemResponse createItemResponse = new CreateItemResponse("Created", "item-123", "Pizza");
        Mockito.when(itemService.createItem(any(CreateItemRequest.class))).thenReturn(createItemResponse);

        org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MANAGER"));
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("manager", null, authorities));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        String createItemJson = "{\"itemName\":\"Pizza\",\"itemDescription\":\"Cheese Pizza\",\"CategoryName\":\"Main Course\",\"itemPrice\":10}";
        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemJson))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated());

        // Test Toggle Item
        ToggleItemResponse toggleResponse = new ToggleItemResponse("Toggled", "item-123", true);
        Mockito.when(itemService.toggleItem("item-123")).thenReturn(toggleResponse);

        mockMvc.perform(patch("/api/v1/items/item-123/toggle"))
                .andExpect(status().isOk());
    }

    @Test
    public void testOrderControllerEndpoints() throws Exception {
        // Test Create Order
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setOrderItems(Collections.emptyList());
        String seatId = "seat-123";
        String tableId = "table-456";
        String sessionId = "sess-789";

        CreateOrderResponse orderResponse = new CreateOrderResponse();
        orderResponse.setOrderId("order-123");
        orderResponse.setOrderStatus(OrderStatus.PENDING_PAYMENT);

        Mockito.when(orderService.createOrder(any(CreateOrderRequest.class), any(com.scantoorder.scantoorder.data.model.CustomerPrincipal.class))).thenReturn(orderResponse);

        com.scantoorder.scantoorder.data.model.CustomerPrincipal mockPrincipal = new com.scantoorder.scantoorder.data.model.CustomerPrincipal(sessionId, seatId, tableId, "member-1", "tenant-1");
        org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"));
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(mockPrincipal, null, authorities));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated());
        
        // Test Update Order (Valid and Invalid)
        Order mockOrder = new Order();
        mockOrder.setOrderId("order-123");
        mockOrder.setOrderStatus(OrderStatus.PAID);
        Mockito.when(orderService.updateOrder(any(), eq("PAID"), eq("order-123"))).thenReturn(mockOrder);

        mockMvc.perform(patch("/api/v1/orders/order-123?status=PAID"))
                .andExpect(status().isForbidden());
        signInAs("ROLE_WAITER");
        mockMvc.perform(patch("/api/v1/orders/order-123?status=PAID"))
                .andExpect(status().isOk());

        // Test Check Order Status
        Mockito.when(orderService.checkOrderStatus(any(), eq("order-123"))).thenReturn("PAID");

        mockMvc.perform(get("/api/v1/orders/order-123/status"))
                .andExpect(status().isOk());

        // Test Get All Paid Orders
        Mockito.when(orderService.getAllPaidOrders(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/orders/paid"))
                .andExpect(status().isOk());
    }

    @Test
    public void testOrderItemControllerEndpoints() throws Exception {
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.setItemName("Pizza");
        itemRequest.setQuantity(2);
        itemRequest.setSpecialInstructions("No onions");

        signInAs("ROLE_CUSTOMER");
        Mockito.when(orderItemService.addItemToMyOrder(any(), eq("order-123"), any(CreateOrderItemRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/orders/order-123/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders/invalid<id>/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testOnlyAManagerCanCreateStaffOrTables() throws Exception {
        String worker = "{\"name\":\"New Waiter\",\"role\":\"WAITER\",\"username\":\"newwaiter\"}";
        String table = "{\"seatCapacity\":2}";
        for (String role : new String[]{"ROLE_CUSTOMER", "ROLE_WAITER", "ROLE_CHEF"}) {
            signInAs(role);
            mockMvc.perform(post("/api/v1/workers").contentType(MediaType.APPLICATION_JSON).content(worker))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/workers"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/tables").contentType(MediaType.APPLICATION_JSON).content(table))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/tables/table-1/close"))
                    .andExpect(status().is(role.equals("ROLE_WAITER") ? 200 : 403));
        }
        signInAs("ROLE_MANAGER");
        mockMvc.perform(post("/api/v1/workers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky\",\"role\":\"MANAGER\",\"username\":\"sneaky\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/tables").contentType(MediaType.APPLICATION_JSON).content("{\"seatCapacity\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/tables").contentType(MediaType.APPLICATION_JSON).content("{\"seatCapacity\":21}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/tables").contentType(MediaType.APPLICATION_JSON).content(table))
                .andExpect(status().isCreated());
    }

    @Test
    public void testCustomerSeatingEndpointsNeedACustomer() throws Exception {
        signInAs("ROLE_WAITER");
        mockMvc.perform(get("/api/v1/sessions/me")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sessions/me/leave")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sessions/me/holds").contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[\"seat-1\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sessions/me/guests").contentType(MediaType.APPLICATION_JSON).content("{\"seatId\":\"seat-1\",\"guestName\":\"Grandma\"}"))
                .andExpect(status().isForbidden());

        signInAs("ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/sessions/me/holds").contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/sessions/me/guests").contentType(MediaType.APPLICATION_JSON).content("{\"seatId\":\"seat-1\",\"guestName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/seats/seat-1/join").contentType(MediaType.APPLICATION_JSON).content("{\"joinCode\":\"ABC234\",\"customerName\":\"Friend\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testTableControllerQrCode() throws Exception {
        signInAs("ROLE_WAITER");
        Mockito.when(restaurantTableService.getQrCode(any(), eq("T1"))).thenReturn(new byte[]{1, 2, 3});
        mockMvc.perform(get("/api/v1/tables/T1/qrcode"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tables/invalid<tableNumber>/qrcode"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testCsvSanitizationForFormulaTriggers() {
        try {
            com.scantoorder.scantoorder.service.ReceiptServiceImpl serviceImpl = new com.scantoorder.scantoorder.service.ReceiptServiceImpl();
            java.lang.reflect.Method method = com.scantoorder.scantoorder.service.ReceiptServiceImpl.class.getDeclaredMethod("sanitizeForCsv", String.class);
            method.setAccessible(true);

            // Test risky formulas
            String resultFormula1 = (String) method.invoke(serviceImpl, "=SUM(1,2)");
            assertEquals("\"'=SUM(1,2)\"", resultFormula1);

            String resultFormula2 = (String) method.invoke(serviceImpl, "@SUM(1,2)");
            assertEquals("\"'@SUM(1,2)\"", resultFormula2);

            String resultFormula3 = (String) method.invoke(serviceImpl, "+CMD");
            assertEquals("'+CMD", resultFormula3);

            String resultFormula4 = (String) method.invoke(serviceImpl, "-10");
            assertEquals("'-10", resultFormula4);

            // Test normal string
            String resultNormal = (String) method.invoke(serviceImpl, "NormalString");
            assertEquals("NormalString", resultNormal);

            // Test csv escaping
            String resultComma = (String) method.invoke(serviceImpl, "Hello, World");
            assertEquals("\"Hello, World\"", resultComma);

            String resultQuotes = (String) method.invoke(serviceImpl, "Hello \"World\"");
            assertEquals("\"Hello \"\"World\"\"\"", resultQuotes);

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
