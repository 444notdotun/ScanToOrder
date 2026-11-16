package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.request.CreateServiceCallRequest;
import com.scantoorder.scantoorder.dtos.respond.StaffResponse;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import com.scantoorder.scantoorder.service.Interface.ServiceCallService;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A manager, chef, waiter and customer of one business ask for the records of another.
 * Every answer must be the same as for a record that does not exist.
 */
@SpringBootTest
class TenantIsolationApiTest {

    private record Call(String label, HttpMethod method, String path, String body) {
    }

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private SeatService seatService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ServiceCallService serviceCallService;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private DinningSessionRepo sessionRepo;
    @Autowired
    private ServiceCallRepo serviceCallRepo;

    private MockMvc mockMvc;
    private Tenant a;
    private Tenant b;

    private CustomerPrincipal customerA;
    private RestaurantTable tableB;
    private Seat seatB;
    private DinningSession sessionB;
    private Category categoryB;
    private Item itemB;
    private Order orderB;
    private Payment paymentB;
    private ServiceCall callB;
    private Worker workerB;

    @BeforeEach
    void setUp() {
        support.clearFloor();
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        a = tenants.main();
        b = tenants.other();
        workerRepo.findByTenant_TenantIdAndIsActiveTrue(b.getTenantId()).forEach(w -> {
            w.setActive(false);
            workerRepo.save(w);
        });

        RestaurantTable tableA = support.newTable(a.getTenantId(), 2);
        customerA = support.claim(support.seatsOf(tableA).get(0), "Ada");

        tableB = support.newTable(b.getTenantId(), 2);
        seatB = support.seatsOf(tableB).get(0);
        support.claim(seatB, "Bola");
        CustomerPrincipal customerB = support.principalFor(seatB.getSeatId());
        sessionB = sessionRepo.findById(customerB.getSessionId()).orElseThrow();

        Menu menuB = tenants.menuOf(b);
        categoryB = new Category();
        categoryB.setTenant(b);
        categoryB.setMenu(menuB);
        categoryB.setCategoryName("B starters");
        categoryB = categoryRepository.save(categoryB);
        itemB = new Item();
        itemB.setTenant(b);
        itemB.setItemName("B special");
        itemB.setItemPrice(BigDecimal.valueOf(900));
        itemB.setCategoryId(categoryB);
        itemB = itemRepo.save(itemB);

        CreateOrderRequest request = new CreateOrderRequest();
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName("B special");
        line.setQuantity(1);
        request.getOrderItems().add(line);
        orderB = orderRepo.findOrderByOrderId(orderService.createOrder(request, customerB).getOrderId());

        paymentB = new Payment();
        paymentB.setTenant(b);
        paymentB.setOrder(orderB);
        paymentB.setAmount(BigDecimal.valueOf(900));
        paymentB = paymentRepo.save(paymentB);

        CreateServiceCallRequest callRequest = new CreateServiceCallRequest();
        callRequest.setRequestType("WATER");
        callB = serviceCallService.createServiceCall(callRequest, b.getTenantId(), customerB.getSessionId());

        StaffResponse staffB = workerService.createWorker(b.getTenantId(), "B waiter", "waiter", "isol" + System.nanoTime() % 100000);
        workerB = workerRepo.findById(staffB.getWorkerId()).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String role) {
        Object principal = role.equals("CUSTOMER") ? customerA
                : new WorkerPrincipal("worker-a", role, a.getTenantId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }

    private List<Call> calls(boolean other) {
        String tableNumber = other ? tableB.getTableNumber() : "TABLE-NOPE0000";
        String tableId = other ? tableB.getTableId() : "no-such-table";
        String seatId = other ? seatB.getSeatId() : "no-such-seat";
        String sessionId = other ? sessionB.getSessionId() : "no-such-session";
        String categoryId = other ? categoryB.getCategoryId() : "no-such-category";
        String itemId = other ? itemB.getItemId() : "no-such-item";
        String orderId = other ? orderB.getOrderId() : "no-such-order";
        String reference = other ? paymentB.getReference() : "PAYMENT-NOPE0000";
        String callId = other ? callB.getServiceCallId() : "no-such-call";
        String workerId = other ? workerB.getWorkerId() : "no-such-worker";
        String itemBody = "{\"itemName\":\"x\",\"itemDescription\":\"x\",\"CategoryName\":\"x\",\"itemPrice\":10}";
        List<Call> list = new ArrayList<>();
        list.add(new Call("seat map", HttpMethod.GET, "/api/v1/tables/" + tableNumber + "/seatMap", null));
        list.add(new Call("qr code", HttpMethod.GET, "/api/v1/tables/" + tableNumber + "/qrcode", null));
        list.add(new Call("table sessions", HttpMethod.GET, "/api/v1/tables/" + tableId + "/sessions", null));
        list.add(new Call("close table", HttpMethod.POST, "/api/v1/tables/" + tableId + "/close", null));
        list.add(new Call("delete table", HttpMethod.DELETE, "/api/v1/tables/" + tableId, null));
        list.add(new Call("release seat", HttpMethod.POST, "/api/v1/seats/" + seatId + "/release", null));
        list.add(new Call("update seat", HttpMethod.PATCH, "/api/v1/seats/update", "{\"seatId\":\"" + seatId + "\",\"newState\":\"VACANT\"}"));
        list.add(new Call("close session", HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/close", null));
        list.add(new Call("delete category", HttpMethod.DELETE, "/api/v1/categories/" + categoryId, null));
        list.add(new Call("toggle item", HttpMethod.PATCH, "/api/v1/items/" + itemId + "/toggle", null));
        list.add(new Call("update item", HttpMethod.PUT, "/api/v1/items/" + itemId, itemBody));
        list.add(new Call("delete item", HttpMethod.DELETE, "/api/v1/items/" + itemId, null));
        list.add(new Call("change order status", HttpMethod.PATCH, "/api/v1/orders/" + orderId + "?status=PAID", null));
        list.add(new Call("read order status", HttpMethod.GET, "/api/v1/orders/" + orderId + "/status", null));
        list.add(new Call("kitchen status", HttpMethod.PATCH, "/api/v1/kitchen/orders/" + orderId + "/status?status=PAID", null));
        list.add(new Call("add order item", HttpMethod.POST, "/api/v1/orders/" + orderId + "/items", "{\"itemName\":\"B special\",\"quantity\":1,\"specialInstructions\":\"x\"}"));
        list.add(new Call("verify payment", HttpMethod.GET, "/api/v1/payments/verify/" + reference, null));
        list.add(new Call("pay for order", HttpMethod.POST, "/api/v1/payments/initialize", "{\"orderId\":\"" + orderId + "\",\"customerEmail\":\"a@example.com\"}"));
        list.add(new Call("receipt", HttpMethod.GET, "/api/v1/receipts/" + reference, null));
        list.add(new Call("service call", HttpMethod.PATCH, "/api/v1/service-calls/" + callId, "{\"status\":\"IN_PROGRESS\"}"));
        list.add(new Call("remove staff", HttpMethod.DELETE, "/api/v1/workers/" + workerId, null));
        list.add(new Call("hold seat", HttpMethod.POST, "/api/v1/sessions/me/holds", "{\"seatIds\":[\"" + seatId + "\"]}"));
        list.add(new Call("release hold", HttpMethod.DELETE, "/api/v1/sessions/me/holds/" + seatId, null));
        list.add(new Call("confirm guest", HttpMethod.POST, "/api/v1/sessions/me/guests", "{\"seatId\":\"" + seatId + "\",\"guestName\":\"Grandma\"}"));
        list.add(new Call("order for seat", HttpMethod.POST, "/api/v1/orders", "{\"seatId\":\"" + seatId + "\",\"orderItems\":[{\"itemName\":\"B special\",\"quantity\":1,\"specialInstructions\":\"x\"}]}"));
        return list;
    }

    private int status(Call call) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(call.method(), call.path());
        if (call.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(call.body());
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private void assertIsolated(String role, int minimumNotFound) throws Exception {
        signIn(role);
        List<Call> other = calls(true);
        List<Call> missing = calls(false);
        int notFound = 0;
        StringBuilder problems = new StringBuilder();
        for (int i = 0; i < other.size(); i++) {
            int otherStatus = status(other.get(i));
            int missingStatus = status(missing.get(i));
            if (otherStatus != missingStatus) {
                problems.append(role).append(" / ").append(other.get(i).label())
                        .append(": other business gave ").append(otherStatus)
                        .append(" but a missing record gave ").append(missingStatus).append("\n");
            }
            if (otherStatus == 404) notFound++;
            if (otherStatus == 200 || otherStatus == 201) {
                problems.append(role).append(" / ").append(other.get(i).label()).append(" succeeded on another business\n");
            }
        }
        assertEquals("", problems.toString());
        assertTrue(notFound >= minimumNotFound, role + " got only " + notFound + " not-found answers");
        assertOtherBusinessUntouched();
    }

    private void assertOtherBusinessUntouched() {
        assertTrue(tableRepo.findById(tableB.getTableId()).orElseThrow().isActive());
        assertEquals(SeatStatus.OCCUPIED, seatRepo.findSeatBySeatId(seatB.getSeatId()).orElseThrow().getStatus());
        assertEquals(DinningSessionStatus.ACTIVE, sessionRepo.findById(sessionB.getSessionId()).orElseThrow().getSessionStatus());
        assertTrue(categoryRepository.findById(categoryB.getCategoryId()).isPresent());
        Item item = itemRepo.findById(itemB.getItemId()).orElseThrow();
        assertTrue(item.isAvailable());
        assertEquals("B special", item.getItemName());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepo.findOrderByOrderId(orderB.getOrderId()).getOrderStatus());
        assertEquals(PaymentStatus.PENDING, paymentRepo.findById(paymentB.getId()).orElseThrow().getStatus());
        assertEquals(ServiceStatus.UNASSIGNED, serviceCallRepo.findById(callB.getServiceCallId()).orElseThrow().getServiceStatus());
        assertTrue(workerRepo.findById(workerB.getWorkerId()).orElseThrow().isActive());
    }

    @Test
    void testThatAManagerOfOneBusinessCannotReachAnotherBusinessesRecords() throws Exception {
        assertIsolated("MANAGER", 20);
    }

    @Test
    void testThatAWaiterOfOneBusinessCannotReachAnotherBusinessesRecords() throws Exception {
        assertIsolated("WAITER", 12);
    }

    @Test
    void testThatAChefOfOneBusinessCannotReachAnotherBusinessesRecords() throws Exception {
        assertIsolated("CHEF", 1);
    }

    @Test
    void testThatACustomerOfOneBusinessCannotReachAnotherBusinessesRecords() throws Exception {
        assertIsolated("CUSTOMER", 5);
    }

    @Test
    void testThatListsOnlyShowTheCallersOwnBusiness() throws Exception {
        signIn("MANAGER");
        for (String path : List.of("/api/v1/tables", "/api/v1/seats/status", "/api/v1/menu", "/api/v1/categories",
                "/api/v1/workers", "/api/v1/service-calls", "/api/v1/kitchen/orders", "/api/v1/orders/paid")) {
            String body = mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(tableB.getTableId()), path);
            assertFalse(body.contains(seatB.getSeatId()), path);
            assertFalse(body.contains("B special"), path);
            assertFalse(body.contains("B starters"), path);
            assertFalse(body.contains(workerB.getUsername()), path);
            assertFalse(body.contains(callB.getServiceCallId()), path);
        }
    }

    @Test
    void testThatTwoBusinessesCanUseTheSameNamesAndNumbers() {
        support.newTable(a.getTenantId(), 1);
        long sameNumberTables = tableRepo.findAll().stream().filter(t -> t.getDisplayNumber() != null && t.getDisplayNumber() == 1).count();
        assertEquals(2, sameNumberTables);

        tenants.signInAsManager();
        Item same = new Item();
        same.setTenant(a);
        same.setItemName("B special");
        same.setItemPrice(BigDecimal.TEN);
        assertNotNull(itemRepo.save(same).getItemId());
    }
}
