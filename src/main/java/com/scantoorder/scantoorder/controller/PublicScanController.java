package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import com.scantoorder.scantoorder.service.TenantAccess;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a guest can see after scanning a table's QR code, before they have a seat.
 */
@RestController
@RequestMapping("/api/v1/t/{slug}")
@Validated
public class PublicScanController {

    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private TenantAccess tenantAccess;

    @GetMapping("/tables/{displayNumber}/seatMap")
    public ResponseEntity<ApiResponse<ViewTableAndSeatAvailabilityResponse>> seatMap(
            @PathVariable @Pattern(regexp = "^[a-z0-9]+$", message = "Invalid identifier format") String slug,
            @PathVariable @Min(1) int displayNumber) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(tableService.publicSeatMap(slug, displayNumber)));
    }

    @GetMapping("/menu")
    public ResponseEntity<ApiResponse<MenuResponse>> menu(
            @PathVariable @Pattern(regexp = "^[a-z0-9]+$", message = "Invalid identifier format") String slug) {
        Tenant tenant = tenantAccess.openTenantBySlug(slug);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(menuService.generateMenu(tenant.getTenantId())));
    }
}
