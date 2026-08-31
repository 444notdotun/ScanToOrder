package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.service.Interface.OrderItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class OrderItemServiceTest {

    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private OrderRepo orderRepo;
    private CreateOrderItemRequest createOrderItemRequest;

    @Autowired
    private OrderItemRepo orderItemRepo;
    Order order;
    Item item;
    @Autowired
    private ItemRepo itemRepo;
    @AfterEach
    void tearDown() {
        orderItemRepo.deleteAll();
        itemRepo.deleteAll();
        orderRepo.deleteAll();


    }

    @BeforeEach
    void setUp() {
        orderItemRepo.deleteAll();
        itemRepo.deleteAll();
        orderRepo.deleteAll();


        order = new Order();
        orderRepo.save(order);
        item = new Item();
        item.setItemPrice(BigDecimal.valueOf(500));
        item.setItemName("amala");
        itemRepo.save(item);


        orderItemRepo.deleteAll();
        createOrderItemRequest = new CreateOrderItemRequest();
        createOrderItemRequest.setItemName(item.getItemName());
        createOrderItemRequest.setQuantity(3);
        createOrderItemRequest.setSpecialInstructions("no extra spicy");

    }





    @Test
    void testThatOrderItemCanBeCreated(){
        OrderItem orderItem =orderItemService.createOrderItem(order.getOrderId(),createOrderItemRequest);
        assertNotNull(orderItem);
        assertEquals(createOrderItemRequest.getItemName(),orderItem.getItem().getItemName());
        assertEquals(1,orderItemRepo.count());
    }
}