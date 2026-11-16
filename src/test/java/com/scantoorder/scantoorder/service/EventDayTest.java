package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.DataSeeder;
import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.*;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.EventService;
import com.scantoorder.scantoorder.service.Interface.OrderRecoveryService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.scantoorder.scantoorder.utils.TimeText;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class EventDayTest {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Autowired
    private EventTestSupport support;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private EventGuestService guests;
    @Autowired
    private EventPlateService plates;
    @Autowired
    private EventTableService tables;
    @Autowired
    private EventSeatingService seating;
    @Autowired
    private EventOrderService orders;
    @Autowired
    private EventCoverageService coverage;
    @Autowired
    private EventService events;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private OrderRecoveryService recovery;
    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private EventPlateRepo plateRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private DinningSessionRepo sessionRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private DataSeeder seeder;
    @Autowired
    private com.scantoorder.scantoorder.service.Interface.RestaurantTableService restaurantTables;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("springSecurityFilterChain")
    private jakarta.servlet.Filter securityFilters;
    @Autowired
    private WebApplicationContext wac;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant planner;
    private String tenantId;
    private Event event;
    private String slug;

    @BeforeEach
    void setUp() {
        clearInvocations(paystackClient);
        planner = support.planner();
        tenantId = planner.getTenantId();
        slug = planner.getSlug();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // A running event with an assigned table of 4 and one plate.
    private void assignedEvent(int paid, Integer plates) {
        event = support.activeEvent(planner, EventSeatingMode.ASSIGNED, plates, paid);
    }

    private RestaurantTable tableRow(EventTableResponse table) {
        return tableRepo.findById(table.getTableId()).orElseThrow();
    }

    private EventGuestRequest named(String name) {
        EventGuestRequest request = new EventGuestRequest();
        request.setName(name);
        return request;
    }

    private SeatClaimedResponse sit(EventGuestResponse guest, EventTableResponse table, CustomerPrincipal phone) {
        return seating.redeem(slug, event.getEventCode(), table.getDisplayNumber(), guest.getCode(), phone);
    }

    private CustomerPrincipal phoneOf(EventGuestResponse guest) {
        SessionSeat membership = guestRepo.findById(guest.getId()).orElseThrow().getSessionSeat();
        return new CustomerPrincipal(membership.getSession().getSessionId(), membership.getSeat().getSeatId(),
                membership.getSeat().getTableId().getTableId(), membership.getId(), tenantId);
    }

    private OrderStatus statusOf(String orderId) {
        return orderRepo.findOrderByOrderId(orderId).getOrderStatus();
    }

    private void setOrder(String orderId, OrderStatus status) {
        Order order = orderRepo.findOrderByOrderId(orderId);
        order.setOrderStatus(status);
        orderRepo.save(order);
    }

    // ---- the guest list

    @Test
    void testThatTheListCannotBeLongerThanThePaidNumberAndSaysSo() {
        assignedEvent(2, 2);
        support.table(planner, event, 4);
        support.guest(planner, event, "One");
        support.guest(planner, event, "Two");

        ConflictException refused = assertThrows(ConflictException.class, () -> guests.add(tenantId, event.getId(), named("Three")));

        assertEquals("You have paid for 2 guests and the list is full. Pay for more to add Three.", refused.getMessage());
        assertEquals(2, guestRepo.countByEvent_Id(event.getId()));
    }

    @Test
    void testThatCodesAreSixCharactersFromTheClearAlphabetAndUniqueEvenForTwoGuestsWithTheSameName() {
        assignedEvent(60, null);
        support.table(planner, event, 20);
        support.table(planner, event, 20);
        support.table(planner, event, 20);
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            String code = support.guest(planner, event, i < 2 ? "Same Name" : "Guest " + i).getCode();
            assertEquals(6, code.length());
            assertTrue(code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0), code);
            assertTrue(codes.add(code), "duplicate " + code);
        }
    }

    @Test
    void testThatAssignedSeatsAreGivenInTableOrderOrChosenByThePlanner() {
        assignedEvent(10, 1);
        EventTableResponse first = support.table(planner, event, 2);
        EventTableResponse second = support.table(planner, event, 2);

        EventGuestResponse a = support.guest(planner, event, "A");
        EventGuestResponse b = support.guest(planner, event, "B");
        EventGuestResponse c = support.guest(planner, event, "C");

        assertEquals(first.getDisplayNumber(), a.getTableNumber());
        assertEquals(1, a.getSeatPosition());
        assertEquals(2, b.getSeatPosition());
        assertEquals(second.getDisplayNumber(), c.getTableNumber());
        EventGuestRequest chosen = named("D");
        chosen.setTableId(second.getTableId());
        EventGuestResponse d = guests.add(tenantId, event.getId(), chosen);
        assertEquals(second.getDisplayNumber(), d.getTableNumber());
        assertEquals(2, d.getSeatPosition());
        EventGuestRequest taken = named("E");
        taken.setTableId(second.getTableId());
        assertThrows(ConflictException.class, () -> guests.add(tenantId, event.getId(), taken));
        assertThrows(ConflictException.class, () -> guests.add(tenantId, event.getId(), named("F")));
    }

    @Test
    void testThatFreeSeatingGivesNoSeatAndRefusesAChosenOne() {
        event = support.activeEvent(planner, EventSeatingMode.FREE, 1, 3);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse guest = support.guest(planner, event, "Free");
        assertNull(guest.getTableNumber());
        EventGuestRequest chosen = named("Picky");
        chosen.setTableId(table.getTableId());
        assertThrows(InvalidRequestException.class, () -> guests.add(tenantId, event.getId(), chosen));
    }

    @Test
    void testThatAGuestWhoHasNotArrivedCanBeRemovedAndFreesAPlaceButASeatedOneCannot() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 4);
        EventGuestResponse stay = support.guest(planner, event, "Stay");
        EventGuestResponse go = support.guest(planner, event, "Go");
        sit(stay, table, null);

        guests.remove(tenantId, event.getId(), go.getId());
        EventGuestResponse replacement = support.guest(planner, event, "Replacement");
        assertNotNull(replacement.getCode());
        ConflictException refused = assertThrows(ConflictException.class, () -> guests.remove(tenantId, event.getId(), stay.getId()));
        assertTrue(refused.getMessage().contains("already arrived"));
        assertThrows(ResourceNotFoundException.class, () -> guests.remove(tenantId, event.getId(), go.getId()));
    }

    @Test
    void testThatALimitCanBeRaisedOrMadeUnlimitedButNeverLoweredBelowWhatWasUsed() {
        assignedEvent(2, 2);
        support.table(planner, event, 4);
        EventGuestResponse guest = support.guest(planner, event, "Hungry");
        EventGuestLimitRequest raise = new EventGuestLimitRequest();
        raise.setLimit(5);

        assertEquals(5, guests.raiseLimit(tenantId, event.getId(), guest.getId(), raise).getPlateLimit());
        raise.setLimit(3);
        assertThrows(ConflictException.class, () -> guests.raiseLimit(tenantId, event.getId(), guest.getId(), raise));
        EventGuest row = guestRepo.findById(guest.getId()).orElseThrow();
        row.setPlatesUsed(4);
        guestRepo.save(row);
        raise.setLimit(2);
        ConflictException below = assertThrows(ConflictException.class, () -> guests.raiseLimit(tenantId, event.getId(), guest.getId(), raise));
        assertTrue(below.getMessage().contains("can't go below"));
        EventGuestLimitRequest unlimited = new EventGuestLimitRequest();
        unlimited.setUnlimited(true);
        assertNull(guests.raiseLimit(tenantId, event.getId(), guest.getId(), unlimited).getPlateLimit());
        assertThrows(ConflictException.class, () -> guests.raiseLimit(tenantId, event.getId(), guest.getId(), raise));
        assertThrows(InvalidRequestException.class, () -> guests.raiseLimit(tenantId, event.getId(), guest.getId(), new EventGuestLimitRequest()));
    }

    @Test
    void testThatTheCsvListsCodesAndMakesFormulasHarmless() {
        assignedEvent(2, 2);
        support.table(planner, event, 4);
        support.guest(planner, event, "=SUM(1,1)");
        EventGuestResponse plain = support.guest(planner, event, "Ada, the bride");

        String csv = new String(guests.exportCsv(tenantId, event.getId()), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("Name,Phone,Email,Code,Table,Seat,Plates allowed,Plates used\n"));
        assertTrue(csv.contains(plain.getCode()));
        assertTrue(csv.contains("\"Ada, the bride\""));
        assertTrue(csv.contains("\"'=SUM(1,1)\""));
        assertEquals(3, csv.split("\n").length);
    }

    @Test
    void testThatAnotherPlannersEventIsNotFoundForPlatesTablesAndGuests() {
        assignedEvent(2, 1);
        Tenant other = support.otherPlanner();
        String mine = event.getId();

        assertThrows(ResourceNotFoundException.class, () -> guests.add(other.getTenantId(), mine, named("X")));
        assertThrows(ResourceNotFoundException.class, () -> guests.list(other.getTenantId(), mine));
        assertThrows(ResourceNotFoundException.class, () -> guests.exportCsv(other.getTenantId(), mine));
        assertThrows(ResourceNotFoundException.class, () -> plates.list(other.getTenantId(), mine));
        assertThrows(ResourceNotFoundException.class, () -> tables.list(other.getTenantId(), mine));
        assertThrows(ResourceNotFoundException.class, () -> coverage.coverage(other.getTenantId(), mine));
        assertThrows(ResourceNotFoundException.class, () -> seating.release(other.getTenantId(), mine, "x"));
    }

    @Test
    void testThatAPlateThatWasOrderedCanBeSwitchedOffButNotDeleted() {
        assignedEvent(1, 3);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Jollof rice, chicken and Coke");
        EventPlateResponse spare = support.plate(planner, event, "Spare plate");
        EventGuestResponse guest = support.guest(planner, event, "Orders");
        sit(guest, table, null);
        orders.order(phoneOf(guest), plate.getId());

        ConflictException refused = assertThrows(ConflictException.class, () -> plates.delete(tenantId, event.getId(), plate.getId()));
        assertEquals("It's been ordered before, so it can only be switched off.", refused.getMessage());
        EventPlateRequest off = new EventPlateRequest();
        off.setName(plate.getName());
        off.setAvailable(false);
        assertFalse(plates.update(tenantId, event.getId(), plate.getId(), off).isAvailable());
        plates.delete(tenantId, event.getId(), spare.getId());
        assertEquals(1, plates.list(tenantId, event.getId()).size());
    }

    // ---- the code at the table

    @Test
    void testThatTheRightCodeAtTheRightTableSeatsTheGuestAndSecondPhoneIsToldWhereItIsInUse() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 4);
        EventGuestResponse guest = support.guest(planner, event, "Funke");

        SeatClaimedResponse seated = sit(guest, table, null);

        assertNotNull(seated.getToken());
        assertEquals(table.getDisplayNumber(), seated.getDisplayNumber());
        assertEquals(1, seated.getPosition());
        EventGuest row = guestRepo.findById(guest.getId()).orElseThrow();
        assertEquals(SessionSeatStatus.OCCUPIED, row.getSessionSeat().getStatus());
        assertNotNull(row.getSeatedAt());
        SeatTakenException inUse = assertThrows(SeatTakenException.class, () -> sit(guest, table, null));
        assertEquals("It is in use at Table " + table.getDisplayNumber() + ", Seat 1 on another phone. Find an usher and they will move it to this one.", inUse.getMessage());
        assertEquals(row.getSessionSeat().getId(), guestRepo.findById(guest.getId()).orElseThrow().getSessionSeat().getId());
        // The same phone coming back keeps its seat and gets no second token.
        SeatClaimedResponse again = sit(guest, table, phoneOf(guest));
        assertNull(again.getToken());
        assertEquals("You are already seated", again.getMessage());
    }

    @Test
    void testThatAnUnknownCodeOrAnotherEventsCodeIsAnsweredAlikeAndGuestsMayKeepTrying() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 4);
        EventGuestResponse mine = support.guest(planner, event, "Mine");
        Event other = support.activeEvent(planner, EventSeatingMode.FREE, 1, 2);
        EventGuestResponse theirs = support.guest(planner, other, "Theirs");

        for (int attempt = 0; attempt < 12; attempt++) {
            ResourceNotFoundException unknown = assertThrows(ResourceNotFoundException.class,
                    () -> seating.redeem(slug, event.getEventCode(), table.getDisplayNumber(), "ZZZZZ" + (char) ('2' + 0), null));
            assertEquals("We can't find that code. Check the letters, or ask the host.", unknown.getMessage());
        }
        ResourceNotFoundException foreign = assertThrows(ResourceNotFoundException.class,
                () -> seating.redeem(slug, event.getEventCode(), table.getDisplayNumber(), theirs.getCode(), null));
        assertEquals(EventSeatingService.UNKNOWN_CODE, foreign.getMessage());
        // Lower case and spaces are forgiven, and the real code still works after all those misses.
        assertNotNull(seating.redeem(slug, event.getEventCode(), table.getDisplayNumber(), " " + mine.getCode().substring(0, 3).toLowerCase() + " " + mine.getCode().substring(3).toLowerCase() + " ", null).getToken());
    }

    @Test
    void testThatTheDoorsAreShutBeforeStartAndAfterTheEnd() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 4);
        EventGuestResponse guest = support.guest(planner, event, "Early");
        Event row = eventRepo.findById(event.getId()).orElseThrow();
        row.setStartsAt(LocalDateTime.now().plusHours(2).withSecond(0).withNano(0));
        row.setEndsAt(LocalDateTime.now().plusHours(6));
        eventRepo.save(row);

        ConflictException early = assertThrows(ConflictException.class, () -> sit(guest, table, null));
        assertEquals("Doors open at " + TimeText.clock(row.getStartsAt()) + ". Come back then and your code will work.", early.getMessage());

        row.setStartsAt(LocalDateTime.now().minusHours(3));
        row.setEndsAt(LocalDateTime.now().minusMinutes(1));
        eventRepo.save(row);
        assertEquals("This event is over. Thank you for coming.", assertThrows(ConflictException.class, () -> sit(guest, table, null)).getMessage());

        row.setEndsAt(LocalDateTime.now().plusHours(1));
        row.setStatus(EventStatus.ENDED);
        eventRepo.save(row);
        assertEquals(EventSeatingService.EVENT_OVER, assertThrows(ConflictException.class, () -> sit(guest, table, null)).getMessage());
        assertNull(guestRepo.findById(guest.getId()).orElseThrow().getSessionSeat());
    }

    @Test
    void testThatAnAssignedGuestAtTheWrongTableIsToldWhereTheirSeatIsAndIsNotSeated() {
        assignedEvent(2, 1);
        EventTableResponse first = support.table(planner, event, 1);
        EventTableResponse second = support.table(planner, event, 1);
        support.guest(planner, event, "Takes first");
        EventGuestResponse wrongTable = support.guest(planner, event, "Takes second");

        SeatStatusException refused = assertThrows(SeatStatusException.class, () -> sit(wrongTable, first, null));

        assertEquals("Your seat is at Table " + second.getDisplayNumber() + ", Seat 1.", refused.getMessage());
        assertNull(guestRepo.findById(wrongTable.getId()).orElseThrow().getSessionSeat());
        assertNotNull(sit(wrongTable, second, null).getToken());
    }

    @Test
    void testThatFreeSeatingFillsATableSeatBySeatAndThenSaysItIsFull() {
        event = support.activeEvent(planner, EventSeatingMode.FREE, 1, 3);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse a = support.guest(planner, event, "A");
        EventGuestResponse b = support.guest(planner, event, "B");
        EventGuestResponse c = support.guest(planner, event, "C");

        assertEquals(1, sit(a, table, null).getPosition());
        assertEquals(2, sit(b, table, null).getPosition());
        SeatTakenException full = assertThrows(SeatTakenException.class, () -> sit(c, table, null));
        assertEquals("This table is full. Try another table.", full.getMessage());
        EventTableResponse next = support.table(planner, event, 2);
        assertNotNull(sit(c, next, null).getToken());
    }

    @Test
    void testThatATableThatIsNotAnEventTableOrIsUnknownIsNotAvailable() {
        assignedEvent(1, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse guest = support.guest(planner, event, "Lost");
        String eventCode = event.getEventCode();
        assertThrows(TableNotFoundException.class, () -> seating.redeem(slug, eventCode, 999, guest.getCode(), null));
        assertThrows(TableNotFoundException.class, () -> seating.redeem("nosuchplace", eventCode, table.getDisplayNumber(), guest.getCode(), null));
        assertThrows(TableNotFoundException.class, () -> seating.redeem(TenantTestSupport.MAIN_SLUG, eventCode, table.getDisplayNumber(), guest.getCode(), null));
        // An event code that does not exist at this planner, or belongs to another planner's event, finds no table.
        assertThrows(TableNotFoundException.class, () -> seating.redeem(slug, "AAAAAA", table.getDisplayNumber(), guest.getCode(), null));
        Event theirs = support.activeEvent(support.otherPlanner(), EventSeatingMode.FREE, 1, 1);
        assertThrows(TableNotFoundException.class, () -> seating.redeem(slug, theirs.getEventCode(), table.getDisplayNumber(), guest.getCode(), null));
    }

    @Test
    void testThatAnUsherFreesTheSeatSoTheCodeWorksOnANewPhoneAndPlatesUsedAreKept() {
        assignedEvent(2, 3);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Plate one");
        EventGuestResponse guest = support.guest(planner, event, "Moves");
        sit(guest, table, null);
        orders.order(phoneOf(guest), plate.getId());

        ReleaseSeatResponse released = seating.release(tenantId, event.getId(), guest.getId());
        assertEquals("VACANT", released.getStatus());
        assertEquals(1, guestRepo.findById(guest.getId()).orElseThrow().getPlatesUsed());
        assertNotNull(sit(guest, table, null).getToken());
        assertEquals(1, guestRepo.findById(guest.getId()).orElseThrow().getPlatesUsed());
        // A guest who is not seated has nothing to free.
        EventGuestResponse never = support.guest(planner, event, "Never came");
        assertTrue(seating.release(tenantId, event.getId(), never.getId()).getMessage().contains("not seated"));
    }

    @Test
    void testThatOnlyAWaiterOrTheManagerCanRelease() throws Exception {
        assignedEvent(1, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse guest = support.guest(planner, event, "Held");
        sit(guest, table, null);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        String path = "/api/v1/events/" + event.getId() + "/guests/" + guest.getId() + "/release";

        for (String role : List.of("CUSTOMER", "CHEF", "PLATFORM_ADMIN")) {
            signIn(role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.post(path)).andReturn().getResponse().getStatus(), role);
        }
        assertEquals(SessionSeatStatus.OCCUPIED, guestRepo.findById(guest.getId()).orElseThrow().getSessionSeat().getStatus());
        signIn("WAITER");
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.post(path)).andReturn().getResponse().getStatus());
        assertEquals(SessionSeatStatus.RELEASED, guestRepo.findById(guest.getId()).orElseThrow().getSessionSeat().getStatus());
    }

    // ---- ordering

    private record Seated(EventGuestResponse guest, CustomerPrincipal phone) {
    }

    private Seated seatedGuest(EventTableResponse table, String name) {
        EventGuestResponse guest = support.guest(planner, event, name);
        sit(guest, table, null);
        return new Seated(guest, phoneOf(guest));
    }

    @Test
    void testThatAPlateGoesStraightToTheKitchenAsPaidWithNoPayment() {
        assignedEvent(1, 2);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Jollof rice, chicken and Coke");
        Seated seated = seatedGuest(table, "Orders");

        EventOrderResponse placed = orders.order(seated.phone(), plate.getId());

        assertEquals("PAID", placed.getStatus());
        assertEquals(1, placed.getPlatesUsed());
        assertTrue(placed.getMessage().startsWith("Nothing to pay."));
        Order order = orderRepo.findOrderByOrderId(placed.getOrderId());
        assertEquals(0, order.getTotalAmount().signum());
        assertEquals(event.getId(), order.getEvent().getId());
        assertTrue(paymentRepo.findByOrderOrderId(order.getOrderId()).isEmpty());
        assertEquals("Jollof rice, chicken and Coke", ordersOnKitchenList().get(0).getItems().get(0).getItemName());
        verifyNoInteractions(paystackClient);
    }

    @Autowired
    private com.scantoorder.scantoorder.service.Interface.OrderService orderService;

    private List<KitchenOrderResponse> ordersOnKitchenList() {
        return orderService.getKitchenOrders(tenantId, null);
    }

    @Test
    void testThatOnlyOnePlateComesAtATimeUntilItIsDelivered() {
        assignedEvent(1, 3);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Fried rice, turkey and Zobo");
        Seated seated = seatedGuest(table, "Patient");
        EventOrderResponse first = orders.order(seated.phone(), plate.getId());

        for (OrderStatus onItsWay : List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY)) {
            setOrder(first.getOrderId(), onItsWay);
            ConflictException refused = assertThrows(ConflictException.class, () -> orders.order(seated.phone(), plate.getId()));
            assertEquals("One plate at a time. Your Fried rice, turkey and Zobo is on its way. Order again once it arrives.", refused.getMessage());
        }
        assertEquals(1, guestRepo.findById(seated.guest().getId()).orElseThrow().getPlatesUsed());
        setOrder(first.getOrderId(), OrderStatus.DELIVERED);
        assertEquals(2, orders.order(seated.phone(), plate.getId()).getPlatesUsed());
    }

    @Test
    void testThatTheLimitStopsFurtherPlatesAndARaisedOrUnlimitedLimitLetsThemOrder() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Only plate");
        Seated limited = seatedGuest(table, "Limited");
        Seated unlimited = seatedGuest(table, "Unlimited");
        EventGuestLimitRequest unlimitedRequest = new EventGuestLimitRequest();
        unlimitedRequest.setUnlimited(true);
        guests.raiseLimit(tenantId, event.getId(), unlimited.guest().getId(), unlimitedRequest);

        EventOrderResponse done = orders.order(limited.phone(), plate.getId());
        setOrder(done.getOrderId(), OrderStatus.DELIVERED);
        ConflictException stopped = assertThrows(ConflictException.class, () -> orders.order(limited.phone(), plate.getId()));
        assertEquals("You've had all 1 of your plates. The host can add more for you.", stopped.getMessage());

        EventGuestLimitRequest raise = new EventGuestLimitRequest();
        raise.setLimit(2);
        guests.raiseLimit(tenantId, event.getId(), limited.guest().getId(), raise);
        assertEquals(2, orders.order(limited.phone(), plate.getId()).getPlatesUsed());

        for (int i = 0; i < 4; i++) {
            EventOrderResponse each = orders.order(unlimited.phone(), plate.getId());
            setOrder(each.getOrderId(), OrderStatus.DELIVERED);
        }
        assertEquals(4, guestRepo.findById(unlimited.guest().getId()).orElseThrow().getPlatesUsed());
    }

    @Test
    void testThatAnUnavailablePlateDoesNotCountAndASwapOrAnotherOrderCoversIt() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Runs out");
        EventPlateResponse backup = support.plate(planner, event, "Backup plate");
        Seated one = seatedGuest(table, "Swapped");
        Seated two = seatedGuest(table, "Reorders");
        EventOrderResponse oneOrder = orders.order(one.phone(), plate.getId());
        EventOrderResponse twoOrder = orders.order(two.phone(), plate.getId());

        recovery.markUnavailable(tenantId, oneOrder.getOrderId(), UnavailableReason.RAN_OUT);
        recovery.markUnavailable(tenantId, twoOrder.getOrderId(), UnavailableReason.EQUIPMENT_DOWN);

        assertEquals(0, guestRepo.findById(one.guest().getId()).orElseThrow().getPlatesUsed());
        assertFalse(plateRepo.findById(plate.getId()).orElseThrow().isAvailable());
        // A swap puts it back with a new plate and the plate counts again.
        SwapRequest swap = new SwapRequest();
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName("backup PLATE");
        line.setQuantity(1);
        swap.getItems().add(line);
        assertEquals("PAID", recovery.swap(tenantId, oneOrder.getOrderId(), swap).getStatus());
        assertEquals(1, guestRepo.findById(one.guest().getId()).orElseThrow().getPlatesUsed());
        assertEquals("Backup plate", ordersOnKitchenList().stream().filter(o -> o.getOrderId().equals(oneOrder.getOrderId()))
                .findFirst().orElseThrow().getItems().get(0).getItemName());
        // Ordering again is allowed while the stuck order waits, and the old ticket is closed.
        EventOrderResponse again = orders.order(two.phone(), backup.getId());
        assertEquals(OrderStatus.CANCELED, statusOf(twoOrder.getOrderId()));
        assertEquals(1, again.getPlatesUsed());
        // A plate that has run out cannot be swapped in.
        SwapRequest gone = new SwapRequest();
        CreateOrderItemRequest bad = new CreateOrderItemRequest();
        bad.setItemName("Runs out");
        bad.setQuantity(1);
        gone.getItems().add(bad);
        recovery.markUnavailable(tenantId, oneOrder.getOrderId(), UnavailableReason.SOMETHING_WRONG);
        assertThrows(ConflictException.class, () -> recovery.swap(tenantId, oneOrder.getOrderId(), gone));
    }

    @Test
    void testThatARefundCannotBeAskedForOnAnEventOrderAndAnOutPlateCannotBeOrdered() {
        assignedEvent(1, 2);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Refund me");
        EventPlateResponse off = support.plate(planner, event, "Switched off");
        EventPlateRequest switchOff = new EventPlateRequest();
        switchOff.setName(off.getName());
        switchOff.setAvailable(false);
        plates.update(tenantId, event.getId(), off.getId(), switchOff);
        Seated seated = seatedGuest(table, "Hopeful");

        assertEquals("Switched off has run out. Choose another plate.", assertThrows(ConflictException.class, () -> orders.order(seated.phone(), off.getId())).getMessage());
        EventOrderResponse placed = orders.order(seated.phone(), plate.getId());
        recovery.markUnavailable(tenantId, placed.getOrderId(), UnavailableReason.EQUIPMENT_DOWN);

        assertThrows(OrderStatusException.class, () -> recovery.requestRefund(tenantId, placed.getOrderId()));
        assertEquals(OrderStatus.UNAVAILABLE, statusOf(placed.getOrderId()));
        assertTrue(paymentRepo.findByOrderOrderId(placed.getOrderId()).isEmpty());
    }

    @Test
    void testThatAGuestOfAnEndedEventCannotOrderAndOnlyASeatedGuestCan() {
        assignedEvent(1, 2);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Late plate");
        Seated seated = seatedGuest(table, "Late");
        assertThrows(SessionEndedException.class, () -> orders.order(null, plate.getId()));
        assertThrows(ResourceNotFoundException.class, () -> orders.order(seated.phone(), "no-such-plate"));

        events.end(tenantId, event.getId());
        assertThrows(RuntimeException.class, () -> orders.order(seated.phone(), plate.getId()));
        assertEquals(0, guestRepo.findById(seated.guest().getId()).orElseThrow().getPlatesUsed());
    }

    // ---- coverage and ending

    @Test
    void testThatCoverageSortsEveryGuestIntoOneStateAndTheCountsAddUp() {
        assignedEvent(5, 2);
        EventTableResponse table = support.table(planner, event, 8);
        EventPlateResponse plate = support.plate(planner, event, "Coverage plate");
        support.guest(planner, event, "Not here");
        Seated seatedOnly = seatedGuest(table, "Seated only");
        Seated waiting = seatedGuest(table, "Waiting");
        Seated eaten = seatedGuest(table, "Eaten");
        Seated stuck = seatedGuest(table, "Stuck");
        orders.order(waiting.phone(), plate.getId());
        setOrder(orders.order(eaten.phone(), plate.getId()).getOrderId(), OrderStatus.DELIVERED);
        recovery.markUnavailable(tenantId, orders.order(stuck.phone(), plate.getId()).getOrderId(), UnavailableReason.SOMETHING_WRONG);

        EventCoverageResponse report = coverage.coverage(tenantId, event.getId());

        assertEquals(5, report.getOnList());
        assertEquals(1, report.getNotArrived());
        assertEquals(1, report.getSeatedNotOrdered());
        assertEquals(2, report.getWaitingForPlate());
        assertEquals(1, report.getEaten());
        assertEquals(report.getOnList(), report.getNotArrived() + report.getSeatedNotOrdered() + report.getWaitingForPlate() + report.getEaten());
        EventCoverageResponse.Guest row = report.getGuests().stream().filter(g -> g.getName().equals("Eaten")).findFirst().orElseThrow();
        assertEquals("EATEN", row.getState());
        assertEquals(table.getDisplayNumber(), row.getTableNumber());
        assertEquals(1, row.getPlatesUsed());
        assertEquals(2, row.getPlateLimit());
        assertNotNull(seatedOnly);
    }

    @Test
    void testThatTheSchedulerEndsEventsPastTheirEndAndClosesTheirSessionsButNotOthers() {
        assignedEvent(2, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse guest = support.guest(planner, event, "Still here");
        sit(guest, table, null);
        Event running = support.activeEvent(planner, EventSeatingMode.FREE, 1, 1);
        Event row = eventRepo.findById(event.getId()).orElseThrow();
        row.setStartsAt(LocalDateTime.now().minusHours(6));
        row.setEndsAt(LocalDateTime.now().minusMinutes(5));
        eventRepo.save(row);

        int ended = events.endFinishedEvents();

        assertTrue(ended >= 1);
        assertEquals(EventStatus.ENDED, eventRepo.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(EventStatus.ACTIVE, eventRepo.findById(running.getId()).orElseThrow().getStatus());
        SessionSeat membership = guestRepo.findById(guest.getId()).orElseThrow().getSessionSeat();
        assertEquals(SessionSeatStatus.RELEASED, membership.getStatus());
        assertEquals(DinningSessionStatus.CLOSED, sessionRepo.findById(membership.getSession().getSessionId()).orElseThrow().getSessionStatus());
        assertEquals(0, events.endFinishedEvents());
    }

    @Test
    void testThatThePlannerEndingAnEventEarlyStopsCodesAndEventTablesAreHiddenFromRestaurantLists() {
        assignedEvent(1, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse guest = support.guest(planner, event, "Early end");
        assertEquals(0, tableRepo.countActive(tenantId));

        assertEquals("ENDED", events.end(tenantId, event.getId()).getStatus());

        assertThrows(ConflictException.class, () -> sit(guest, table, null));
        assertThrows(ConflictException.class, () -> guests.add(tenantId, event.getId(), named("After")));
    }

    @Test
    void testThatAnEventGuestSeesTheirPlateByNameInTheTablesOrders() {
        assignedEvent(1, 2);
        EventTableResponse table = support.table(planner, event, 2);
        EventPlateResponse plate = support.plate(planner, event, "Jollof rice, chicken and Coke");
        Seated seated = seatedGuest(table, "Funke");
        orders.order(seated.phone(), plate.getId());

        SessionOrdersResponse mineSeen = diningSessionService.getSessionOrders(seated.phone());

        assertEquals(1, mineSeen.getOrders().size());
        assertEquals(List.of("Jollof rice, chicken and Coke"), mineSeen.getOrders().get(0).getItems());
        assertEquals("PAID", mineSeen.getOrders().get(0).getStatus());
    }

    // ---- numbering and links

    @Test
    void testThatEachEventsTablesAreNumberedFromOneAndTheEventCodeNamesTheEvent() {
        Event first = support.activeEvent(planner, EventSeatingMode.ASSIGNED, 1, 4);
        Event second = support.activeEvent(planner, EventSeatingMode.ASSIGNED, 1, 4);

        EventTableResponse a1 = support.table(planner, first, 2);
        EventTableResponse a2 = support.table(planner, first, 2);
        EventTableResponse b1 = support.table(planner, second, 2);

        assertEquals(1, a1.getDisplayNumber());
        assertEquals(2, a2.getDisplayNumber());
        assertEquals(1, b1.getDisplayNumber());
        CreateTableRequest explicit = new CreateTableRequest(2, 1);
        assertThrows(ConflictException.class, () -> tables.create(tenantId, first.getId(), explicit));
        assertEquals(3, tables.create(tenantId, first.getId(), new CreateTableRequest(2, 3)).getDisplayNumber());
        // Table 1 of the second event is reached through the second event's code, and only through it.
        EventGuestResponse guest = support.guest(planner, second, "Second event guest");
        assertNotNull(seating.redeem(slug, second.getEventCode(), 1, guest.getCode(), null).getToken());
        EventGuestResponse stranger = support.guest(planner, first, "First event guest");
        assertThrows(ResourceNotFoundException.class, () -> seating.redeem(slug, second.getEventCode(), 1, stranger.getCode(), null));
        assertEquals(0, tableRepo.countActive(tenantId));
    }

    @Test
    void testThatEventCodesAreSixCharactersFromTheClearAlphabetAndUniqueWithinThePlanner() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String code = support.draft(planner, EventSeatingMode.FREE, null).getEventCode();
            assertEquals(6, code.length());
            assertTrue(code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0), code);
            assertTrue(seen.add(code), "duplicate " + code);
        }
        EventResponse edited = events.update(tenantId, support.draft(planner, EventSeatingMode.FREE, null).getId(), support.request(EventSeatingMode.FREE, 2));
        assertEquals(6, edited.getEventCode().length());
    }

    @Test
    void testThatAnEventTablesLinkNamesTheEventAndARestaurantTablesLinkIsUnchanged() {
        assignedEvent(1, 1);
        EventTableResponse eventTable = support.table(planner, event, 2);
        Tenant restaurant = tenants.main();
        RestaurantTable plain = new RestaurantTable();
        plain.setTenant(restaurant);
        plain.setCapacity(2);
        plain.setDisplayNumber(41);
        plain = tableRepo.save(plain);

        String eventLink = restaurantTables.scanLink(tenantId, eventTable.getTableNumber());
        String restaurantLink = restaurantTables.scanLink(restaurant.getTenantId(), plain.getTableNumber());

        assertTrue(eventLink.endsWith("/t/" + slug + "/e/" + event.getEventCode() + "/table/" + eventTable.getDisplayNumber()), eventLink);
        assertTrue(restaurantLink.endsWith("/t/" + restaurant.getSlug() + "/table/41"), restaurantLink);
        assertFalse(restaurantLink.contains("/e/"));
        assertThrows(ResourceNotFoundException.class, () -> restaurantTables.scanLink(restaurant.getTenantId(), eventTable.getTableNumber()));
    }

    @Test
    void testThatTheCodeEndpointIsPublicOnTheNewPathAndTheOldPathIsGone() throws Exception {
        assignedEvent(1, 1);
        EventTableResponse table = support.table(planner, event, 2);
        EventGuestResponse guest = support.guest(planner, event, "Http guest");
        MockMvc open = MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilters).build();
        String body = "{\"code\":\"" + guest.getCode() + "\"}";

        int viaNewPath = open.perform(MockMvcRequestBuilders.post("/api/v1/t/{slug}/e/{code}/tables/{n}/code", slug, event.getEventCode(), table.getDisplayNumber())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        int wrongEvent = open.perform(MockMvcRequestBuilders.post("/api/v1/t/{slug}/e/{code}/tables/{n}/code", slug, "AAAAAA", table.getDisplayNumber())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        int oldPath = open.perform(MockMvcRequestBuilders.post("/api/v1/t/{slug}/tables/{n}/code", slug, table.getDisplayNumber())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();

        assertEquals(201, viaNewPath);
        assertEquals(404, wrongEvent);
        assertTrue(oldPath == 401 || oldPath == 403 || oldPath == 404 || oldPath == 405, "old path answered " + oldPath);
    }

    @Test
    void testThatTheTablePreviewNamesTheEventAndSaysWhetherCodesWorkYet() throws Exception {
        assignedEvent(1, 1);
        EventTableResponse table = support.table(planner, event, 2);
        MockMvc open = MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilters).build();
        String path = "/api/v1/t/" + slug + "/e/" + event.getEventCode() + "/tables/" + table.getDisplayNumber();

        var running = seating.preview(slug, event.getEventCode(), table.getDisplayNumber());
        assertEquals("Test Wedding", running.getEventName());
        assertEquals("The Hall", running.getVenue());
        assertTrue(running.isOpen());
        assertFalse(running.isOver());
        assertEquals(200, open.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse().getStatus());

        Event row = eventRepo.findById(event.getId()).orElseThrow();
        row.setStartsAt(LocalDateTime.now().plusHours(2));
        row.setEndsAt(LocalDateTime.now().plusHours(6));
        eventRepo.save(row);
        var early = seating.preview(slug, event.getEventCode(), table.getDisplayNumber());
        assertFalse(early.isOpen());
        assertFalse(early.isOver());

        row.setStartsAt(LocalDateTime.now().minusHours(6));
        row.setEndsAt(LocalDateTime.now().minusHours(1));
        eventRepo.save(row);
        assertTrue(seating.preview(slug, event.getEventCode(), table.getDisplayNumber()).isOver());

        assertThrows(TableNotFoundException.class, () -> seating.preview(slug, "AAAAAA", table.getDisplayNumber()));
        assertThrows(TableNotFoundException.class, () -> seating.preview(slug, event.getEventCode(), 999));
        assertThrows(TableNotFoundException.class, () -> seating.preview(TenantTestSupport.MAIN_SLUG, event.getEventCode(), table.getDisplayNumber()));
        assertEquals(404, open.perform(MockMvcRequestBuilders.get("/api/v1/t/" + slug + "/e/AAAAAA/tables/1")).andReturn().getResponse().getStatus());
    }

    // ---- the demo

    @Test
    void testThatTheSeededDemoPlannerHasAPaidEventTodayWithTablesPlatesAndGuests() {
        seeder.run();

        Tenant owambo = tenantRepo.findBySlug(DataSeeder.PLANNER_SLUG).orElseThrow();
        assertEquals(TenantType.EVENT_PLANNER, owambo.getType());
        assertTrue(workerRepo.findByUsername("owambeco.manager").isPresent());
        Event demo = eventRepo.findByTenant_TenantIdOrderByStartsAtDesc(owambo.getTenantId()).get(0);
        assertEquals("Ada and Tobi, the wedding", demo.getName());
        assertEquals("The Garden Hall, Ikeja", demo.getVenue());
        assertEquals(6, demo.getEventCode().length());
        assertEquals(List.of(1, 2, 3), tableRepo.findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(demo.getId()).stream().map(RestaurantTable::getDisplayNumber).toList());
        assertEquals(EventStatus.ACTIVE, demo.getStatus());
        assertEquals(EventSeatingMode.ASSIGNED, demo.getSeatingMode());
        assertEquals(2, demo.getPlatesPerGuest());
        assertEquals(java.time.LocalDate.now(), demo.getStartsAt().toLocalDate());
        assertEquals(3, tableRepo.findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(demo.getId()).size());
        assertTrue(tableRepo.findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(demo.getId()).stream().allMatch(t -> t.getCapacity() == 4));
        assertEquals(3, plateRepo.findByEvent_IdOrderByNameAsc(demo.getId()).size());
        assertTrue(guestRepo.countByEvent_Id(demo.getId()) >= 3);
        long before = guestRepo.countByEvent_Id(demo.getId());
        seeder.run();
        assertEquals(before, guestRepo.countByEvent_Id(demo.getId()));
        assertEquals(1, eventRepo.findByTenant_TenantIdOrderByStartsAtDesc(owambo.getTenantId()).size());
    }

    private void signIn(String role) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", tenantId)
                : new WorkerPrincipal("worker-" + role, role, role.equals("PLATFORM_ADMIN") ? null : tenantId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }
}
