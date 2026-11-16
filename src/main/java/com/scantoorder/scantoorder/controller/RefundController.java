package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.dtos.request.GuestBankDetailsRequest;
import com.scantoorder.scantoorder.dtos.request.GuestEmailRequest;
import com.scantoorder.scantoorder.dtos.request.SettleRefundRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.GuestRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.ManagerRefundResponse;
import com.scantoorder.scantoorder.service.Interface.RefundTrackingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/refunds")
@Validated
public class RefundController {

    private static final String ID = "^[A-Za-z0-9_-]+$";

    @Autowired
    private RefundTrackingService refundService;

    // ---- the guest's link: the token in it is the only access ----

    @GetMapping("/{id}/guest")
    public ResponseEntity<ApiResponse<GuestRefundResponse>> guestView(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @RequestParam("t") @Size(max = 100) String token) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.guestView(id, token)));
    }

    @PostMapping("/{id}/guest/email")
    public ResponseEntity<ApiResponse<GuestRefundResponse>> guestEmail(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @RequestParam("t") @Size(max = 100) String token,
            @Valid @RequestBody GuestEmailRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.guestEmail(id, token, request)));
    }

    @PostMapping("/{id}/guest/bank-details")
    public ResponseEntity<ApiResponse<GuestRefundResponse>> guestBankDetails(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @RequestParam("t") @Size(max = 100) String token,
            @Valid @RequestBody GuestBankDetailsRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.guestBankDetails(id, token, request)));
    }

    @PostMapping("/{id}/guest/received")
    public ResponseEntity<ApiResponse<GuestRefundResponse>> guestReceived(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @RequestParam("t") @Size(max = 100) String token) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.guestReceived(id, token)));
    }

    @PostMapping("/{id}/guest/not-received")
    public ResponseEntity<ApiResponse<GuestRefundResponse>> guestNotReceived(
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @RequestParam("t") @Size(max = 100) String token) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.guestNotReceived(id, token)));
    }

    // ---- the manager's list ----

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ManagerRefundResponse>>> list(
            @RequestParam(defaultValue = "open") String state) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.list(CurrentTenant.id(), state)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/settle")
    public ResponseEntity<ApiResponse<ManagerRefundResponse>> settle(
            @AuthenticationPrincipal WorkerPrincipal caller,
            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
            @Valid @RequestBody SettleRefundRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(refundService.settle(CurrentTenant.id(), caller.getWorkerId(), id, request)));
    }
}
