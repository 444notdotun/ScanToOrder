package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.UpdateSeatResponse;
import com.scantoorder.scantoorder.service.Interface.SeatService;
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

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@Validated
public class SeatController {

    @Autowired
    private SeatService seatService;

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<SeatClaimedResponse>> claimSeat(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody ClaimSeatRequest claimSeatRequest) {
        SeatClaimedResponse response = seatService.claimSeat(claimSeatRequest, asCustomer(principal));
        return seatedResponse(response);
    }

    @PostMapping("/{seatId}/join")
    public ResponseEntity<ApiResponse<SeatClaimedResponse>> joinSeat(
            @AuthenticationPrincipal Object principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String seatId,
            @Valid @RequestBody JoinSeatRequest joinSeatRequest) {
        SeatClaimedResponse response = seatService.joinSeat(seatId, joinSeatRequest, asCustomer(principal));
        return seatedResponse(response);
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PatchMapping("/update")
    public ResponseEntity<ApiResponse<UpdateSeatResponse>> updateSeat(@Valid @RequestBody UpdateSeatRequest updateSeatRequest) {
        UpdateSeatResponse response = seatService.updateSeat(CurrentTenant.id(), updateSeatRequest);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<SeatStatusResponse>>> viewAllSeatStatus() {
        List<SeatStatusResponse> response = seatService.viewAllSeatStatus(CurrentTenant.id());
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<ReleaseSeatResponse>> releaseSeat(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String id) {
        ReleaseSeatResponse response = seatService.releaseSeat(CurrentTenant.id(), id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    private CustomerPrincipal asCustomer(Object principal) {
        return principal instanceof CustomerPrincipal customer ? customer : null;
    }

    // A new seat comes with a new token cookie. A guest sent back to the seat they already have keeps theirs.
    private ResponseEntity<ApiResponse<SeatClaimedResponse>> seatedResponse(SeatClaimedResponse response) {
        if (response.getToken() == null) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
        }
        ResponseCookie jwtCookie = ResponseCookie.from("access_token", response.getToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofHours(4))
                .build();
        response.setToken(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new ApiResponse<>(response));
    }
}
