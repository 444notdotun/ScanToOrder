package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;

public interface OrderItemService {


    OrderItem createOrderItem(String tenantId, String orderId, CreateOrderItemRequest createOrderItemRequest);

    OrderItem addItemToMyOrder(CustomerPrincipal caller, String orderId, CreateOrderItemRequest request);
}
