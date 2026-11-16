package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.OrderItemService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ServiceDayTest {
    @Autowired
    private OrderStepService steps;
    @Autowired
    private ServiceBoardService board;
    @Autowired
    private MenuStockService stock;
    @Autowired
    private MenuService menuService;
    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private LiveUpdates liveUpdates;
    @Autowired
    private SeatingTestSupport seating;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ServiceCallRepo callRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo sessionRepo;
    @Autowired
    private WebApplicationContext wac;
    @PersistenceContext
    private EntityManager entityManager;

    private Tenant tenant;
    private String tenantId;
    private Item plate;
    private Worker bisi;
    private Worker chidi;
    private Worker emeka;
    private Worker ngozi;

    @BeforeEach
    void setUp() {
        tenant = tenants.freshActiveTenant();
        tenant.setOpenForOrders(true);
        tenantId = tenants.save(tenant).getTenantId();
        plate = pay.item(tenant, "Service plate", 1500, true);
        bisi = worker("Bisi Adebayo", WorkerRole.CHEF);
        chidi = worker("Chidi Okoro", WorkerRole.CHEF);
        emeka = worker("Emeka Eze", WorkerRole.WAITER);
        ngozi = worker("Ngozi Bello", WorkerRole.WAITER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Worker worker(String name, WorkerRole role) {
        Worker worker = new Worker();
        worker.setTenant(tenant);
        worker.setFullName(name);
        worker.setUsername("svc." + role.name().toLowerCase() + "." + System.nanoTime());
        worker.setPassword("x");
        worker.setRole(role);
        return workerRepo.save(worker);
    }

    private Order paid(OrderStatus status) {
        Order order = pay.order(tenant, status, plate, 1);
        order.setPaidAt(LocalDateTime.now().minusMinutes(3));
        return orderRepo.save(order);
    }

    private Order reload(Order order) {
        entityManager.flush();
        entityManager.clear();
        return orderRepo.findOrderByOrderId(order.getOrderId());
    }

    // ---- the timeline

    @Test
    void testThatAnOrderMovesForwardOneStepAtATimeAndRecordsEachTimeAndWho() {
        Order order = paid(OrderStatus.PAID);

        OrderActionResponse started = steps.start(tenantId, bisi.getWorkerId(), order.getOrderId());
        OrderActionResponse ready = steps.ready(tenantId, bisi.getWorkerId(), order.getOrderId());
        OrderActionResponse delivered = steps.deliver(tenantId, emeka.getWorkerId(), order.getOrderId());

        assertEquals("PREPARING", started.getStatus());
        assertEquals("READY", ready.getStatus());
        assertEquals("DELIVERED", delivered.getStatus());
        assertEquals(order.getOrderNumber() + " was delivered to the seat.", delivered.getMessage());
        Order saved = reload(order);
        assertNotNull(saved.getPreparingAt());
        assertNotNull(saved.getReadyAt());
        assertNotNull(saved.getDeliveredAt());
        assertFalse(saved.getReadyAt().isBefore(saved.getPreparingAt()));
        assertEquals("Chef Bisi", saved.getPreparingBy());
        assertEquals("Waiter Emeka", saved.getDeliveredBy());
    }

    @Test
    void testThatEveryStepOutOfOrderIsRefusedAndChangesNothing() {
        Order fresh = paid(OrderStatus.PAID);
        assertThrows(OrderStatusException.class, () -> steps.ready(tenantId, bisi.getWorkerId(), fresh.getOrderId()));
        assertThrows(OrderStatusException.class, () -> steps.deliver(tenantId, emeka.getWorkerId(), fresh.getOrderId()));
        Order cooking = paid(OrderStatus.PREPARING);
        assertThrows(OrderStatusException.class, () -> steps.deliver(tenantId, emeka.getWorkerId(), cooking.getOrderId()));
        for (OrderStatus other : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELED, OrderStatus.UNAVAILABLE, OrderStatus.REFUND_DUE, OrderStatus.REFUNDED)) {
            Order order = paid(other);
            assertThrows(OrderStatusException.class, () -> steps.start(tenantId, bisi.getWorkerId(), order.getOrderId()), other.name());
            assertThrows(OrderStatusException.class, () -> steps.ready(tenantId, bisi.getWorkerId(), order.getOrderId()), other.name());
            assertThrows(OrderStatusException.class, () -> steps.deliver(tenantId, emeka.getWorkerId(), order.getOrderId()), other.name());
            assertEquals(other, reload(order).getOrderStatus());
        }
        assertEquals(OrderStatus.PAID, reload(fresh).getOrderStatus());
        assertEquals(OrderStatus.PREPARING, reload(cooking).getOrderStatus());
    }

    @Test
    void testThatTheSecondChefOrWaiterIsToldWhoGotThereFirst() {
        Order order = paid(OrderStatus.PAID);
        steps.start(tenantId, bisi.getWorkerId(), order.getOrderId());

        ConflictException second = assertThrows(ConflictException.class, () -> steps.start(tenantId, chidi.getWorkerId(), order.getOrderId()));
        assertEquals("Chef Bisi has already started " + order.getOrderNumber() + ".", second.getMessage());

        steps.ready(tenantId, bisi.getWorkerId(), order.getOrderId());
        assertEquals("Chef Bisi has already marked " + order.getOrderNumber() + " ready.",
                assertThrows(ConflictException.class, () -> steps.ready(tenantId, chidi.getWorkerId(), order.getOrderId())).getMessage());
        assertEquals("Chef Bisi has already started " + order.getOrderNumber() + ".",
                assertThrows(ConflictException.class, () -> steps.start(tenantId, chidi.getWorkerId(), order.getOrderId())).getMessage());

        steps.deliver(tenantId, emeka.getWorkerId(), order.getOrderId());
        assertEquals("Waiter Emeka has already delivered " + order.getOrderNumber() + ".",
                assertThrows(ConflictException.class, () -> steps.deliver(tenantId, ngozi.getWorkerId(), order.getOrderId())).getMessage());
        assertEquals("Chef Bisi", reload(order).getPreparingBy());
    }

    @Test
    void testThatTheOlderPatchRoutesFollowTheSameRules() {
        Order order = paid(OrderStatus.PAID);
        assertThrows(OrderStatusException.class, () -> orderService.updateOrder(tenantId, "READY", order.getOrderId()));
        assertEquals(OrderStatus.PREPARING, orderService.updateOrder(tenantId, "PREPARING", order.getOrderId()).getOrderStatus());
        assertThrows(ConflictException.class, () -> orderService.updateOrder(tenantId, "PREPARING", order.getOrderId()));
        assertThrows(OrderStatusException.class, () -> orderService.updateOrder(tenantId, "NONSENSE", order.getOrderId()));
        assertNotNull(reload(order).getPreparingAt());
    }

    // ---- the kitchen and the waiter lists

    @Test
    void testThatTheKitchenListIsPaidCookingAndReadyOldestFirstWithTableSeatGuestAndItems() {
        RestaurantTable table = seating.newTable(tenantId, 2);
        Seat seat = seating.seatsOf(table).get(0);
        CustomerPrincipal guest = seating.claim(seat, "Ada Obi");
        SessionSeat membership = sessionSeatRepo.findById(guest.getSessionSeatId()).orElseThrow();
        Order newer = paid(OrderStatus.PAID);
        newer.setPaidAt(LocalDateTime.now().minusMinutes(2));
        newer.setSeat(seat);
        newer.setTable(table);
        newer.setSessionSeat(membership);
        newer.setNote("No pepper");
        orderRepo.save(newer);
        Order older = paid(OrderStatus.PREPARING);
        older.setPaidAt(LocalDateTime.now().minusMinutes(12));
        orderRepo.save(older);
        paid(OrderStatus.READY);
        paid(OrderStatus.DELIVERED);
        paid(OrderStatus.UNAVAILABLE);
        paid(OrderStatus.PENDING_PAYMENT);

        List<ServiceOrderResponse> list = board.kitchen(tenantId);

        assertEquals(3, list.size());
        assertEquals(older.getOrderId(), list.get(0).getOrderId());
        assertTrue(list.get(0).getMinutesWaiting() >= 12);
        ServiceOrderResponse mine = list.stream().filter(o -> o.getOrderId().equals(newer.getOrderId())).findFirst().orElseThrow();
        assertEquals(table.getDisplayNumber(), mine.getTableNumber());
        assertEquals(1, mine.getSeatPosition());
        assertEquals("Ada Obi", mine.getGuestName());
        assertEquals(List.of("1 x Service plate"), mine.getItems());
        assertEquals("No pepper", mine.getNote());
        assertEquals("PAID", mine.getStep());
    }

    @Test
    void testThatASwappedOrderIsATicketOfItsOwnAndSaysSo() {
        Order order = paid(OrderStatus.PAID);
        order.setSwappedAt(LocalDateTime.now().minusMinutes(1));
        orderRepo.save(order);
        assertEquals("Swapped at the table. Cook this instead.", board.kitchen(tenantId).get(0).getFlag());
    }

    @Test
    void testThatAReadyOrderGetsTheColdFlagAtTwentyMinutesAndNotBefore() {
        Order warm = paid(OrderStatus.READY);
        warm.setReadyAt(LocalDateTime.now().minusMinutes(19));
        orderRepo.save(warm);
        Order cold = paid(OrderStatus.READY);
        cold.setReadyAt(LocalDateTime.now().minusMinutes(25).minusSeconds(10));
        orderRepo.save(cold);
        paid(OrderStatus.PREPARING);

        List<ServiceOrderResponse> ready = board.ready(tenantId);

        assertEquals(2, ready.size());
        assertEquals(cold.getOrderId(), ready.get(0).getOrderId(), "oldest first");
        assertEquals("Ready for 25 minutes. It is getting cold.", ready.get(0).getFlag());
        assertNull(ready.get(1).getFlag());
        assertEquals(19, ready.get(1).getMinutesWaiting());
    }

    @Test
    void testThatOrdersTheKitchenCouldNotMakeAreOnTheWaitersVisitList() {
        Order unavailable = paid(OrderStatus.UNAVAILABLE);
        unavailable.setUnavailableReason(UnavailableReason.RAN_OUT);
        orderRepo.save(unavailable);
        paid(OrderStatus.REFUND_DUE);
        paid(OrderStatus.READY);

        List<ServiceOrderResponse> visits = board.visits(tenantId);

        assertEquals(2, visits.size());
        assertTrue(visits.stream().anyMatch(v -> "RAN_OUT".equals(v.getUnavailableReason())));
    }

    // ---- sold out

    @Test
    void testThatSoldOutBlocksAGuestOrderUntilBackInStockAndTheMenuFollows() {
        Order order = pay.order(tenant, OrderStatus.PENDING_PAYMENT, plate, 1);
        CreateOrderItemRequest request = new CreateOrderItemRequest();
        request.setItemName(plate.getItemName());
        request.setQuantity(1);

        ToggleItemResponse out = stock.soldOut(tenantId, plate.getItemId());

        assertEquals("Service plate is now sold out for guests.", out.getMessage());
        assertFalse(itemRepo.findById(plate.getItemId()).orElseThrow().isAvailable());
        assertThrows(ConflictException.class, () -> orderItemService.createOrderItem(tenantId, order.getOrderId(), request));

        ToggleItemResponse back = stock.backInStock(tenantId, plate.getItemId());
        assertEquals("Service plate is back on the menu.", back.getMessage());
        assertEquals(plate.getItemName(), orderItemService.createOrderItem(tenantId, order.getOrderId(), request).getItem().getItemName());
    }

    @Test
    void testThatTheStockListShowsEveryPlateOfThisBusinessWithItsIdAndWhetherItIsInStock() {
        Item second = pay.item(tenant, "Another plate", 900, true);
        stock.soldOut(tenantId, second.getItemId());
        Item theirs = pay.item(tenants.other(), "Not ours at all", 100, true);

        List<StockItemResponse> list = stock.list(tenantId);

        assertTrue(list.stream().anyMatch(s -> s.getItemId().equals(plate.getItemId()) && s.isAvailable()));
        assertTrue(list.stream().anyMatch(s -> s.getItemId().equals(second.getItemId()) && !s.isAvailable()));
        assertTrue(list.stream().noneMatch(s -> s.getItemId().equals(theirs.getItemId())));
    }

    @Test
    void testThatSoldOutClearsTheMenuCacheAndTellsOpenScreens() {
        List<LiveUpdates.Notice> staff = new ArrayList<>();
        List<LiveUpdates.Notice> guest = new ArrayList<>();
        LiveUpdates.Subscription a = liveUpdates.subscribeStaff(tenantId, staff::add);
        LiveUpdates.Subscription b = liveUpdates.subscribeGuest(tenantId, "any-session", guest::add);
        try {
            stock.soldOut(tenantId, plate.getItemId());
            // Notices go out once the change is saved. This test's transaction is rolled back, so run that step by hand.
            for (var synchronization : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            liveUpdates.unsubscribe(a);
            liveUpdates.unsubscribe(b);
        }
        assertTrue(staff.stream().anyMatch(n -> n.type().equals("menu")));
        assertTrue(guest.stream().anyMatch(n -> n.type().equals("menu")));
    }

    @Test
    void testThatAnotherBusinessesItemOrderOrCallAnswersNotFound() {
        Tenant other = tenants.other();
        Item theirs = pay.item(other, "Their stock plate", 100, true);
        Order theirOrder = pay.order(other, OrderStatus.PAID, theirs, 1);
        ServiceCall theirCall = new ServiceCall();
        theirCall.setTenant(other);
        theirCall.setServiceDescription("Water");
        callRepo.save(theirCall);

        assertThrows(ResourceNotFoundException.class, () -> stock.soldOut(tenantId, theirs.getItemId()));
        assertThrows(ResourceNotFoundException.class, () -> stock.backInStock(tenantId, "no-such-item"));
        assertThrows(OrderNotFoundException.class, () -> steps.start(tenantId, bisi.getWorkerId(), theirOrder.getOrderId()));
        assertThrows(OrderNotFoundException.class, () -> steps.ready(tenantId, bisi.getWorkerId(), theirOrder.getOrderId()));
        assertThrows(OrderNotFoundException.class, () -> steps.deliver(tenantId, emeka.getWorkerId(), theirOrder.getOrderId()));
        assertThrows(ResourceNotFoundException.class, () -> board.take(tenantId, emeka.getWorkerId(), theirCall.getServiceCallId()));
        assertThrows(ResourceNotFoundException.class, () -> board.done(tenantId, emeka.getWorkerId(), theirCall.getServiceCallId()));
        assertThrows(TableNotFoundException.class, () -> diningSessionService.closeTable(tenantId, seating.newTable(other.getTenantId(), 2).getTableId(), true));
        assertTrue(board.kitchen(tenantId).isEmpty());
        assertTrue(board.calls(tenantId).isEmpty());
        assertTrue(board.tables(tenantId).isEmpty());
        assertTrue(itemRepo.findById(theirs.getItemId()).orElseThrow().isAvailable());
    }

    // ---- calls

    private ServiceCall call(String where) {
        RestaurantTable table = seating.newTable(tenantId, 2);
        DinningSession session = new DinningSession();
        session.setTableId(table);
        session.setTenant(tenant);
        session = sessionRepo.save(session);
        ServiceCall call = new ServiceCall();
        call.setTenant(tenant);
        call.setSessionId(session);
        call.setServiceDescription(where);
        return callRepo.save(call);
    }

    @Test
    void testThatAWaiterTakesAndFinishesACallAndTheSecondWaiterIsToldWhoHasIt() {
        ServiceCall call = call("Water");
        int tableNumber = call.getSessionId().getTableId().getDisplayNumber();

        WaiterCallResponse open = board.calls(tenantId).get(0);
        assertEquals("OPEN", open.getState());
        assertEquals("Table " + tableNumber, open.getWhere());

        WaiterCallResponse taken = board.take(tenantId, emeka.getWorkerId(), call.getServiceCallId());
        assertEquals("TAKEN", taken.getState());
        assertEquals("Emeka", taken.getTakenBy());
        ServiceCall saved = callRepo.findById(call.getServiceCallId()).orElseThrow();
        assertNotNull(saved.getTakenAt());
        assertEquals(emeka.getWorkerId(), saved.getTakenBy().getWorkerId());

        ConflictException second = assertThrows(ConflictException.class, () -> board.take(tenantId, ngozi.getWorkerId(), call.getServiceCallId()));
        assertEquals("Emeka has already taken Table " + tableNumber + "'s call.", second.getMessage());
        assertEquals("TAKEN", board.take(tenantId, emeka.getWorkerId(), call.getServiceCallId()).getState());

        assertEquals("DONE", board.done(tenantId, emeka.getWorkerId(), call.getServiceCallId()).getState());
        assertNotNull(callRepo.findById(call.getServiceCallId()).orElseThrow().getDoneAt());
        assertTrue(board.calls(tenantId).isEmpty());
        assertThrows(ConflictException.class, () -> board.done(tenantId, ngozi.getWorkerId(), call.getServiceCallId()));
        assertThrows(ConflictException.class, () -> board.take(tenantId, ngozi.getWorkerId(), call.getServiceCallId()));
    }

    // ---- tables and closing

    @Test
    void testThatTheWaitersTablesShowVacantHeldAndOccupiedSeatsAndTheNumberOfGroups() {
        RestaurantTable table = seating.newTable(tenantId, 4);
        List<Seat> seats = seating.seatsOf(table);
        CustomerPrincipal host = seating.claim(seats.get(0), "Ada Obi");
        diningSessionService.holdSeats(host, List.of(seats.get(1).getSeatId()));
        seating.claim(seats.get(3), "Chuka Eze");

        FloorTableResponse view = board.tables(tenantId).get(0);

        assertEquals(table.getDisplayNumber(), view.getDisplayNumber());
        assertEquals(2, view.getGroups());
        assertEquals(List.of("OCCUPIED", "HELD", "VACANT", "OCCUPIED"), view.getSeats().stream().map(FloorTableResponse.Seat::getStatus).toList());
        assertEquals("Ada", view.getSeats().get(0).getGuestFirstName());
        assertEquals("Chuka", view.getSeats().get(3).getGuestFirstName());
        assertTrue(view.getSeats().get(1).getSecondsLeft() > 0);
        assertNull(view.getSeats().get(2).getSecondsLeft());
    }

    @Test
    void testThatClosingATableWithUndeliveredPaidOrdersNeedsConfirmationAndThenFreesEverything() {
        RestaurantTable table = seating.newTable(tenantId, 2);
        List<Seat> seats = seating.seatsOf(table);
        CustomerPrincipal guest = seating.claim(seats.get(0), "Ada");
        Order paidOrder = pay.order(tenant, OrderStatus.PAID, plate, 1);
        paidOrder.setSessionSeat(sessionSeatRepo.findById(guest.getSessionSeatId()).orElseThrow());
        paidOrder.setDinningSession(sessionRepo.findById(guest.getSessionId()).orElseThrow());
        orderRepo.save(paidOrder);

        ConfirmationNeededException needed = assertThrows(ConfirmationNeededException.class,
                () -> diningSessionService.closeTable(tenantId, table.getTableId(), false));

        assertEquals(1, needed.getPaidUndeliveredOrders());
        assertEquals("1 paid orders have not been delivered. The food stays with you and nothing is refunded.", needed.getMessage());
        assertEquals(DinningSessionStatus.ACTIVE, sessionRepo.findById(guest.getSessionId()).orElseThrow().getSessionStatus());
        assertEquals(SeatStatus.OCCUPIED, seating.reload(seats.get(0)).getStatus());

        CloseTableResponse closed = diningSessionService.closeTable(tenantId, table.getTableId(), true);

        assertEquals(1, closed.getClosedSessions());
        assertEquals(1, closed.getPaidUndeliveredOrders());
        DinningSession session = sessionRepo.findById(guest.getSessionId()).orElseThrow();
        assertEquals(DinningSessionStatus.CLOSED, session.getSessionStatus());
        assertTrue(session.isClosedByStaff());
        assertEquals(SeatStatus.VACANT, seating.reload(seats.get(0)).getStatus());
        assertEquals(OrderStatus.PAID, reload(paidOrder).getOrderStatus());
    }

    @Test
    void testThatClosingATableWithNothingToDeliverNeedsNoConfirmation() {
        RestaurantTable table = seating.newTable(tenantId, 2);
        CustomerPrincipal guest = seating.claim(seating.seatsOf(table).get(0), "Ada");

        CloseTableResponse closed = diningSessionService.closeTable(tenantId, table.getTableId(), false);

        assertEquals(1, closed.getClosedSessions());
        assertEquals(DinningSessionStatus.CLOSED, sessionRepo.findById(guest.getSessionId()).orElseThrow().getSessionStatus());
    }

    // ---- what staff and managers see

    @Test
    void testThatTodayCountsOnlyThisBusinessesOrdersPaidTodayAndCarriesNoMoney() throws Exception {
        long before = board.today(tenantId).getOrdersToday();
        Order todays = paid(OrderStatus.PAID);
        todays.setPaidAt(LocalDateTime.now());
        orderRepo.save(todays);
        Order yesterdays = paid(OrderStatus.DELIVERED);
        yesterdays.setPaidAt(LocalDateTime.now().minusDays(2));
        orderRepo.save(yesterdays);
        Tenant other = tenants.other();
        Order theirs = pay.order(other, OrderStatus.PAID, pay.item(other, "Their today plate", 100, true), 1);
        theirs.setPaidAt(LocalDateTime.now());
        orderRepo.save(theirs);

        assertEquals(before + 1, board.today(tenantId).getOrdersToday());
        assertEquals(1, TodayResponse.class.getDeclaredFields().length, "a count and nothing about money");
    }

    @Test
    void testThatTheFloorShowsTheRoomRightNow() {
        RestaurantTable table = seating.newTable(tenantId, 2);
        seating.claim(seating.seatsOf(table).get(0), "Ada");
        paid(OrderStatus.PAID);
        paid(OrderStatus.PREPARING);
        paid(OrderStatus.PREPARING);
        paid(OrderStatus.READY);
        paid(OrderStatus.UNAVAILABLE);
        call("Napkins");
        stock.soldOut(tenantId, plate.getItemId());

        FloorResponse floor = board.floor(tenantId);

        assertEquals(2, floor.getTables().size());
        assertEquals(1, floor.getPaid());
        assertEquals(2, floor.getCooking());
        assertEquals(1, floor.getReady());
        assertEquals(1, floor.getNeedVisit());
        assertEquals(1, floor.getOpenCalls());
        assertEquals(0, floor.getTakenCalls());
        assertEquals(List.of("Service plate"), floor.getSoldOut());
        assertEquals(0, floor.getOpenRefunds());
    }

    // ---- roles over HTTP

    private void signIn(String role) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", tenantId)
                : new WorkerPrincipal(role.equals("CHEF") ? bisi.getWorkerId() : role.equals("WAITER") ? emeka.getWorkerId() : "worker-" + role, role,
                role.equals("PLATFORM_ADMIN") ? null : tenantId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }

    private int call(MockMvc mockMvc, String method, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus();
    }

    @Test
    void testThatEachRouteIsOpenOnlyToItsRoles() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        Order order = paid(OrderStatus.PAID);
        String id = order.getOrderId();

        for (String role : List.of("WAITER", "CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role);
            assertEquals(403, call(mockMvc, "POST", "/api/v1/orders/" + id + "/start"), role);
            assertEquals(403, call(mockMvc, "POST", "/api/v1/orders/" + id + "/ready"), role);
        }
        for (String role : List.of("CHEF", "CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role);
            assertEquals(403, call(mockMvc, "POST", "/api/v1/orders/" + id + "/deliver"), role);
            assertEquals(403, call(mockMvc, "GET", "/api/v1/waiter/ready"), role);
            assertEquals(403, call(mockMvc, "GET", "/api/v1/waiter/tables"), role);
        }
        for (String role : List.of("CHEF", "WAITER", "CUSTOMER")) {
            signIn(role);
            assertEquals(403, call(mockMvc, "GET", "/api/v1/floor"), role);
        }
        for (String role : List.of("WAITER", "CUSTOMER")) {
            signIn(role);
            assertEquals(403, call(mockMvc, "GET", "/api/v1/kitchen/orders"), role + " without a status");
            assertEquals(403, call(mockMvc, "POST", "/api/v1/menu/items/" + plate.getItemId() + "/sold-out"), role);
        }
        signIn("WAITER");
        // The lists the waiter's screen already used keep working.
        assertEquals(200, call(mockMvc, "GET", "/api/v1/kitchen/orders?status=READY"));
        assertEquals(200, call(mockMvc, "GET", "/api/v1/staff/today"));
        signIn("CHEF");
        assertEquals(200, call(mockMvc, "GET", "/api/v1/kitchen/orders"));
        assertEquals(200, call(mockMvc, "GET", "/api/v1/staff/today"));
        assertEquals(200, call(mockMvc, "POST", "/api/v1/orders/" + id + "/start"));
        assertEquals(409, call(mockMvc, "POST", "/api/v1/orders/" + id + "/start"));
        assertEquals(200, call(mockMvc, "POST", "/api/v1/menu/items/" + plate.getItemId() + "/sold-out"));
        signIn("MANAGER");
        assertEquals(200, call(mockMvc, "GET", "/api/v1/floor"));
        assertEquals(200, call(mockMvc, "POST", "/api/v1/menu/items/" + plate.getItemId() + "/back-in-stock"));
        signIn("WAITER");
        assertEquals(200, call(mockMvc, "GET", "/api/v1/waiter/tables"));
        assertEquals(200, call(mockMvc, "GET", "/api/v1/waiter/calls"));
        assertEquals(404, call(mockMvc, "POST", "/api/v1/calls/no-such-call/take"));
    }

    @Test
    void testThatCloseTableOverHttpAsksForConfirmationWithTheCount() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        RestaurantTable table = seating.newTable(tenantId, 2);
        CustomerPrincipal guest = seating.claim(seating.seatsOf(table).get(0), "Ada");
        Order paidOrder = pay.order(tenant, OrderStatus.PAID, plate, 1);
        paidOrder.setDinningSession(sessionRepo.findById(guest.getSessionId()).orElseThrow());
        orderRepo.save(paidOrder);
        signIn("WAITER");

        var refused = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tables/" + table.getTableId() + "/close")).andReturn().getResponse();
        assertEquals(409, refused.getStatus());
        assertTrue(refused.getContentAsString().contains("\"paidUndeliveredOrders\":1"));
        assertTrue(refused.getContentAsString().contains("The food stays with you and nothing is refunded."));
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tables/" + table.getTableId() + "/close").param("confirm", "true"))
                .andReturn().getResponse().getStatus());
    }

    @Autowired
    private com.scantoorder.scantoorder.config.JwtFilter jwtFilter;
    @Autowired
    private com.scantoorder.scantoorder.service.Interface.SeatService seatService;

    private Object endedMessage(String token) throws Exception {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", token));
        jwtFilter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), new org.springframework.mock.web.MockFilterChain());
        SecurityContextHolder.clearContext();
        return request.getAttribute(com.scantoorder.scantoorder.config.JwtFilter.SESSION_ENDED_ATTRIBUTE);
    }

    @Test
    void testThatAGuestAtATableStaffClosedSeesTheirVisitEndedAndOtherEndingsKeepTheOldWords() throws Exception {
        RestaurantTable table = seating.newTable(tenantId, 2);
        List<Seat> seats = seating.seatsOf(table);
        SeatClaimedResponse closedByStaff = seatService.claimSeat(seating.claimRequest(seats.get(0), "Closed"), null);
        SeatClaimedResponse leaver = seatService.claimSeat(seating.claimRequest(seats.get(1), "Leaves"), null);
        CustomerPrincipal leaving = seating.principalFor(leaver.getSeatId());
        diningSessionService.leave(leaving);

        assertEquals("Your session has ended", endedMessage(leaver.getToken()));

        diningSessionService.closeTable(tenantId, table.getTableId(), true);

        assertEquals("Your visit has ended.", endedMessage(closedByStaff.getToken()));
    }
}
