package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LiveStreamTest {
    @Autowired
    private LiveUpdates live;
    @Autowired
    private OrderStepService steps;
    @Autowired
    private SeatingTestSupport seating;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private WebApplicationContext wac;

    private final List<LiveUpdates.Subscription> open = new ArrayList<>();
    private Tenant tenant;
    private Tenant other;

    @BeforeEach
    void setUp() {
        tenant = tenants.freshActiveTenant();
        tenant.setOpenForOrders(true);
        tenant = tenants.save(tenant);
        other = tenants.freshActiveTenant();
    }

    @AfterEach
    void tearDown() {
        open.forEach(live::unsubscribe);
        SecurityContextHolder.clearContext();
    }

    private List<LiveUpdates.Notice> staff(Tenant of) {
        List<LiveUpdates.Notice> got = new CopyOnWriteArrayList<>();
        open.add(live.subscribeStaff(of.getTenantId(), got::add));
        return got;
    }

    private List<LiveUpdates.Notice> guest(Tenant of, String session) {
        List<LiveUpdates.Notice> got = new CopyOnWriteArrayList<>();
        open.add(live.subscribeGuest(of.getTenantId(), session, got::add));
        return got;
    }

    // Notices go out once the change is saved. These tests roll back, so the "saved" step is run by hand.
    private void saved() {
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    void testThatAGuestStreamGetsOnlyItsOwnSessionsNoticesAndAStaffStreamOnlyItsOwnBusiness() {
        List<LiveUpdates.Notice> staffHere = staff(tenant);
        List<LiveUpdates.Notice> staffElsewhere = staff(other);
        List<LiveUpdates.Notice> guestA = guest(tenant, "session-a");
        List<LiveUpdates.Notice> guestB = guest(tenant, "session-b");
        List<LiveUpdates.Notice> guestElsewhere = guest(other, "session-a");

        live.publish(tenant.getTenantId(), "session-a", "order", "o-1", "READY");
        saved();

        assertEquals(List.of(new LiveUpdates.Notice("order", "o-1", "READY")), staffHere);
        assertEquals(staffHere, guestA);
        assertTrue(guestB.isEmpty());
        assertTrue(staffElsewhere.isEmpty());
        assertTrue(guestElsewhere.isEmpty());
        assertEquals("{\"type\":\"order\",\"id\":\"o-1\",\"step\":\"READY\"}", guestA.get(0).json());
    }

    @Test
    void testThatAStaffOnlyNoticeNeverReachesAGuestButMenuReachesEveryGuestOfTheBusinessAndNoOneElse() {
        List<LiveUpdates.Notice> staffHere = staff(tenant);
        List<LiveUpdates.Notice> guestA = guest(tenant, "session-a");
        List<LiveUpdates.Notice> guestElsewhere = guest(other, "session-z");
        List<LiveUpdates.Notice> staffElsewhere = staff(other);

        live.publish(tenant.getTenantId(), "call", "c-1", "OPEN");
        saved();
        live.publish(tenant.getTenantId(), "table", "t-1", "CLOSED");
        saved();

        assertEquals(2, staffHere.size());
        assertTrue(guestA.isEmpty(), "a notice with no session reaches staff only");

        live.publish(tenant.getTenantId(), "menu", null, null);
        saved();

        assertEquals("menu", guestA.get(0).type());
        assertEquals("{\"type\":\"menu\"}", guestA.get(0).json());
        assertEquals(3, staffHere.size());
        assertTrue(guestElsewhere.isEmpty());
        assertTrue(staffElsewhere.isEmpty());
    }

    @Test
    void testThatAStepMoveTellsTheStaffAndTheGuestsSessionOnceItIsSaved() {
        RestaurantTable table = seating.newTable(tenant.getTenantId(), 2);
        CustomerPrincipal guest = seating.claim(seating.seatsOf(table).get(0), "Ada");
        CustomerPrincipal stranger = seating.claim(seating.seatsOf(seating.newTable(tenant.getTenantId(), 2)).get(0), "Stranger");
        Item item = pay.item(tenant, "Stream plate", 500, true);
        Order order = pay.order(tenant, OrderStatus.PAID, item, 1);
        order.setDinningSession(seatingSession(guest));
        orderRepo.save(order);
        Worker chef = new Worker();
        chef.setTenant(tenant);
        chef.setFullName("Bisi Adebayo");
        chef.setUsername("stream.chef." + System.nanoTime());
        chef.setPassword("x");
        chef.setRole(WorkerRole.CHEF);
        chef = workerRepo.save(chef);
        List<LiveUpdates.Notice> staffHere = staff(tenant);
        List<LiveUpdates.Notice> mine = guest(tenant, guest.getSessionId());
        List<LiveUpdates.Notice> notMine = guest(tenant, stranger.getSessionId());
        saved();
        staffHere.clear();
        mine.clear();
        notMine.clear();

        steps.start(tenant.getTenantId(), chef.getWorkerId(), order.getOrderId());
        assertTrue(staffHere.isEmpty(), "nothing goes out before the change is saved");
        saved();

        assertEquals(List.of(new LiveUpdates.Notice("order", order.getOrderId(), "PREPARING")), staffHere);
        assertEquals(staffHere, mine);
        assertTrue(notMine.isEmpty());
    }

    private DinningSession seatingSession(CustomerPrincipal guest) {
        return seating.sessionOf(guest);
    }

    @Test
    void testThatSeatingAGuestTellsTheStaffAndOwnSessionOnly() {
        RestaurantTable table = seating.newTable(tenant.getTenantId(), 2);
        List<LiveUpdates.Notice> staffHere = staff(tenant);
        List<LiveUpdates.Notice> bystander = guest(tenant, "someone-else");
        saved();
        staffHere.clear();

        CustomerPrincipal guest = seating.claim(seating.seatsOf(table).get(0), "Ada");
        saved();

        assertTrue(staffHere.stream().anyMatch(n -> n.type().equals("seat") && "OCCUPIED".equals(n.step())));
        assertTrue(bystander.isEmpty());
        assertNotNull(guest);
    }

    @Test
    void testThatAHeartbeatReachesEveryOpenStreamAndABrokenOneIsDropped() {
        List<LiveUpdates.Notice> staffHere = staff(tenant);
        List<LiveUpdates.Notice> guestA = guest(other, "session-a");
        int[] calls = {0};
        open.add(live.subscribeStaff(tenant.getTenantId(), notice -> {
            calls[0]++;
            throw new IllegalStateException("the connection is gone");
        }));
        int before = live.openStreams();

        live.heartbeat();
        live.heartbeat();

        assertEquals("heartbeat", staffHere.get(staffHere.size() - 1).type());
        assertTrue(staffHere.stream().filter(n -> n.type().equals("heartbeat")).count() >= 2);
        assertTrue(guestA.stream().anyMatch(n -> n.type().equals("heartbeat")));
        assertEquals(1, calls[0], "a broken stream is forgotten after its first failure");
        assertEquals(before - 1, live.openStreams());
    }

    @Test
    void testThatUnsubscribingStopsNotices() {
        List<LiveUpdates.Notice> got = new ArrayList<>();
        LiveUpdates.Subscription subscription = live.subscribeStaff(tenant.getTenantId(), got::add);
        live.publish(tenant.getTenantId(), "table", "t", null);
        saved();
        live.unsubscribe(subscription);
        live.publish(tenant.getTenantId(), "table", "t", null);
        saved();
        assertEquals(1, got.size());
    }

    private void signIn(String role, String tenantId, String sessionId) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal(sessionId, "seat", "table", "member", tenantId)
                : new WorkerPrincipal("worker-" + role, role, role.equals("PLATFORM_ADMIN") ? null : tenantId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void testThatTheStreamRoutesOpenForTheRightCallersAndRefuseTheOthers() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        int before = live.openStreams();

        for (String role : List.of("CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role, tenant.getTenantId(), "s1");
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/stream/staff")).andReturn().getResponse().getStatus(), role);
        }
        for (String role : List.of("CHEF", "WAITER", "MANAGER", "PLATFORM_ADMIN")) {
            signIn(role, tenant.getTenantId(), "s1");
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/stream/guest")).andReturn().getResponse().getStatus(), role);
        }
        assertEquals(before, live.openStreams());

        for (String role : List.of("CHEF", "WAITER", "MANAGER")) {
            signIn(role, tenant.getTenantId(), "s1");
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/stream/staff")).andReturn();
            assertTrue(result.getRequest().isAsyncStarted(), role);
        }
        signIn("CUSTOMER", tenant.getTenantId(), "s1");
        MvcResult guestStream = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/stream/guest")).andReturn();
        assertTrue(guestStream.getRequest().isAsyncStarted());
        assertEquals(before + 4, live.openStreams());

        // A real notice arrives on the open connection, and only the right ones.
        live.publish(tenant.getTenantId(), "s1", "order", "o-9", "READY");
        saved();
        live.publish(tenant.getTenantId(), "someone-else", "order", "o-10", "READY");
        saved();
        String body = guestStream.getResponse().getContentAsString();
        assertTrue(body.contains("o-9"));
        assertFalse(body.contains("o-10"));
    }
}
