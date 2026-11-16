package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.dtos.request.ChangePasswordRequest;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import com.scantoorder.scantoorder.service.Interface.Auth;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private Auth auth;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login( @RequestBody @Valid LoginRequest request) {
        return withCookie(auth.login(request));
    }

    // The only thing a temporary-password token may do.
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal WorkerPrincipal caller,
            @RequestBody @Valid ChangePasswordRequest request) {
        return withCookie(auth.changePassword(caller, request.getNewPassword()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withCookie(AuthResponse response) {
        ResponseCookie jwtCookie = ResponseCookie.from("access_token", response.getToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofHours(8))
                .build();
        response.setToken(null);
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new ApiResponse<>(response));
    }
}
