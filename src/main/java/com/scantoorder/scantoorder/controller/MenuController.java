package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'WAITER', 'CHEF')")
    @GetMapping
    public ResponseEntity<ApiResponse<MenuResponse>> generateMenu() {
        MenuResponse response = menuService.generateMenu(CurrentTenant.id());

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }
}
