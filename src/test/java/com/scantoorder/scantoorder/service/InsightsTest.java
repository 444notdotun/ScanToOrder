package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.respond.InsightsResponse;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every figure on known data. The clock is a Wednesday in Lagos, so a week that starts on Monday is checked.
 */
@SpringBootTest
@Transactional
class InsightsTest {
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    // Wednesday 18 November 2026, 10:00 in Lagos.
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, LAGOS);

    @Autowired
    private InsightsService insights;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private EventTestSupport events;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo sessionRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private ServiceCallRepo callRepo;
    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private SeatLedger ledger;
    @Autowired
    private RefundService refundService;
    @Autowired
    private WebApplicationContext wac;
    @PersistenceContext
    private EntityManager entityManager;

    private Tenant tenant;
    private String tenantId;
    private Item jollof;
    private Item rice;
    private Item soda;

    @BeforeEach
    void setUp() {
        tenant = tenants.freshActiveTenant();
        tenantId = tenant.getTenantId();
        jollof = pay.item(tenant, "Insight jollof", 1000, true);
        rice = pay.item(tenant, "Insight rice", 500, true);
        soda = pay.item(tenant, "Insight soda", 200, true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LocalDateTime lagos(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, LAGOS).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    // A paid order of one or more lines, paid and delivered at the given times.
    private Order paidOrder(long amountKobo, long chargeKobo, LocalDateTime paidAt, LocalDateTime deliveredAt, Object... itemsAndQuantities) {
        Order order = pay.order(tenant, OrderStatus.DELIVERED, jollof, 1);
        // Replace the placeholder line with the lines this test wants.
        orderItemRepo.deleteAll(orderItemRepo.findByOrder_OrderId(order.getOrderId()));
        for (int i = 0; i < itemsAndQuantities.length; i += 2) {
            Item item = (Item) itemsAndQuantities[i];
            int quantity = (Integer) itemsAndQuantities[i + 1];
            OrderItem line = new OrderItem();
            line.setTenant(tenant);
            line.setOrder(order);
            line.setItem(item);
            line.setQuantity(quantity);
            line.setUnitPrice(item.getItemPrice());
            line.setTotalPrice(item.getItemPrice().multiply(java.math.BigDecimal.valueOf(quantity)));
            orderItemRepo.save(line);
        }
        order.setTotalAmount(java.math.BigDecimal.valueOf(amountKobo, 2));
        order.setPaidAt(paidAt);
        order.setDeliveredAt(deliveredAt);
        if (deliveredAt == null) order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);
        Payment payment = pay.payment(order, PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(paidAt);
        payment.setPlatelyChargeKobo(chargeKobo);
        paymentRepo.save(payment);
        return order;
    }

    private Refund refund(Order order, RefundReason reason, RefundStatus status, LocalDateTime when) {
        Payment payment = paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow();
        Refund refund = refundService.createDue(payment, reason);
        refund.setStatus(status);
        if (status == RefundStatus.SETTLED) refund.setSettledAt(when);
        if (status == RefundStatus.CONFIRMED) refund.setGuestConfirmedAt(when);
        return refundRepo.save(refund);
    }

    private void stampCall(ServiceCall call, LocalDateTime created, LocalDateTime taken) {
        entityManager.createNativeQuery("UPDATE service_call SET created_at = :created, taken_at = :taken WHERE service_call_id = :id")
                .setParameter("created", created).setParameter("taken", taken).setParameter("id", call.getServiceCallId()).executeUpdate();
        entityManager.clear();
    }

    // Monday and Tuesday of that week, Sunday before it (outside the week), and Wednesday morning (today).
    private void knownWeek() {
        Order monday = paidOrder(500000, 10000, lagos(11, 16, 0, 30), lagos(11, 16, 1, 10), jollof, 2, soda, 1);
        Order tuesday = paidOrder(300000, 6000, lagos(11, 17, 13, 10), lagos(11, 17, 13, 30), jollof, 1, rice, 4);
        tuesday.setUnavailableReason(UnavailableReason.RAN_OUT);
        orderRepo.save(tuesday);
        Order sunday = paidOrder(200000, 4000, lagos(11, 15, 23, 30), lagos(11, 16, 0, 10), rice, 1);
        paidOrder(100000, 2000, lagos(11, 18, 9, 0), null, soda, 1);
        refund(tuesday, RefundReason.KITCHEN_UNAVAILABLE, RefundStatus.SETTLED, lagos(11, 17, 15, 0));
        refund(sunday, RefundReason.LATE_PAYMENT, RefundStatus.CONFIRMED, lagos(11, 15, 23, 50));
    }

    @Test
    void testThatAWeekStartsOnMondayInLagosTimeAndEachMoneyFigureIsAddedFromRecords() {
        knownWeek();

        InsightsResponse week = insights.insights(tenantId, "week", NOW);

        assertTrue(week.isHasData());
        assertEquals("week", week.getPeriod());
        // Monday, Tuesday and Wednesday count; the Sunday order at 23:30 Lagos time does not.
        assertEquals(3, week.getOrders());
        assertEquals(900000, week.getSalesKobo());
        assertEquals(18000, week.getPlatelyChargeKobo());
        assertEquals(300000, week.getRefundsKobo());
        assertEquals(900000 - 18000 - 300000, week.getReceivedKobo());
        assertEquals(1, week.getOrdersKitchenCouldNotMake());
        assertEquals(300000, week.getAverageOrderKobo());
    }

    @Test
    void testThatTodayIsOneDayAndAMonthIsTheCalendarMonthWithItsRefunds() {
        knownWeek();

        InsightsResponse today = insights.insights(tenantId, "today", NOW);
        InsightsResponse month = insights.insights(tenantId, "month", NOW);

        assertEquals(1, today.getOrders());
        assertEquals(100000, today.getSalesKobo());
        assertEquals(0, today.getRefundsKobo());
        assertEquals(100000 - 2000, today.getReceivedKobo());
        assertEquals(4, month.getOrders());
        assertEquals(1100000, month.getSalesKobo());
        assertEquals(22000, month.getPlatelyChargeKobo());
        // A refund the guest confirmed counts in the period it was confirmed.
        assertEquals(500000, month.getRefundsKobo());
        assertEquals(1100000 - 22000 - 500000, month.getReceivedKobo());
        assertEquals(275000, month.getAverageOrderKobo());
    }

    @Test
    void testThatAMondayEarlyMorningInLagosIsInThatWeekEvenWhenTheServerClockIsElsewhere() {
        paidOrder(100000, 2000, lagos(11, 16, 0, 5), null, soda, 1);
        paidOrder(100000, 2000, lagos(11, 15, 23, 55), null, soda, 1);

        assertEquals(1, insights.insights(tenantId, "week", NOW).getOrders());
        assertEquals(1, insights.insights(tenantId, "week", ZonedDateTime.of(2026, 11, 22, 23, 59, 0, 0, LAGOS)).getOrders());
        assertEquals(0, insights.insights(tenantId, "week", ZonedDateTime.of(2026, 11, 23, 0, 1, 0, 0, LAGOS)).getOrders());
    }

    @Test
    void testThatBestPlatesAreTheTopFiveByQuantityAndBusiestHoursAreOrdersPerLagosHour() {
        knownWeek();

        InsightsResponse week = insights.insights(tenantId, "week", NOW);

        assertEquals(List.of("Insight rice", "Insight jollof", "Insight soda"), week.getBestPlates().stream().map(InsightsResponse.BestPlate::getName).toList());
        assertEquals(List.of(4L, 3L, 2L), week.getBestPlates().stream().map(InsightsResponse.BestPlate::getQuantity).toList());
        assertEquals(24, week.getBusiestHours().size());
        assertEquals(1, week.getBusiestHours().get(0).getOrders());
        assertEquals(1, week.getBusiestHours().get(9).getOrders());
        assertEquals(1, week.getBusiestHours().get(13).getOrders());
        assertEquals(3, week.getBusiestHours().stream().mapToLong(InsightsResponse.HourCount::getOrders).sum());

        Order extra = paidOrder(100000, 2000, lagos(11, 17, 14, 0), null,
                pay.item(tenant, "Plate a", 100, true), 1, pay.item(tenant, "Plate b", 100, true), 1, pay.item(tenant, "Plate c", 100, true), 1);
        assertNotNull(extra);
        assertEquals(5, insights.insights(tenantId, "week", NOW).getBestPlates().size());
    }

    @Test
    void testThatPaidToDeliveredIsAveragedOverDeliveredOrdersOnly() {
        knownWeek();
        // Monday took 40 minutes, Tuesday 20, Wednesday is not delivered yet.
        assertEquals(30.0, insights.insights(tenantId, "week", NOW).getPaidToDeliveredMinutes());
        assertNull(insights.insights(tenantId, "today", NOW).getPaidToDeliveredMinutes());
    }

    @Test
    void testThatGuestsSeatedSeatsHeldAndTakenUpAreCountedFromTheSeatRecords() {
        knownWeek();
        RestaurantTable table = tableRepo.findById(tableService.createTable(tenantId, 6).getTableId()).orElseThrow();
        List<Seat> seats = seatRepo.findByTableIdOrderByPositionAsc(table);
        DinningSession session = new DinningSession();
        session.setTableId(table);
        session.setTenant(tenant);
        session = sessionRepo.save(session);
        SessionSeat one = ledger.occupy(session, seats.get(0), "One", "080", null);
        one.setClaimedAt(lagos(11, 16, 12, 0));
        SessionSeat two = ledger.occupy(session, seats.get(1), "Two", "080", null);
        two.setClaimedAt(lagos(11, 17, 12, 0));
        SessionSeat outside = ledger.occupy(session, seats.get(4), "Sunday", "080", null);
        outside.setClaimedAt(lagos(11, 15, 12, 0));
        SessionSeat heldAndLeft = ledger.hold(session, seats.get(2));
        heldAndLeft.setHeldAt(lagos(11, 17, 11, 0));
        SessionSeat heldAndTaken = ledger.hold(session, seats.get(3));
        heldAndTaken.setHeldAt(lagos(11, 17, 11, 5));
        ledger.seatGuest(heldAndTaken, "Friend", "080", null, true);
        heldAndTaken.setClaimedAt(lagos(11, 17, 13, 0));
        sessionSeatRepo.saveAll(List.of(one, two, outside, heldAndLeft, heldAndTaken));

        InsightsResponse week = insights.insights(tenantId, "week", NOW);

        assertEquals(3, week.getGuestsSeated());
        assertEquals(2, week.getSeatsHeld());
        assertEquals(1, week.getSeatsHeldTakenUp());
    }

    @Test
    void testThatWaiterCallsAndTheAverageMinutesFromCallToTakenAreCounted() {
        knownWeek();
        ServiceCall first = callRepo.save(newCall());
        ServiceCall second = callRepo.save(newCall());
        ServiceCall untaken = callRepo.save(newCall());
        ServiceCall sunday = callRepo.save(newCall());
        callRepo.flush();
        stampCall(first, lagos(11, 16, 10, 0), lagos(11, 16, 10, 4));
        stampCall(second, lagos(11, 17, 10, 0), lagos(11, 17, 10, 6));
        stampCall(untaken, lagos(11, 17, 11, 0), null);
        stampCall(sunday, lagos(11, 15, 10, 0), lagos(11, 15, 10, 30));

        InsightsResponse week = insights.insights(tenantId, "week", NOW);

        assertEquals(3, week.getWaiterCalls());
        assertEquals(5.0, week.getCallToTakenMinutes());
    }

    private ServiceCall newCall() {
        ServiceCall call = new ServiceCall();
        call.setTenant(tenant);
        call.setServiceDescription("Water");
        return call;
    }

    @Test
    void testThatABusinessWithNoPaidOrdersInThePeriodAnswersHasDataFalse() {
        InsightsResponse none = insights.insights(tenantId, "week", NOW);

        assertFalse(none.isHasData());
        assertEquals(0, none.getOrders());
        assertEquals(0, none.getSalesKobo());
        assertTrue(none.getBestPlates().isEmpty());
        // Paid, but in another period: still nothing for this one.
        paidOrder(100000, 2000, lagos(10, 1, 12, 0), null, soda, 1);
        assertFalse(insights.insights(tenantId, "week", NOW).isHasData());
        assertTrue(insights.insights(tenantId, "month", ZonedDateTime.of(2026, 10, 20, 9, 0, 0, 0, LAGOS)).isHasData());
    }

    @Test
    void testThatAnUnknownPeriodIsRefusedAndAPlannerHasNoInsights() {
        assertThrows(InvalidRequestException.class, () -> insights.insights(tenantId, "year", NOW));
        Tenant planner = events.planner();
        assertThrows(AccessDeniedException.class, () -> insights.insights(planner.getTenantId(), "week", NOW));
    }

    @Test
    void testThatAnotherBusinessesSalesNeverCount() {
        knownWeek();
        Tenant other = tenants.other();
        Item theirs = pay.item(other, "Their insight plate", 100, true);
        Order foreign = pay.order(other, OrderStatus.PAID, theirs, 1);
        foreign.setPaidAt(lagos(11, 17, 12, 0));
        orderRepo.save(foreign);
        Payment payment = pay.payment(foreign, PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(lagos(11, 17, 12, 0));
        paymentRepo.save(payment);

        assertEquals(900000, insights.insights(tenantId, "week", NOW).getSalesKobo());
    }

    @Test
    void testThatOnlyAManagerReachesInsightsAndAPlannersManagerIsRefused() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        for (String role : List.of("CHEF", "WAITER", "CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role, tenantId);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/insights").param("period", "week")).andReturn().getResponse().getStatus(), role);
        }
        signIn("MANAGER", tenantId);
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/insights").param("period", "month")).andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/insights").param("period", "decade")).andReturn().getResponse().getStatus());
        signIn("MANAGER", events.planner().getTenantId());
        assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/insights")).andReturn().getResponse().getStatus());
    }

    private void signIn(String role, String forTenant) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", forTenant)
                : new WorkerPrincipal("worker-" + role, role, role.equals("PLATFORM_ADMIN") ? null : forTenant);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }
}
