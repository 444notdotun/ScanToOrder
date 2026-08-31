package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.exception.ItemNotFound;
import com.scantoorder.scantoorder.exception.OrderNotFoundException;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import com.scantoorder.scantoorder.service.Interface.OrderItemService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderItemImplementation implements OrderItemService {
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ItemService itemService;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private ModelMapper modelMapper;

    @Override
    public OrderItem createOrderItem(String orderId ,CreateOrderItemRequest createOrderItemRequest) {
        Order order = orderRepo.findById(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        Item item = itemRepo.findItemsByItemName(createOrderItemRequest.getItemName()).orElseThrow(() -> new ItemNotFound("Item not found"));
        OrderItem orderItem = modelMapper.map(createOrderItemRequest, OrderItem.class);
        orderItem.setOrder(order);
        orderItem.setItem(item);
        orderItem.setOrderItemId(null);
        orderItem.setTotalPrice(calculateTotalPrice(createOrderItemRequest.getQuantity(), item.getItemPrice()));
        orderItem.setUnitPrice(item.getItemPrice());
        orderItemRepo.save(orderItem);
        return orderItem;
    }

    private BigDecimal calculateTotalPrice(int quantity, BigDecimal price) {
        return price.multiply(new BigDecimal(quantity));
    }
}

