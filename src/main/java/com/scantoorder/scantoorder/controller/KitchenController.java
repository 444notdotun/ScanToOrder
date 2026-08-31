package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderResponse;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> getOrders(
            @RequestParam(required = false) String status) {
        List<KitchenOrderResponse> response = orderService.getKitchenOrders(status);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'WAITER', 'MANAGER')")
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(
            @PathVariable String id,
            @RequestParam String status) {
        Order response = orderService.updateOrder(status, id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
