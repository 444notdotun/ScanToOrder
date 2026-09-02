package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.dtos.request.CreateSessionRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateSessionResponse;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
public class SessionController {
    

    
    
    @GetMapping("/me/references")
    public ResponseEntity<ApiResponse<java.util.List<String>>> getMyPaymentReferences(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        if (principal == null || principal.getSessionId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<String> references = diningSessionService.getSessionPaymentReferences(principal.getSessionId());
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(references));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getCurrentSession(

            @AuthenticationPrincipal CustomerPrincipal principal) {
        java.util.Map<String, String> response = new java.util.HashMap<>();
        if (principal != null) {
            response.put("sessionId", principal.getSessionId());
            response.put("tableId", principal.getTableId());
            response.put("seatId", principal.getSeatId());
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @Autowired
    private DiningSessionService diningSessionService;
    @GetMapping("/active/count")
    public ResponseEntity<ApiResponse<Long>> getActiveSessionsCount() {
        long count = diningSessionService.getActiveSessionsCount();
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(count));
    }




    @PostMapping
    public ResponseEntity<ApiResponse<CreateSessionResponse>> createSession( @RequestBody @Valid CreateSessionRequest request) {
        CreateSessionResponse response = diningSessionService.createSession(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(response));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CloseSessionResponse>> closeSession(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String id,
            @AuthenticationPrincipal Object principal) {

        if (principal instanceof CustomerPrincipal customer) {
            CloseSessionResponse response = diningSessionService.closeSession(customer.getSessionId());
            
            ResponseCookie jwtCookie = ResponseCookie.from("access_token", "")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("None")
                    .path("/")
                    .maxAge(0)
                    .build();
                    
            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                    .body(new ApiResponse<>(response));
        } else {
            CloseSessionResponse response = diningSessionService.closeSessionByTableId(id);
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
        }
    }
}
