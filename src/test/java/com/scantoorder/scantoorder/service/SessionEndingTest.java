package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.JwtFilter;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.CloseTableResponse;
import com.scantoorder.scantoorder.dtos.respond.LeaveSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.SessionSeatView;
import com.scantoorder.scantoorder.dtos.respond.TableSessionsResponse;
import com.scantoorder.scantoorder.exception.SessionEndedException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import jakarta.servlet.http.Cookie;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionEndingTest {
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private JwtFilter jwtFilter;

    private RestaurantTable table;
    private List<Seat> seats;
    private CustomerPrincipal host;
    private String joinCode;

    @BeforeEach
    void setUp() {
        support.clearFloor();
        SecurityContextHolder.clearContext();
        table = support.newTable(4);
        seats = support.seatsOf(table);
        host = support.claim(seats.get(0), "host");
        joinCode = diningSessionService.getMySession(host).getJoinCode();
        support.saveItem("leavingDish", 1000, true);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private String id(int index) {
        return seats.get(index).getSeatId();
    }

    private SeatStatus statusOf(int index) {
        return support.reload(seats.get(index)).getStatus();
    }

    private DinningSessionStatus sessionStatus(CustomerPrincipal member) {
        return dinningSessionRepo.findById(member.getSessionId()).orElseThrow().getSessionStatus();
    }

    private CustomerPrincipal friendJoins(int index) {
        diningSessionService.holdSeats(host, List.of(id(index)));
        JoinSeatRequest request = new JoinSeatRequest();
        request.setJoinCode(joinCode);
        request.setCustomerName("friend");
        request.setCustomerPhoneNumber("08012345678");
        seatService.joinSeat(id(index), request, null);
        return support.principalFor(id(index));
    }

    private void guestWithoutPhoneSits(int index) {
        diningSessionService.holdSeats(host, List.of(id(index)));
        ConfirmGuestRequest request = new ConfirmGuestRequest();
        request.setSeatId(id(index));
        request.setGuestName("Grandma");
        diningSessionService.confirmGuest(host, request);
    }

    private Order order(CustomerPrincipal customer, OrderStatus status) {
        CreateOrderRequest request = new CreateOrderRequest();
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName("leavingDish");
        line.setQuantity(1);
        request.getOrderItems().add(line);
        Order order = orderRepo.findOrderByOrderId(orderService.createOrder(request, customer).getOrderId());
        order.setOrderStatus(status);
        return orderRepo.save(order);
    }

    private OrderStatus statusOf(Order order) {
        return orderRepo.findOrderByOrderId(order.getOrderId()).getOrderStatus();
    }

    @Test
    void testThatLeavingFreesOnlyTheLeaversSeat() {
        CustomerPrincipal friend = friendJoins(1);

        LeaveSessionResponse response = diningSessionService.leave(friend);

        assertFalse(response.isSessionClosed());
        assertEquals(SeatStatus.VACANT, statusOf(1));
        assertEquals(SeatStatus.OCCUPIED, statusOf(0));
        assertEquals(DinningSessionStatus.ACTIVE, sessionStatus(host));
    }

    @Test
    void testThatLeavingCancelsOnlyTheLeaversUnpaidOrders() {
        CustomerPrincipal friend = friendJoins(1);
        Order friendsUnpaid = order(friend, OrderStatus.PENDING_PAYMENT);
        Order hostsUnpaid = order(host, OrderStatus.PENDING_PAYMENT);

        diningSessionService.leave(friend);

        assertEquals(OrderStatus.CANCELED, statusOf(friendsUnpaid));
        assertEquals(OrderStatus.PENDING_PAYMENT, statusOf(hostsUnpaid));
    }

    @Test
    void testThatAGuestCanLeaveWithPaidFoodStillComing() {
        CustomerPrincipal friend = friendJoins(1);
        Order paid = order(friend, OrderStatus.PAID);
        Order cooking = order(friend, OrderStatus.PREPARING);

        diningSessionService.leave(friend);

        assertEquals(SeatStatus.VACANT, statusOf(1));
        assertEquals(OrderStatus.PAID, statusOf(paid));
        assertEquals(OrderStatus.PREPARING, statusOf(cooking));
    }

    @Test
    void testThatTheLastGuestLeavingClosesTheSessionAndReleasesItsHolds() {
        diningSessionService.holdSeats(host, List.of(id(1), id(2)));

        LeaveSessionResponse response = diningSessionService.leave(host);

        assertTrue(response.isSessionClosed());
        assertEquals(DinningSessionStatus.CLOSED, sessionStatus(host));
        assertEquals(SeatStatus.VACANT, statusOf(0));
        assertEquals(SeatStatus.VACANT, statusOf(1));
        assertEquals(SeatStatus.VACANT, statusOf(2));
    }

    @Test
    void testThatGuestsWithoutPhonesKeepTheirSeatsWhenTheLastPhoneLeaves() {
        guestWithoutPhoneSits(1);

        LeaveSessionResponse response = diningSessionService.leave(host);

        assertFalse(response.isSessionClosed());
        assertEquals(SeatStatus.OCCUPIED, statusOf(1));
        assertEquals(DinningSessionStatus.ACTIVE, sessionStatus(host));
    }

    @Test
    void testThatAGuestWhoLeftIsToldTheirSessionHasEnded() {
        diningSessionService.leave(host);
        assertThrows(SessionEndedException.class, () -> diningSessionService.leave(host));
        assertThrows(SessionEndedException.class, () -> diningSessionService.getMySession(host));
        assertThrows(SessionEndedException.class, () -> diningSessionService.holdSeats(host, List.of(id(1))));
    }

    @Test
    void testThatTheTokenStopsWorkingOnceTheGuestHasLeft() throws Exception {
        SeatClaimedResponse claimed = seatService.claimSeat(support.claimRequest(seats.get(3), "newcomer"), null);
        CustomerPrincipal newcomer = support.principalFor(id(3));

        assertNotNull(authenticationFor(claimed.getToken(), new MockHttpServletRequest()));
        SecurityContextHolder.clearContext();

        diningSessionService.leave(newcomer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNull(authenticationFor(claimed.getToken(), request));
        assertNotNull(request.getAttribute(JwtFilter.SESSION_ENDED_ATTRIBUTE));
    }

    private Authentication authenticationFor(String token, MockHttpServletRequest request) throws Exception {
        request.setCookies(new Cookie("access_token", token));
        jwtFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void testThatAWaiterClosingASessionFreesItsSeatsAndCancelsUnpaidOrders() {
        CustomerPrincipal friend = friendJoins(1);
        diningSessionService.holdSeats(host, List.of(id(2)));
        CustomerPrincipal stranger = support.claim(seats.get(3), "stranger");
        Order unpaid = order(host, OrderStatus.PENDING_PAYMENT);
        Order paid = order(friend, OrderStatus.PAID);
        Order ready = order(friend, OrderStatus.READY);
        order(friend, OrderStatus.DELIVERED);
        Order strangersUnpaid = order(stranger, OrderStatus.PENDING_PAYMENT);

        CloseSessionResponse response = diningSessionService.closeSession(support.tenantId(), host.getSessionId());

        assertEquals("CLOSED", response.getStatus());
        assertEquals(2, response.getPaidUndeliveredOrders());
        assertEquals(SeatStatus.VACANT, statusOf(0));
        assertEquals(SeatStatus.VACANT, statusOf(1));
        assertEquals(SeatStatus.VACANT, statusOf(2));
        assertEquals(OrderStatus.CANCELED, statusOf(unpaid));
        assertEquals(OrderStatus.PAID, statusOf(paid));
        assertEquals(OrderStatus.READY, statusOf(ready));
        assertThrows(SessionEndedException.class, () -> diningSessionService.getMySession(friend));

        assertEquals(SeatStatus.OCCUPIED, statusOf(3));
        assertEquals(DinningSessionStatus.ACTIVE, sessionStatus(stranger));
        assertEquals(OrderStatus.PENDING_PAYMENT, statusOf(strangersUnpaid));
    }

    @Test
    void testThatClosingATableClosesEverySessionAtIt() {
        diningSessionService.holdSeats(host, List.of(id(1)));
        CustomerPrincipal stranger = support.claim(seats.get(2), "stranger");
        CustomerPrincipal elsewhere = support.claim(support.seatsOf(support.newTable(1)).get(0), "elsewhere");
        order(stranger, OrderStatus.PAID);

        CloseTableResponse response = diningSessionService.closeTable(support.tenantId(), table.getTableId());

        assertEquals(2, response.getClosedSessions());
        assertEquals(1, response.getPaidUndeliveredOrders());
        for (int i = 0; i < 4; i++) {
            assertEquals(SeatStatus.VACANT, statusOf(i));
        }
        assertEquals(DinningSessionStatus.CLOSED, sessionStatus(host));
        assertEquals(DinningSessionStatus.CLOSED, sessionStatus(stranger));
        assertEquals(DinningSessionStatus.ACTIVE, sessionStatus(elsewhere));
    }

    @Test
    void testThatStaffSeeATablesSessionsWithNamesButNoPhoneNumbers() {
        CustomerPrincipal friend = friendJoins(1);
        support.claim(seats.get(3), "stranger");
        order(friend, OrderStatus.PAID);
        order(host, OrderStatus.PENDING_PAYMENT);

        TableSessionsResponse response = diningSessionService.getTableSessions(support.tenantId(), table.getTableId());

        assertEquals(2, response.getSessions().size());
        assertEquals(1, response.getPaidUndeliveredOrders());
        assertEquals(1, response.getSessions().get(0).getPaidUndeliveredOrders());
        assertEquals(0, response.getSessions().get(1).getPaidUndeliveredOrders());
        List<SessionSeatView> firstGroup = response.getSessions().get(0).getSeats();
        assertEquals(List.of("host", "friend"), firstGroup.stream().map(SessionSeatView::getGuestName).toList());
        assertTrue(firstGroup.stream().allMatch(seat -> seat.getGuestPhone() == null));
    }
}
