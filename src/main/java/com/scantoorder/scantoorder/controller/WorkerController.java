package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.request.CreateStaffRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.StaffResponse;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workers")
@PreAuthorize("hasRole('MANAGER')")
@Validated
public class WorkerController {

    @Autowired
    private WorkerService workerService;

    @PostMapping
    public ResponseEntity<ApiResponse<StaffResponse>> createWorker(@Valid @RequestBody CreateStaffRequest request) {
        StaffResponse worker = workerService.createWorker(CurrentTenant.id(), request.getName(), request.getRole(), request.getHandle());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(worker));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getAllWorkers() {
        return ResponseEntity.ok(new ApiResponse<>(workerService.getAllWorkers(CurrentTenant.id())));
    }

    // Staff have no email, so the new temporary password comes back here, once.
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<StaffResponse>> resetPassword(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String id) {
        return ResponseEntity.ok(new ApiResponse<>(workerService.resetPassword(CurrentTenant.id(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteWorker(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String id) {
        workerService.deleteWorker(CurrentTenant.id(), id);
        return ResponseEntity.ok(new ApiResponse<>("Team member removed"));
    }
}
