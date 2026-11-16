package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.FloorResponse;
import com.scantoorder.scantoorder.dtos.respond.FloorTableResponse;
import com.scantoorder.scantoorder.dtos.respond.OrderActionResponse;
import com.scantoorder.scantoorder.dtos.respond.ServiceOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.TodayResponse;
import com.scantoorder.scantoorder.dtos.respond.WaiterCallResponse;
import com.scantoorder.scantoorder.service.OrderStepService;
import com.scantoorder.scantoorder.service.ServiceBoardService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The kitchen's and the waiters' screens, and the manager's floor.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ServiceFloorController {

    private static final String ID = "^[A-Za-z0-9_-]+$";

    @Autowired
    private OrderStepService steps;
    @Autowired
    private ServiceBoardService board;

    // ---- steps

    @PreAuthorize("hasAnyRole('CHEF', 'MANAGER')")
    @PostMapping("/orders/{id}/start")
    public ResponseEntity<ApiResponse<OrderActionResponse>> start(@AuthenticationPrincipal WorkerPrincipal caller,
                                                                  @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(steps.start(CurrentTenant.id(), caller.getWorkerId(), id)));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'MANAGER')")
    @PostMapping("/orders/{id}/ready")
    public ResponseEntity<ApiResponse<OrderActionResponse>> ready(@AuthenticationPrincipal WorkerPrincipal caller,
                                                                  @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(steps.ready(CurrentTenant.id(), caller.getWorkerId(), id)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/orders/{id}/deliver")
    public ResponseEntity<ApiResponse<OrderActionResponse>> deliver(@AuthenticationPrincipal WorkerPrincipal caller,
                                                                    @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(steps.deliver(CurrentTenant.id(), caller.getWorkerId(), id)));
    }

    // ---- the waiter

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/waiter/ready")
    public ResponseEntity<ApiResponse<List<ServiceOrderResponse>>> ready() {
        return ResponseEntity.ok(new ApiResponse<>(board.ready(CurrentTenant.id())));
    }

    // Orders the kitchen could not make: the waiter goes to the table.
    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/waiter/visits")
    public ResponseEntity<ApiResponse<List<ServiceOrderResponse>>> visits() {
        return ResponseEntity.ok(new ApiResponse<>(board.visits(CurrentTenant.id())));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/waiter/calls")
    public ResponseEntity<ApiResponse<List<WaiterCallResponse>>> calls() {
        return ResponseEntity.ok(new ApiResponse<>(board.calls(CurrentTenant.id())));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/calls/{id}/take")
    public ResponseEntity<ApiResponse<WaiterCallResponse>> take(@AuthenticationPrincipal WorkerPrincipal caller,
                                                                @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(board.take(CurrentTenant.id(), caller.getWorkerId(), id)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @PostMapping("/calls/{id}/done")
    public ResponseEntity<ApiResponse<WaiterCallResponse>> done(@AuthenticationPrincipal WorkerPrincipal caller,
                                                                @PathVariable @Pattern(regexp = ID, message = "Invalid identifier") String id) {
        return ResponseEntity.ok(new ApiResponse<>(board.done(CurrentTenant.id(), caller.getWorkerId(), id)));
    }

    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    @GetMapping("/waiter/tables")
    public ResponseEntity<ApiResponse<List<FloorTableResponse>>> tables() {
        return ResponseEntity.ok(new ApiResponse<>(board.tables(CurrentTenant.id())));
    }

    // ---- the day and the floor

    // A count and nothing about money: chefs and waiters see only this.
    @PreAuthorize("hasAnyRole('CHEF', 'WAITER', 'MANAGER')")
    @GetMapping("/staff/today")
    public ResponseEntity<ApiResponse<TodayResponse>> today() {
        return ResponseEntity.ok(new ApiResponse<>(board.today(CurrentTenant.id())));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/floor")
    public ResponseEntity<ApiResponse<FloorResponse>> floor() {
        return ResponseEntity.ok(new ApiResponse<>(board.floor(CurrentTenant.id())));
    }
}
