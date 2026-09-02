package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tables")
@Validated
public class TableController {

    @Autowired
    private RestaurantTableService tableService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRestaurantTableResponse>> createTable(
            @Min(value = 4, message = "seat capacity can not be less than 4") 
            @RequestBody int seatCapacity) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(tableService.createTable(seatCapacity)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RestaurantTable>>> getAllTable() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(tableService.viewAllTable()));
    }

    @GetMapping("/{tableNumber}/seatMap")
    public ResponseEntity<ApiResponse<ViewTableAndSeatAvailabilityResponse>> getTableSeatMap(
            @NotNull(message = "can not be null") 
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
            @PathVariable String tableNumber) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(tableService.viewTableAndSeatAvailability(tableNumber)));
    }

    @GetMapping("/{tableNumber}/status")
    public ResponseEntity<ApiResponse<TableStatus>> getTableStatus(
            @NotNull(message = "can not be null") 
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
            @PathVariable String tableNumber) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(tableService.syncTableStatus(tableNumber)));
    }

    @GetMapping("/{tableNumber}/qrcode")
    public ResponseEntity<byte[]> getTableQrCode(
            @NotNull(message = "can not be null") 
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
            @PathVariable String tableNumber) {
        byte[] qrImage = tableService.getQrCode(tableNumber);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"table_" + tableNumber + "_qr.png\"")
                .body(qrImage);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteTable(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format") String id) {
        tableService.deleteTable(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Table deleted successfully"));
    }
}
