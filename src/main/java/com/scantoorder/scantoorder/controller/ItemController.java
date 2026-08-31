package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ToggleItemResponse;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/items")
@Validated
public class ItemController {

    @Autowired
    private ItemService itemService;

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateItemResponse>> createItem(@Valid @RequestBody CreateItemRequest request) {
        CreateItemResponse response = itemService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<ToggleItemResponse>> toggleItem(
            @PathVariable String id) {
        ToggleItemResponse response = itemService.toggleItem(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CreateItemResponse>> updateItem(
            @PathVariable String id,
            @Valid @RequestBody CreateItemRequest request) {
        CreateItemResponse response = itemService.updateItem(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteItem(
            @PathVariable String id) {
        itemService.deleteItem(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>("Item deleted successfully"));
    }
}
