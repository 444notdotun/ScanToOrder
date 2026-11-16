package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
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
    @Autowired
    private com.scantoorder.scantoorder.service.ServiceBoardService board;

    @PreAuthorize("hasAnyRole('CHEF', 'WAITER', 'MANAGER')")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<? extends Object>> getOrders(
            @RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            // The kitchen's own list. A waiter reaches the lists they used before by naming a status.
            if (isWaiter()) {
                throw new org.springframework.security.access.AccessDeniedException("This is not available to your account");
            }
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(board.kitchen(CurrentTenant.id())));
        }
        List<KitchenOrderResponse> response = orderService.getKitchenOrders(CurrentTenant.id(), status);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    private boolean isWaiter() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof com.scantoorder.scantoorder.data.model.WorkerPrincipal worker && "WAITER".equals(worker.getRole());
    }

    @PreAuthorize("hasAnyRole('CHEF', 'WAITER', 'MANAGER')")
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(
            @PathVariable String id,
            @RequestParam String status) {
        Order response = orderService.updateOrder(CurrentTenant.id(), status, id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
