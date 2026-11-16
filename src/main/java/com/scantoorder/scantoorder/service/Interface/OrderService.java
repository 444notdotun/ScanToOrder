package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderResponse;

import java.util.List;

public interface OrderService {
    CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest, CustomerPrincipal caller);
    Order updateOrder(String tenantId, String newState, String orderId);
    String checkOrderStatus(String tenantId, String orderId);
    List<KitchenOrderResponse> getAllPaidOrders(String tenantId);
    List<KitchenOrderResponse> getKitchenOrders(String tenantId, String status);
}
