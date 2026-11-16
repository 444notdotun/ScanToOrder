package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.dtos.request.PayoutAccountRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant")
@PreAuthorize("hasRole('MANAGER')")
@Validated
public class TenantController {

    @Autowired
    private TenantWorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<ApiResponse<MyTenantResponse>> myTenant() {
        return ResponseEntity.ok(new ApiResponse<>(workspaceService.getMyTenant(CurrentTenant.id())));
    }

    @PatchMapping("/open-for-orders")
    public ResponseEntity<ApiResponse<MyTenantResponse>> openForOrders(@NotNull @RequestBody Boolean open) {
        return ResponseEntity.ok(new ApiResponse<>(workspaceService.setOpenForOrders(CurrentTenant.id(), open)));
    }

    @PutMapping("/payout-account")
    public ResponseEntity<ApiResponse<MyTenantResponse>> payoutAccount(
            @AuthenticationPrincipal WorkerPrincipal caller,
            @Valid @RequestBody PayoutAccountRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(workspaceService.changePayoutAccount(CurrentTenant.id(), caller.getWorkerId(), request)));
    }

    @PatchMapping("/holds")
    public ResponseEntity<ApiResponse<MyTenantResponse>> holds(@NotNull @RequestBody Boolean enabled) {
        return ResponseEntity.ok(new ApiResponse<>(workspaceService.setHolds(CurrentTenant.id(), enabled)));
    }
}
