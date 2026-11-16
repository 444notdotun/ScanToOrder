package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.DeclineTenantRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.TenantActionResponse;
import com.scantoorder.scantoorder.dtos.respond.TenantSummaryResponse;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Validated
public class PlatformController {

    private static final String ID = "^[A-Za-z0-9_-]+$";

    @Autowired
    private PlatformService platformService;

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<TenantSummaryResponse>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(new ApiResponse<>(platformService.listTenants(status)));
    }

    @PostMapping("/tenants/{id}/approve")
    public ResponseEntity<ApiResponse<TenantActionResponse>> approve(@PathVariable @Pattern(regexp = ID) String id) {
        return ResponseEntity.ok(new ApiResponse<>(platformService.approve(id)));
    }

    @PostMapping("/tenants/{id}/decline")
    public ResponseEntity<ApiResponse<TenantActionResponse>> decline(@PathVariable @Pattern(regexp = ID) String id,
                                                                     @Valid @RequestBody DeclineTenantRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(platformService.decline(id, request.getReason())));
    }

    @PostMapping("/tenants/{id}/suspend")
    public ResponseEntity<ApiResponse<TenantActionResponse>> suspend(@PathVariable @Pattern(regexp = ID) String id) {
        return ResponseEntity.ok(new ApiResponse<>(platformService.suspend(id)));
    }

    @PostMapping("/tenants/{id}/reinstate")
    public ResponseEntity<ApiResponse<TenantActionResponse>> reinstate(@PathVariable @Pattern(regexp = ID) String id) {
        return ResponseEntity.ok(new ApiResponse<>(platformService.reinstate(id)));
    }

    @PostMapping("/tenants/{id}/resend-sign-in")
    public ResponseEntity<ApiResponse<TenantActionResponse>> resendSignIn(@PathVariable @Pattern(regexp = ID) String id) {
        return ResponseEntity.ok(new ApiResponse<>(platformService.resendSignIn(id)));
    }
}
