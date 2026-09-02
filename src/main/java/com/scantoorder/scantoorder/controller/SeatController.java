package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import java.time.Duration;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@Validated
public class SeatController {

    @Autowired
    private SeatService seatService;

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<SeatClaimedResponse>> claimSeat(@Valid @RequestBody ClaimSeatRequest claimSeatRequest) {
        SeatClaimedResponse response = seatService.claimSeat(claimSeatRequest);
        
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

    @PatchMapping("/update")
    public ResponseEntity<ApiResponse<UpdateSeatResponse>> updateSeat(@Valid @RequestBody UpdateSeatRequest updateSeatRequest) {
        UpdateSeatResponse response = seatService.updateSeat(updateSeatRequest);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<SeatStatusResponse>>> viewAllSeatStatus() {
        List<SeatStatusResponse> response = seatService.viewAllSeatStatus();
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<ReleaseSeatResponse>> releaseSeat(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String id) {
        ReleaseSeatResponse response = seatService.releaseSeat(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @Autowired
    private com.scantoorder.scantoorder.service.Interface.DiningSessionService diningSessionService;

    @PostMapping("/{seatNumber}/close-session")
    public ResponseEntity<ApiResponse<CloseSessionResponse>> closeSessionBySeat(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier") String seatNumber) {
        CloseSessionResponse response = diningSessionService.closeSessionBySeatNumber(seatNumber);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
