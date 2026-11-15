package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.OrderStatusException;
import com.scantoorder.scantoorder.exception.SeatOwnershipException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SessionEndedException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class OrderServiceTest {
    private CreateOrderRequest createOrderRequest;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private OrderRepo orderRepo;
    private List<Seat> seats;
    private CustomerPrincipal customer;

    @BeforeEach
    void setUp() {
        support.clearFloor();
        seats = support.seatsOf(support.newTable(4));
        customer = support.claim(seats.get(0), "host");
        saveItem("itemName", 5000, true);
        saveItem("itemName1", 51000, true);
        createOrderRequest=new CreateOrderRequest();
        createOrderRequest.getOrderItems().add(orderItem("itemName1", 5));
        createOrderRequest.getOrderItems().add(orderItem("itemName", 5));
    }

    private void saveItem(String name, int price, boolean available) {
        Item item = itemRepo.findItemsByItemName(name).orElseGet(Item::new);
        item.setItemName(name);
        item.setItemPrice(BigDecimal.valueOf(price));
        item = itemRepo.save(item);
        item.setAvailable(available);
        itemRepo.save(item);
    }

    private CreateOrderItemRequest orderItem(String name, int quantity) {
        CreateOrderItemRequest request = new CreateOrderItemRequest();
        request.setItemName(name);
        request.setQuantity(quantity);
        return request;
    }

    private String id(int index) {
        return seats.get(index).getSeatId();
    }

    private CreateOrderRequest orderFor(int seatIndex) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.getOrderItems().add(orderItem("itemName", 1));
        request.setSeatId(id(seatIndex));
        return request;
    }

    @Test
    void testThatAnOrderDefaultsToTheCallersOwnSeat() {
        CreateOrderResponse response = orderService.createOrder(createOrderRequest, customer);
        Order order = orderRepo.findOrderByOrderId(response.getOrderId());
        assertEquals(id(0), order.getSeat().getSeatId());
        assertEquals(customer.getSessionSeatId(), order.getSessionSeat().getId());
        assertEquals(customer.getSessionId(), order.getDinningSession().getSessionId());
    }

    @Test
    void testThatASeatedGuestCanOrderForAGuestWithoutAPhoneInTheirSession() {
        diningSessionService.holdSeats(customer, List.of(id(1)));
        ConfirmGuestRequest guest = new ConfirmGuestRequest();
        guest.setSeatId(id(1));
        guest.setGuestName("Grandma");
        diningSessionService.confirmGuest(customer, guest);

        CreateOrderResponse response = orderService.createOrder(orderFor(1), customer);

        Order order = orderRepo.findOrderByOrderId(response.getOrderId());
        assertEquals(id(1), order.getSeat().getSeatId());
        assertEquals(support.membershipOf(id(1)).getId(), order.getSessionSeat().getId());
    }

    @Test
    void testThatNobodyCanOrderForAHeldSeat() {
        diningSessionService.holdSeats(customer, List.of(id(1)));
        assertThrows(SeatStatusException.class, () -> orderService.createOrder(orderFor(1), customer));
        assertEquals(0, orderRepo.count());
    }

    @Test
    void testThatNobodyCanOrderForAVacantSeatOrAGuestWhoHasLeft() {
        assertThrows(SeatStatusException.class, () -> orderService.createOrder(orderFor(1), customer));

        diningSessionService.holdSeats(customer, List.of(id(1)));
        ConfirmGuestRequest guest = new ConfirmGuestRequest();
        guest.setSeatId(id(1));
        guest.setGuestName("Grandma");
        diningSessionService.confirmGuest(customer, guest);
        seatService.releaseSeat(id(1));
        assertThrows(SeatStatusException.class, () -> orderService.createOrder(orderFor(1), customer));
    }

    @Test
    void testThatNobodyCanOrderForASeatInAnotherSession() {
        support.claim(seats.get(2), "stranger");
        assertThrows(SeatOwnershipException.class, () -> orderService.createOrder(orderFor(2), customer));
    }

    @Test
    void testThatNobodyCanOrderForAGuestWhoHasTheirOwnPhone() {
        String code = diningSessionService.holdSeats(customer, List.of(id(1))).getJoinCode();
        JoinSeatRequest join = new JoinSeatRequest();
        join.setJoinCode(code);
        join.setCustomerName("friend");
        join.setCustomerPhoneNumber("08012345678");
        seatService.joinSeat(id(1), join, null);

        assertThrows(SeatOwnershipException.class, () -> orderService.createOrder(orderFor(1), customer));
    }

    @Test
    void testThatAGuestWhoseSeatWasFreedCannotOrder() {
        seatService.releaseSeat(id(0));
        assertThrows(SessionEndedException.class, () -> orderService.createOrder(createOrderRequest, customer));
    }

    @Test
    void testThatAnOutOfStockItemIsNamedAndNoOrderIsLeftBehind() {
        saveItem("soldOut", 2000, false);
        createOrderRequest.getOrderItems().add(orderItem("soldOut", 1));
        ConflictException exception = assertThrows(ConflictException.class, () -> orderService.createOrder(createOrderRequest, customer));
        assertTrue(exception.getMessage().contains("soldOut"));
        assertEquals(0, orderRepo.count());
    }

    @Test
    void testThatAnEmptyOrderIsRejected() {
        assertThrows(InvalidRequestException.class, () -> orderService.createOrder(new CreateOrderRequest(), customer));
    }

    @Test
    void testThatOrderCanBeCreated() {
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        assertEquals(OrderStatus.PENDING_PAYMENT, createOrderResponse.getOrderStatus());
        assertNotNull(orderRepo.findOrderByOrderId(createOrderResponse.getOrderId()));
    }
    @Test
    void testThatOrderCanBeCanceled() {
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
       Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        String newState = "CANCELED";
        System.out.println(order.getOrderStatus());
        Order updatedOrder = orderService.updateOrder(newState, order.getOrderId());
        assertEquals(OrderStatus.CANCELED, updatedOrder.getOrderStatus());
    }

    @Test
    void testThatOrderCanBeCompletedPreparing(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        String newState = "PREPARING";
        Order updatedOrder = orderService.updateOrder(newState, createOrderResponse.getOrderId());
        assertEquals(OrderStatus.PREPARING, updatedOrder.getOrderStatus());
    }

    @Test
    void testThatOrderCanBeCompletedAsReady(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.PREPARING);
        orderRepo.save(order);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        String newState = "READY";
        Order updatedOrder = orderService.updateOrder(newState, createOrderResponse.getOrderId());
        assertEquals(OrderStatus.READY, updatedOrder.getOrderStatus());
    }
    @Test
    void testThatOrderCanBeCompletedAsDelivered(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.READY);
        orderRepo.save(order);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        String newState = "DELIVERED";
        Order updatedOrder = orderService.updateOrder(newState, createOrderResponse.getOrderId());
        assertEquals(OrderStatus.DELIVERED, updatedOrder.getOrderStatus());
    }

    @Test
    void testThatExceptionIsThrownWhenOrderStatusIsInvalid(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        String newState = "PREPARING";
        assertThrows(OrderStatusException.class,()->orderService.updateOrder(newState, createOrderResponse.getOrderId()));
    }

    @Test
    void testThatStatusOfAnOrderCheck(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        assertEquals(OrderStatus.PENDING_PAYMENT, createOrderResponse.getOrderStatus());
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);
        assertEquals(OrderStatus.PAID.toString(),orderService.checkOrderStatus(createOrderResponse.getOrderId()));
    }

    @Test
    void testThatPaidOrdersCanBeSeen(){

        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse);
        assertEquals(0, BigDecimal.valueOf(280000).compareTo(createOrderResponse.getTotalPrice()));
        assertEquals(OrderStatus.PENDING_PAYMENT, createOrderResponse.getOrderStatus());
        CreateOrderResponse createOrderResponse1 = orderService.createOrder(createOrderRequest, customer);
        assertNotNull(createOrderResponse1);
        orderService.updateOrder("PAID",createOrderResponse1.getOrderId());
        assertEquals(1,orderService.getAllPaidOrders().size());
        assertEquals(createOrderResponse1.getTableNumber(),orderService.getAllPaidOrders().get(0).getTableNumber());
    }



}