package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class Health {
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.status(HttpStatus.OK).body("Service is running. Total sessions: " + dinningSessionRepo.count());
    }
}
