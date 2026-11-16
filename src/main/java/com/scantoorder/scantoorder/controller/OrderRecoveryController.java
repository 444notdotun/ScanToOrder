package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.request.SwapRequest;
import com.scantoorder.scantoorder.dtos.request.UnavailableRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.OrderActionResponse;
import com.scantoorder.scantoorder.service.Interface.OrderRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class OrderRecoveryController {

    private static final String ID = "^[A-Za-z0-9_-]+$";

    @Autowired
    private OrderRecoveryService recoveryService;

    @PreAuthorize("hasAnyRole('CHEF', 'MANAGER')")
    @PostMapping("/orders/{id}/unavailable")
    public ResponseEntity<ApiResponse<OrderActionResponse>> unavailable(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @Valid @RequestBody UnavailableRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(recoveryService.markUnavailable(CurrentTenant.id(), id, request.getReason())));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/orders/{id}/swap")
    public ResponseEntity<ApiResponse<OrderActionResponse>> swap(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @Valid @RequestBody SwapRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(recoveryService.swap(CurrentTenant.id(), id, request)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/orders/{id}/refund-request")
    public ResponseEntity<ApiResponse<OrderActionResponse>> refundRequest(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(recoveryService.requestRefund(CurrentTenant.id(), id)));
    }
}
