package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
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
    private OrderItemService orderItemService;
    @Autowired
    private OrderStepService orderStepService;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private DiningSessionService diningSessionService;
    @Transactional
    @Override
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest, CustomerPrincipal caller) {
        if (createOrderRequest.getOrderItems() == null || createOrderRequest.getOrderItems().isEmpty()) {
            throw new InvalidRequestException("Your order is empty. Add something from the menu first.");
        }
        SessionSeat target = diningSessionService.seatToOrderFor(caller, createOrderRequest.getSeatId());
        Order order = new Order();
        order.setTenant(target.getTenant());
        order.setSeat(target.getSeat());
        order.setTable(target.getSeat().getTableId());
        order.setDinningSession(target.getSession());
        order.setSessionSeat(target);
        orderRepo.save(order);
        BigDecimal totalAmount = BigDecimal.ZERO;
        CreateOrderResponse response = new CreateOrderResponse();
       for(CreateOrderItemRequest request:createOrderRequest.getOrderItems()){
          OrderItem orderItem= orderItemService.createOrderItem(order.getTenant().getTenantId(), order.getOrderId(), request);
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
    public Order updateOrder(String tenantId, String newState, String orderId) {
        // The same rules as the step buttons: locked, forward only, first change wins.
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() == null
                ? null : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String workerId = principal instanceof WorkerPrincipal worker ? worker.getWorkerId() : null;
        return orderStepService.moveTo(tenantId, workerId, orderId, newState);
    }

    @Override
    public String checkOrderStatus(String tenantId, String orderId) {
        Order order = orderRepo.findByOrderIdAndTenant_TenantId(orderId, tenantId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        return order.getOrderStatus().toString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getAllPaidOrders(String tenantId) {
        List<OrderItem> orderItems = orderItemRepo.findAllByOrderStatusWithOrder(OrderStatus.PAID, tenantId);
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
    public List<KitchenOrderResponse> getKitchenOrders(String tenantId, String status) {
        List<OrderStatus> statuses;
        if (status != null && !status.trim().isEmpty()) {
            OrderStatus asked = OrderStatus.valueOf(status.toUpperCase());
            // The waiter's list: orders the kitchen gave up on are waiting for a waiter too.
            statuses = asked == OrderStatus.READY
                    ? List.of(OrderStatus.READY, OrderStatus.UNAVAILABLE, OrderStatus.REFUND_DUE)
                    : List.of(asked);
        } else {
            statuses = List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.UNAVAILABLE);
        }

        List<OrderItem> orderItems = orderItemRepo.findAllByOrderStatusesWithOrder(statuses, tenantId);
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
        // A swapped order is a new ticket: it takes its place in the queue from the swap.
        orderResponse.setCreatedAt(order.getSwappedAt() != null ? order.getSwappedAt() : order.getCreatedAt());
        if (order.getUnavailableReason() != null && order.getOrderStatus() != null
                && (order.getOrderStatus() == OrderStatus.UNAVAILABLE || order.getOrderStatus() == OrderStatus.REFUND_DUE)) {
            orderResponse.setUnavailableReason(order.getUnavailableReason().name());
        }
        orderResponse.setNote(order.getNote());
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
            } else if (item.getPlate() != null) {
                itemDto.setItemName(item.getPlate().getName());
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
}
