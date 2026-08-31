package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.exception.OrderStatusException;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class OrderServiceTest {
    private CreateOrderRequest createOrderRequest;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private OrderService orderService;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo  seatRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private OrderRepo orderRepo;
    private Seat seat;
    private RestaurantTable table;
    private DinningSession session;

    @BeforeEach
    void setUp() {
        table = new RestaurantTable();
        table.setCapacity(2);
        tableRepo.save(table);
        seat = new Seat();
        seatRepo.save(seat);
        Item item = new  Item();
        item.setItemName("itemName");
        item.setItemPrice(BigDecimal.valueOf(5000));
        itemRepo.save(item);
        Item item1 = new  Item();
        item1.setItemName("itemName1");
        item1.setItemPrice(BigDecimal.valueOf(51000));
        itemRepo.save(item1);
        session = new DinningSession();
        session.setCustomerPhone("08149048149");
        dinningSessionRepo.save(session);
        CreateOrderItemRequest createOrderItemRequest = new CreateOrderItemRequest();
        createOrderItemRequest.setItemName("itemName");
        createOrderItemRequest.setQuantity(5);
        CreateOrderItemRequest createOrderItemRequest1 = new CreateOrderItemRequest();
        createOrderItemRequest1.setItemName("itemName1");
        createOrderItemRequest1.setQuantity(5);
        createOrderRequest=new CreateOrderRequest();
        createOrderRequest.getOrderItems().add(createOrderItemRequest1);
        createOrderRequest.getOrderItems().add(createOrderItemRequest);
    }
    @Transactional
    @Test
    void testThatOrderCanBeCreated() {
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        assertEquals(OrderStatus.PENDING_PAYMENT, createOrderResponse.getOrderStatus());
        assertNotNull(orderRepo.findOrderByOrderId(createOrderResponse.getOrderId()));
    }
    @Transactional
    @Test
    void testThatOrderCanBeCanceled() {
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
       Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        String newState = "CANCELED";
        System.out.println(order.getOrderStatus());
        Order updatedOrder = orderService.updateOrder(newState, order.getOrderId());
        assertEquals(OrderStatus.CANCELED, updatedOrder.getOrderStatus());
    }

    @Transactional
    @Test
    void testThatOrderCanBeCompletedPreparing(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        String newState = "PREPARING";
        Order updatedOrder = orderService.updateOrder(newState, createOrderResponse.getOrderId());
        assertEquals(OrderStatus.PREPARING, updatedOrder.getOrderStatus());
    }

    @Transactional
    @Test
    void testThatOrderCanBeCompletedAsReady(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.PREPARING);
        orderRepo.save(order);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        String newState = "READY";
        Order updatedOrder = orderService.updateOrder(newState, createOrderResponse.getOrderId());
        assertEquals(OrderStatus.READY, updatedOrder.getOrderStatus());
    }
    @Transactional
    @Test
    void testThatOrderCanBeCompletedAsDelivered(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.READY);
        orderRepo.save(order);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        String newState = "DELIVERED";
        Order updatedOrder = orderService.updateOrder(newState, createOrderResponse.getOrderId());
        assertEquals(OrderStatus.DELIVERED, updatedOrder.getOrderStatus());
    }

    @Transactional
    @Test
    void testThatExceptionIsThrownWhenOrderStatusIsInvalid(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        String newState = "PREPARING";
        assertThrows(OrderStatusException.class,()->orderService.updateOrder(newState, createOrderResponse.getOrderId()));
    }

    @Transactional
    @Test
    void testThatStatusOfAnOrderCheck(){
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        assertEquals(OrderStatus.PENDING_PAYMENT, createOrderResponse.getOrderStatus());
        Order order = orderRepo.findOrderByOrderId(createOrderResponse.getOrderId());
        order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);
        assertEquals(OrderStatus.PAID.toString(),orderService.checkOrderStatus(createOrderResponse.getOrderId()));
    }

    @Transactional
    @Test
    void testThatPaidOrdersCanBeSeen(){

        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse);
        assertEquals(BigDecimal.valueOf(280000),createOrderResponse.getTotalPrice());
        assertEquals(OrderStatus.PENDING_PAYMENT, createOrderResponse.getOrderStatus());
        CreateOrderResponse createOrderResponse1 = orderService.createOrder(createOrderRequest,seat.getSeatId(),table.getTableId(),session.getSessionId());
        assertNotNull(createOrderResponse1);
        orderService.updateOrder("PAID",createOrderResponse1.getOrderId());
        assertEquals(1,orderService.getAllPaidOrders().size());
        assertEquals(createOrderResponse1.getTableNumber(),orderService.getAllPaidOrders().get(0).getTableNumber());
    }



}