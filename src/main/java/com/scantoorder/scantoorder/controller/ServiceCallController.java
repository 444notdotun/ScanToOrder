package com.scantoorder.scantoorder.controller;

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

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createServiceCall(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @RequestBody @Valid CreateServiceCallRequest request) {
        ServiceCall call = serviceCallService.createServiceCall(request, principal.getSessionId());
        
        ApiResponse<String> response = new ApiResponse<>(call.getServiceCallId());
        response.setMessage("Service call created successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<ServiceCall>>> getAllServiceCalls() {
        return ResponseEntity.ok(new ApiResponse<>(serviceCallService.getAllServiceCalls()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCall>> updateServiceCallStatus(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        ServiceCall call = serviceCallService.updateServiceCallStatus(id, payload.get("status"), payload.get("waiterId"));
        return ResponseEntity.ok(new ApiResponse<>(call));
    }
}
