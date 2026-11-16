package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import jakarta.validation.constraints.NotNull;
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

    @PatchMapping("/holds")
    public ResponseEntity<ApiResponse<MyTenantResponse>> holds(@NotNull @RequestBody Boolean enabled) {
        return ResponseEntity.ok(new ApiResponse<>(workspaceService.setHolds(CurrentTenant.id(), enabled)));
    }
}
