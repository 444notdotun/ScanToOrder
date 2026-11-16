package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.Interface.OrderItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/items")
@Validated
public class OrderItemController {

    @Autowired
    private OrderItemService orderItemService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderItem>> createOrderItem(
            @AuthenticationPrincipal CustomerPrincipal caller,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String orderId,
            @Valid @RequestBody CreateOrderItemRequest request) {
        OrderItem response = orderItemService.addItemToMyOrder(caller, orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(response));
    }
}
