package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import org.springframework.security.access.prepost.PreAuthorize;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.ServiceCall;
import com.scantoorder.scantoorder.dtos.request.CreateServiceCallRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.Interface.ServiceCallService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/service-calls")
@Validated
public class ServiceCallController {

    @Autowired
    private ServiceCallService serviceCallService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> createServiceCall(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @RequestBody @Valid CreateServiceCallRequest request) {
        ServiceCall call = serviceCallService.createServiceCall(request, principal.getTenantId(), principal.getSessionId());
        
        ApiResponse<String> response = new ApiResponse<>(call.getServiceCallId());
        response.setMessage("Service call created successfully");
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<ServiceCall>>> getAllServiceCalls() {
        return ResponseEntity.ok(new ApiResponse<>(serviceCallService.getAllServiceCalls(CurrentTenant.id())));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCall>> updateServiceCallStatus(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        ServiceCall call = serviceCallService.updateServiceCallStatus(CurrentTenant.id(), id, payload.get("status"), payload.get("waiterId"));
        return ResponseEntity.ok(new ApiResponse<>(call));
    }
}
