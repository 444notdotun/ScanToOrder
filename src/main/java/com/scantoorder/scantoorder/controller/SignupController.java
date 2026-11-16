package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.SignupRequest;
import com.scantoorder.scantoorder.dtos.request.VerifyAccountRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.SignupResponse;
import com.scantoorder.scantoorder.dtos.respond.VerifyAccountResponse;
import com.scantoorder.scantoorder.service.Interface.SignupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/signup")
@Validated
public class SignupController {

    @Autowired
    private SignupService signupService;

    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<BankData>>> banks() {
        return ResponseEntity.ok(new ApiResponse<>(signupService.banks()));
    }

    @GetMapping("/name-available")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> nameAvailable(@RequestParam String name) {
        return ResponseEntity.ok(new ApiResponse<>(Map.of("available", signupService.nameAvailable(name))));
    }

    @PostMapping("/verify-account")
    public ResponseEntity<ApiResponse<VerifyAccountResponse>> verifyAccount(@Valid @RequestBody VerifyAccountRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(signupService.verifyAccount(request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(signupService.signup(request)));
    }
}
