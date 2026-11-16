package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.StockItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ToggleItemResponse;
import com.scantoorder.scantoorder.service.MenuStockService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu/items")
@Validated
public class MenuStockController {

    private static final String ID = "^[A-Za-z0-9_-]+$";

    @Autowired
    private MenuStockService stock;

    @PreAuthorize("hasAnyRole('CHEF', 'WAITER', 'MANAGER')")
    @GetMapping("/stock")
    public ResponseEntity<ApiResponse<List<StockItemResponse>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(stock.list(CurrentTenant.id())));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'MANAGER')")
    @PostMapping("/{id}/sold-out")
    public ResponseEntity<ApiResponse<ToggleItemResponse>> soldOut(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(stock.soldOut(CurrentTenant.id(), id)));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'MANAGER')")
    @PostMapping("/{id}/back-in-stock")
    public ResponseEntity<ApiResponse<ToggleItemResponse>> backInStock(@PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(stock.backInStock(CurrentTenant.id(), id)));
    }
}
