package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.AccessCookie;
import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.request.HoldSeatsRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.LeaveSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.SessionViewResponse;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.dtos.respond.SessionOrdersResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
    private AccessCookie accessCookie;
    @Autowired
    private DiningSessionService diningSessionService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me/references")
    public ResponseEntity<ApiResponse<List<String>>> getMyPaymentReferences(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        List<String> references = diningSessionService.getSessionPaymentReferences(principal);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(references));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me/orders")
    public ResponseEntity<ApiResponse<SessionOrdersResponse>> getMyOrders(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(diningSessionService.getSessionOrders(principal)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SessionViewResponse>> getCurrentSession(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(diningSessionService.getMySession(principal)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/me/holds")
    public ResponseEntity<ApiResponse<SessionViewResponse>> holdSeats(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Valid @RequestBody HoldSeatsRequest request) {
        SessionViewResponse response = diningSessionService.holdSeats(principal, request.getSeatIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/me/holds/{seatId}")
    public ResponseEntity<ApiResponse<SessionViewResponse>> releaseHold(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String seatId) {
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(diningSessionService.releaseHold(principal, seatId)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/me/guests")
    public ResponseEntity<ApiResponse<SessionViewResponse>> confirmGuest(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Valid @RequestBody ConfirmGuestRequest request) {
        SessionViewResponse response = diningSessionService.confirmGuest(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/me/leave")
    public ResponseEntity<ApiResponse<LeaveSessionResponse>> leave(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        LeaveSessionResponse response = diningSessionService.leave(principal);
        ResponseCookie expiredCookie = accessCookie.clear();
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/active/count")
    public ResponseEntity<ApiResponse<Long>> getActiveSessionsCount() {
        long count = diningSessionService.getActiveSessionsCount(CurrentTenant.id());
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(count));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CloseSessionResponse>> closeSession(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String id) {
        CloseSessionResponse response = diningSessionService.closeSession(CurrentTenant.id(), id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
