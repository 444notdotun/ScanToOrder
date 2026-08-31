package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderResponse;

import java.util.List;

public interface OrderService {
    CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest, String seatId, String sessionId, String tableId);
    Order updateOrder(String newState, String orderId);
    String checkOrderStatus(String orderId);
    List<KitchenOrderResponse> getAllPaidOrders();
    List<KitchenOrderResponse> getKitchenOrders(String status);
}
