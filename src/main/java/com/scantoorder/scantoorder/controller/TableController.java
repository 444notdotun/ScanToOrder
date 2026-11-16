package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.CloseTableResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.TableResponse;
import com.scantoorder.scantoorder.dtos.respond.TableSessionsResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tables")
@Validated
public class TableController {

    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private DiningSessionService diningSessionService;

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateRestaurantTableResponse>> createTable(
            @Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(tableService.createTable(CurrentTenant.id(), request)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TableResponse>>> getAllTable() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(tableService.viewAllTable(CurrentTenant.id())));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/{tableNumber}/seatMap")
    public ResponseEntity<ApiResponse<ViewTableAndSeatAvailabilityResponse>> getTableSeatMap(
            @NotNull(message = "can not be null")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
            @PathVariable String tableNumber) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(tableService.viewTableAndSeatAvailability(CurrentTenant.id(), tableNumber)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/{tableNumber}/qrcode")
    public ResponseEntity<byte[]> getTableQrCode(
            @NotNull(message = "can not be null")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
            @PathVariable String tableNumber) {
        byte[] qrImage = tableService.getQrCode(CurrentTenant.id(), tableNumber);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"table_" + tableNumber + "_qr.png\"")
                .body(qrImage);
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/{id}/sessions")
    public ResponseEntity<ApiResponse<TableSessionsResponse>> getTableSessions(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format") String id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(diningSessionService.getTableSessions(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CloseTableResponse>> closeTable(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format") String id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(diningSessionService.closeTable(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteTable(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format") String id) {
        tableService.deleteTable(CurrentTenant.id(), id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Table deleted successfully"));
    }
}
