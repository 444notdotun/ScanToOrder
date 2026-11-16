package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.SessionOrdersResponse;
import com.scantoorder.scantoorder.dtos.respond.SessionViewResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.exception.SessionEndedException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SessionOrdersTest {
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;

    private Tenant tenant;
    private RestaurantTable table;
    private List<Seat> seats;
    private Item dish;

    @BeforeEach
    void setUp() {
        tenant = tenants.freshActiveTenant();
        tenant.setOpenForOrders(true);
        tenant = tenants.save(tenant);
        table = support.newTable(tenant.getTenantId(), 4);
        seats = support.seatsOf(table);
        dish = pay.item(tenant, "Orders dish", 1500, true);
    }

    private Order order(CustomerPrincipal from, String forSeatId, OrderStatus status, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName(dish.getItemName());
        line.setQuantity(quantity);
        request.getOrderItems().add(line);
        request.setSeatId(forSeatId);
        Order order = orderRepo.findOrderByOrderId(orderService.createOrder(request, from).getOrderId());
        order.setOrderStatus(status);
        order = orderRepo.save(order);
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.CANCELED) {
            pay.payment(order, PaymentStatus.SUCCESSFUL);
        }
        return order;
    }

    @Test
    void testThatTheSeatMapAndTheSessionNameTheBusiness() {
        CustomerPrincipal me = support.claim(seats.get(0), "Ada");

        SessionViewResponse view = diningSessionService.getMySession(me);
        ViewTableAndSeatAvailabilityResponse map = tableService.publicSeatMap(tenant.getSlug(), table.getDisplayNumber());

        assertEquals(tenant.getName(), view.getBusinessName());
        assertEquals(tenant.getName(), map.getBusinessName());
    }

    @Test
    void testThatAGuestSeesTheirOwnOrdersAndThoseForGuestsWithoutAPhoneButNotAnotherPhonesAndTotalsAddUp() {
        CustomerPrincipal me = support.claim(seats.get(0), "Ada Obi");
        diningSessionService.holdSeats(me, List.of(seats.get(1).getSeatId(), seats.get(2).getSeatId()));
        ConfirmGuestRequest confirm = new ConfirmGuestRequest();
        confirm.setSeatId(seats.get(1).getSeatId());
        confirm.setGuestName("Mama Ada");
        diningSessionService.confirmGuest(me, confirm);
        SessionSeat friendSeat = sessionSeatRepo.findBySeatAndStatusIn(seats.get(2).getSeatId(), SeatLedger.ACTIVE).orElseThrow();
        com.scantoorder.scantoorder.dtos.request.JoinSeatRequest join = new com.scantoorder.scantoorder.dtos.request.JoinSeatRequest();
        join.setJoinCode(friendSeat.getSession().getJoinCode());
        join.setCustomerName("Friend");
        join.setCustomerPhoneNumber("08012345678");
        support.joinSeat(seats.get(2).getSeatId(), join);
        CustomerPrincipal friend = support.principalFor(seats.get(2).getSeatId());

        Order mine = order(me, null, OrderStatus.PREPARING, 2);
        Order forMama = order(me, seats.get(1).getSeatId(), OrderStatus.PAID, 1);
        Order pending = order(me, null, OrderStatus.PENDING_PAYMENT, 1);
        order(me, null, OrderStatus.CANCELED, 1);
        order(friend, null, OrderStatus.DELIVERED, 3);

        SessionOrdersResponse mineSeen = diningSessionService.getSessionOrders(me);

        assertEquals(3, mineSeen.getOrders().size(), "cancelled and another phone's orders are not shown");
        SessionOrdersResponse.Order first = mineSeen.getOrders().stream().filter(o -> o.getOrderId().equals(mine.getOrderId())).findFirst().orElseThrow();
        assertTrue(first.isForMe());
        assertEquals("PREPARING", first.getStatus());
        assertEquals(List.of("2 x Orders dish"), first.getItems());
        assertNotNull(first.getPaymentReference());
        SessionOrdersResponse.Order mama = mineSeen.getOrders().stream().filter(o -> o.getOrderId().equals(forMama.getOrderId())).findFirst().orElseThrow();
        assertFalse(mama.isForMe());
        assertEquals("Mama Ada", mama.getForName());
        assertEquals(2, mama.getForSeatPosition());
        SessionOrdersResponse.Order waiting = mineSeen.getOrders().stream().filter(o -> o.getOrderId().equals(pending.getOrderId())).findFirst().orElseThrow();
        assertNull(waiting.getPaymentReference());
        assertEquals("PENDING_PAYMENT", waiting.getStatus());
        // Paid from this phone: mine and Mama's. The whole table also includes the friend's.
        assertEquals(0, new BigDecimal("4500.00").compareTo(mineSeen.getPaidByYou()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(mineSeen.getPaidByTable()));

        SessionOrdersResponse friendSeen = diningSessionService.getSessionOrders(friend);
        assertEquals(2, friendSeen.getOrders().size(), "the friend sees their own and Mama's, not Ada's");
        // The friend's own three dishes plus Mama's one.
        assertEquals(0, new BigDecimal("6000.00").compareTo(friendSeen.getPaidByYou()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(friendSeen.getPaidByTable()));
    }

    @Test
    void testThatSomeoneWhoHasLeftGetsSessionEndedNotOrders() {
        CustomerPrincipal me = support.claim(seats.get(0), "Ada");
        diningSessionService.leave(me);
        assertThrows(SessionEndedException.class, () -> diningSessionService.getSessionOrders(me));
    }
}
