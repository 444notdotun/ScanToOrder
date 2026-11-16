package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.request.EventGuestLimitRequest;
import com.scantoorder.scantoorder.dtos.request.EventGuestRequest;
import com.scantoorder.scantoorder.dtos.request.EventPayRequest;
import com.scantoorder.scantoorder.dtos.request.EventPlateRequest;
import com.scantoorder.scantoorder.dtos.respond.EventGuestResponse;
import com.scantoorder.scantoorder.dtos.respond.EventPlateResponse;
import com.scantoorder.scantoorder.dtos.respond.EventTableResponse;
import com.scantoorder.scantoorder.dtos.respond.EventCoverageResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.service.EventCoverageService;
import com.scantoorder.scantoorder.service.EventGuestService;
import com.scantoorder.scantoorder.service.EventSeatingService;
import com.scantoorder.scantoorder.service.EventPlateService;
import com.scantoorder.scantoorder.service.EventTableService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    @Autowired
    private EventPlateService plateService;
    @Autowired
    private EventTableService tableService;
    @Autowired
    private EventGuestService guestService;
    @Autowired
    private EventSeatingService seatingService;
    @Autowired
    private EventCoverageService coverageService;

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
        return ResponseEntity.ok(new ApiResponse<>(eventService.startPayment(CurrentTenant.id(), id, request.getGuests(), request.getReceiptEmail())));
    }

    // ---- plates

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CHEF')")
    @GetMapping("/{id}/plates")
    public ResponseEntity<ApiResponse<List<EventPlateResponse>>> plates(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(plateService.list(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/plates")
    public ResponseEntity<ApiResponse<EventPlateResponse>> addPlate(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                    @Valid @RequestBody EventPlateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(plateService.create(CurrentTenant.id(), id, request)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}/plates/{plateId}")
    public ResponseEntity<ApiResponse<EventPlateResponse>> updatePlate(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                       @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String plateId,
                                                                       @Valid @RequestBody EventPlateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(plateService.update(CurrentTenant.id(), id, plateId, request)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}/plates/{plateId}")
    public ResponseEntity<Void> deletePlate(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String plateId) {
        plateService.delete(CurrentTenant.id(), id, plateId);
        return ResponseEntity.noContent().build();
    }

    // ---- tables

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER')")
    @GetMapping("/{id}/tables")
    public ResponseEntity<ApiResponse<List<EventTableResponse>>> tables(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(tableService.list(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/tables")
    public ResponseEntity<ApiResponse<EventTableResponse>> addTable(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                    @Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(tableService.create(CurrentTenant.id(), id, request)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}/tables/{tableId}")
    public ResponseEntity<Void> deleteTable(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String tableId) {
        tableService.delete(CurrentTenant.id(), id, tableId);
        return ResponseEntity.noContent().build();
    }

    // ---- guests

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER')")
    @GetMapping("/{id}/guests")
    public ResponseEntity<ApiResponse<List<EventGuestResponse>>> guests(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(guestService.list(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/guests")
    public ResponseEntity<ApiResponse<EventGuestResponse>> addGuest(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                    @Valid @RequestBody EventGuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(guestService.add(CurrentTenant.id(), id, request)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}/guests/{guestId}")
    public ResponseEntity<Void> removeGuest(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                            @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String guestId) {
        guestService.remove(CurrentTenant.id(), id, guestId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/{id}/guests/{guestId}/limit")
    public ResponseEntity<ApiResponse<EventGuestResponse>> raiseLimit(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                      @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String guestId,
                                                                      @Valid @RequestBody EventGuestLimitRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(guestService.raiseLimit(CurrentTenant.id(), id, guestId, request)));
    }

    // The planner shares the codes themselves: emailing them is coming later.
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/{id}/guests/export")
    public ResponseEntity<byte[]> export(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"guests.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(guestService.exportCsv(CurrentTenant.id(), id));
    }

    // ---- the day

    // An usher frees the guest's seat so their code works again on a new phone.
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER')")
    @PostMapping("/{id}/guests/{guestId}/release")
    public ResponseEntity<ApiResponse<ReleaseSeatResponse>> release(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id,
                                                                    @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String guestId) {
        return ResponseEntity.ok(new ApiResponse<>(seatingService.release(CurrentTenant.id(), id, guestId)));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CHEF')")
    @GetMapping("/{id}/coverage")
    public ResponseEntity<ApiResponse<EventCoverageResponse>> coverage(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(coverageService.coverage(CurrentTenant.id(), id)));
    }
}
