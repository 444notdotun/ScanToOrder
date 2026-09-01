package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import com.scantoorder.scantoorder.service.Interface.Auth;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")

public class AuthController {

    @Autowired
    private Auth auth;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login( @RequestBody @Valid LoginRequest request) {
        AuthResponse response = auth.login(request);
        
        ResponseCookie jwtCookie = org.springframework.http.ResponseCookie.from("access_token", response.getToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(java.time.Duration.ofHours(8))
                .build();
        response.setToken(null);
        
        return ResponseEntity.status(HttpStatus.OK)
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new ApiResponse<>(response));
    }
}
