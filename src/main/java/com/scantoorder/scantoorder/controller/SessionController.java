package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
public class SessionController {

    @Autowired
    private DiningSessionService diningSessionService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me/references")
    public ResponseEntity<ApiResponse<List<String>>> getMyPaymentReferences(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        List<String> references = diningSessionService.getSessionPaymentReferences(principal);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(references));
    }

    @GetMapping("/active/count")
    public ResponseEntity<ApiResponse<Long>> getActiveSessionsCount() {
        long count = diningSessionService.getActiveSessionsCount();
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(count));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CloseSessionResponse>> closeSession(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String id) {
        CloseSessionResponse response = diningSessionService.closeSession(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
