package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.request.EventPayRequest;
import com.scantoorder.scantoorder.dtos.request.EventRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.EventPriceResponse;
import com.scantoorder.scantoorder.dtos.respond.EventResponse;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.service.Interface.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@Validated
public class EventController {

    private static final String ID = "^[A-Za-z0-9_-]+$";

    @Autowired
    private EventService eventService;

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> create(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(eventService.create(CurrentTenant.id(), request)));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CHEF')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(eventService.list(CurrentTenant.id())));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CHEF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> get(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(eventService.get(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> update(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                             @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(eventService.update(CurrentTenant.id(), id, request)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<EventResponse>> end(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(eventService.end(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/{id}/price")
    public ResponseEntity<ApiResponse<EventPriceResponse>> price(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                 @RequestParam @Min(value = 1, message = "Pay for at least one guest") int guests) {
        return ResponseEntity.ok(new ApiResponse<>(eventService.price(CurrentTenant.id(), id, guests)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<InitializePaymentResponse>> pay(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                      @Valid @RequestBody EventPayRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(eventService.startPayment(CurrentTenant.id(), id, request.getGuests())));
    }
}
