package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderItemResponse;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.OrderItemResponse;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.OrderItemService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderImplementation implements OrderService {
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Transactional
    @Override
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest,String seatId, String tableId,String sessionId) {
        Order order = CreateOrder(seatId, tableId, sessionId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        CreateOrderResponse response = new CreateOrderResponse();
       for(CreateOrderItemRequest request:createOrderRequest.getOrderItems()){
          OrderItem orderItem= orderItemService.createOrderItem(order.getOrderId(),request);
          totalAmount=totalAmount.add(orderItem.getTotalPrice());
           OrderItemResponse orderItemResponse =  new OrderItemResponse();
           orderItemResponse.setItemName(request.getItemName());
           orderItemResponse.setQuantity(request.getQuantity());
          response.getItems().add(orderItemResponse);
       }
        order.setTotalAmount(totalAmount);
        orderRepo.save(order);
        return getCreateOrderResponse(response, totalAmount, order);
    }

    @Override
    public Order updateOrder(String newState, String orderId) {
        Order order = orderRepo.findById(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if(!isStateAvailable(newState,order.getOrderStatus().toString())) throw  new OrderStatusException("orderStatus is not allowed");
        order.setOrderStatus(OrderStatus.valueOf(newState));
        return orderRepo.save(order);
    }

    @Override
    public String checkOrderStatus(String orderId) {
        Order order = orderRepo.findById(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        return order.getOrderStatus().toString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getAllPaidOrders() {
        List<OrderItem> orderItems = orderItemRepo.findAllByOrderStatusWithOrder(OrderStatus.PAID);
        if (orderItems.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Order, List<OrderItem>> groupedByOrder = orderItems.stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getOrder,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<KitchenOrderResponse> responses = new ArrayList<>();

        for (Map.Entry<Order, List<OrderItem>> entry : groupedByOrder.entrySet()) {
            Order order = entry.getKey();
            KitchenOrderResponse orderResponse = getKitchenOrderResponse(entry, order);
            responses.add(orderResponse);
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getKitchenOrders(String status) {
        List<OrderStatus> statuses;
        if (status != null && !status.trim().isEmpty()) {
            statuses = List.of(OrderStatus.valueOf(status.toUpperCase()));
        } else {
            statuses = List.of(OrderStatus.PAID, OrderStatus.PREPARING);
        }

        List<OrderItem> orderItems = orderItemRepo.findAllByOrderStatusesWithOrder(statuses);
        if (orderItems.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Order, List<OrderItem>> groupedByOrder = orderItems.stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getOrder,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<KitchenOrderResponse> responses = new ArrayList<>();
        for (Map.Entry<Order, List<OrderItem>> entry : groupedByOrder.entrySet()) {
            Order order = entry.getKey();
            KitchenOrderResponse orderResponse = getKitchenOrderResponse(entry, order);
            responses.add(orderResponse);
        }

        return responses;
    }

    private static  KitchenOrderResponse getKitchenOrderResponse(Map.Entry<Order, List<OrderItem>> entry, Order order) {
        List<OrderItem> items = entry.getValue();

        KitchenOrderResponse orderResponse = new KitchenOrderResponse();
        orderResponse.setOrderId(order.getOrderId());
        orderResponse.setCreatedAt(order.getCreatedAt());
        if (order.getOrderStatus() != null) {
            orderResponse.setStatus(order.getOrderStatus().name());
        }
        orderResponse.setTotalAmount(order.getTotalAmount());

        if (order.getTable() != null) {
            orderResponse.setTableNumber(order.getTable().getTableNumber());
        }
        if (order.getSeat() != null) {
            orderResponse.setSeatId(String.valueOf(order.getSeat().getSeatId()));
            orderResponse.setSeatNumber(order.getSeat().getSeatNumber());
        }

        List<KitchenOrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : items) {
            KitchenOrderItemResponse itemDto = new KitchenOrderItemResponse();
            itemDto.setQuantity(item.getQuantity());
            itemDto.setSpecialInstructions(item.getSpecialInstructions());
            if (item.getItem() != null) {
                itemDto.setItemName(item.getItem().getItemName());
            }
            itemResponses.add(itemDto);
        }

        orderResponse.setItems(itemResponses);
        return orderResponse;
    }

    private boolean isStateAvailable(String state,String oldCase) {
        return switch (oldCase) {
            case "PENDING_PAYMENT" -> state.equals("PAID") || state.equals("CANCELED");
            case "PAID" -> state.equals("PREPARING");
            case "PREPARING" -> state.equals("READY");
            case "READY" -> state.equals("DELIVERED");
            default -> false;
        };

    }

    private  CreateOrderResponse getCreateOrderResponse(CreateOrderResponse response, BigDecimal totalAmount, Order order) {
        response.setTotalPrice(totalAmount);
        response.setOrderId(order.getOrderId());
        response.setTableNumber(order.getTable().getTableNumber());
        response.setSeatNumber(order.getSeat().getSeatNumber());
        response.setOrderStatus(order.getOrderStatus());
        return response;
    }

    private  Order CreateOrder(String seatId, String tableId, String sessionId) {
        Order order = new Order();
        order.setSeat(getSeat(seatId));
        order.setTable(getTable(tableId));
        order.setDinningSession(getSession(sessionId));
        orderRepo.save(order);
        return order;
    }

    private  RestaurantTable getTable(String tableId) {
        return tableRepo.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found"));
    }

    private  Seat getSeat(String seatId) {
        return seatRepo.findSeatBySeatId(seatId)
                .orElseThrow(() -> new SeatNotFoundException("Seat not found"));
    }

    private  DinningSession getSession(String sessionId) {
        return dinningSessionRepo.findDinningSessionBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found"));
    }
}