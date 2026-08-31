package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {

    @Autowired
    private WorkerService workerService;

    @PostMapping
    public ResponseEntity<ApiResponse<Worker>> createWorker(@RequestBody CreateWorkerRequest request) {
        Worker worker = workerService.createWorker(request.getName(), request.getRole(), request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(worker));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Worker>>> getAllWorkers() {
        List<Worker> workers = workerService.getAllWorkers();
        return ResponseEntity.ok(new ApiResponse<>(workers));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteWorker(@PathVariable String id) {
        workerService.deleteWorker(id);
        return ResponseEntity.ok(new ApiResponse<>("Worker deleted successfully"));
    }

    @Data
    public static class CreateWorkerRequest {
        private String name;
        private String role;
        private String username;
    }
}
