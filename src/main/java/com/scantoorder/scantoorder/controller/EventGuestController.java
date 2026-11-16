package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.dtos.request.EventCodeRequest;
import com.scantoorder.scantoorder.dtos.request.EventOrderRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.EventOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.MyEventResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.service.EventOrderService;
import com.scantoorder.scantoorder.service.EventSeatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/**
 * What a guest does at an event: type their code at their table, then tap plates.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class EventGuestController {

    @Autowired
    private EventSeatingService seatingService;
    @Autowired
    private EventOrderService orderService;

    // Public: the guest has no seat yet. The code is what proves who they are.
    @PostMapping("/t/{slug}/e/{eventCode}/tables/{displayNumber}/code")
    public ResponseEntity<ApiResponse<SeatClaimedResponse>> redeem(
            @AuthenticationPrincipal Object principal,
            @PathVariable @Pattern(regexp = "^[a-z0-9]+$", message = "Invalid identifier format") String slug,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "Invalid identifier format") String eventCode,
            @PathVariable @Min(1) int displayNumber,
            @Valid @RequestBody EventCodeRequest request) {
        SeatClaimedResponse response = seatingService.redeem(slug, eventCode, displayNumber, request.getCode(),
                principal instanceof CustomerPrincipal customer ? customer : null);
        if (response.getToken() == null) {
            return ResponseEntity.ok(new ApiResponse<>(response));
        }
        ResponseCookie jwtCookie = ResponseCookie.from("access_token", response.getToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofHours(8))
                .build();
        response.setToken(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me/event")
    public ResponseEntity<ApiResponse<MyEventResponse>> myEvent(@AuthenticationPrincipal CustomerPrincipal caller) {
        return ResponseEntity.ok(new ApiResponse<>(orderService.myEvent(caller)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/me/event/orders")
    public ResponseEntity<ApiResponse<EventOrderResponse>> order(@AuthenticationPrincipal CustomerPrincipal caller,
                                                                 @Valid @RequestBody EventOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(orderService.order(caller, request.getPlateId())));
    }
}
