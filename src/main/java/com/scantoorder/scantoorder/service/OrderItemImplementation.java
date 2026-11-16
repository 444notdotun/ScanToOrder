package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
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
    public OrderItem createOrderItem(String tenantId, String orderId, CreateOrderItemRequest createOrderItemRequest) {
        Order order = orderRepo.findByOrderIdAndTenant_TenantId(orderId, tenantId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        Item item = itemRepo.findByTenant_TenantIdAndItemName(order.getTenant().getTenantId(), createOrderItemRequest.getItemName())
                .orElseThrow(() -> new ItemNotFound("We cannot find " + createOrderItemRequest.getItemName() + " on this menu"));
        if (!item.isAvailable()) {
            throw new ConflictException(item.getItemName() + " is out of stock. Take it off your order and try again.");
        }
        if (createOrderItemRequest.getQuantity() < 1) {
            throw new InvalidRequestException("Choose at least one " + item.getItemName());
        }
        OrderItem orderItem = modelMapper.map(createOrderItemRequest, OrderItem.class);
        orderItem.setOrder(order);
        orderItem.setTenant(order.getTenant());
        orderItem.setItem(item);
        orderItem.setOrderItemId(null);
        orderItem.setTotalPrice(calculateTotalPrice(createOrderItemRequest.getQuantity(), item.getItemPrice()));
        orderItem.setUnitPrice(item.getItemPrice());
        orderItemRepo.save(orderItem);
        return orderItem;
    }

    // A guest can add to an order of their own session while it is still unpaid, never to anyone else's.
    @org.springframework.transaction.annotation.Transactional
    @Override
    public OrderItem addItemToMyOrder(CustomerPrincipal caller, String orderId, CreateOrderItemRequest request) {
        Order order = orderRepo.findByOrderIdAndTenant_TenantId(orderId, caller.getTenantId())
                .filter(found -> found.getDinningSession() != null
                        && found.getDinningSession().getSessionId().equals(caller.getSessionId()))
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if (order.getOrderStatus() != com.scantoorder.scantoorder.data.model.OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("This order has already been paid for. Start a new order for anything extra.");
        }
        OrderItem orderItem = createOrderItem(caller.getTenantId(), orderId, request);
        BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        order.setTotalAmount(total.add(orderItem.getTotalPrice()));
        orderRepo.save(order);
        return orderItem;
    }

    private BigDecimal calculateTotalPrice(int quantity, BigDecimal price) {
        return price.multiply(new BigDecimal(quantity));
    }
}

