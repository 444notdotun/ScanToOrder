package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderResponse;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")

public class OrderController {

    @Autowired
    private OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(@AuthenticationPrincipal CustomerPrincipal customerPrincipal, @RequestBody @Valid CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(orderService.createOrder(request,customerPrincipal.getSeatId(), customerPrincipal.getTableId(), customerPrincipal.getSessionId())));
    }

    @PatchMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> updateOrder(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String orderId,
            @RequestParam String status) {
        Order response = orderService.updateOrder(status, orderId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'WAITER', 'CHEF', 'MANAGER')")
    @GetMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<String>> checkOrderStatus(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String orderId) {
        String response = orderService.checkOrderStatus(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @GetMapping("/paid")
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> getAllPaidOrders() {
        List<KitchenOrderResponse> response = orderService.getAllPaidOrders();
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
