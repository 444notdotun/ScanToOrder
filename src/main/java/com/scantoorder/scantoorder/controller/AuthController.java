package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.dtos.request.ChangePasswordRequest;
import com.scantoorder.scantoorder.dtos.request.ForgotPasswordRequest;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.request.ResetPasswordRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.PasswordResetService;
import com.scantoorder.scantoorder.utils.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
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
    @Autowired
    private PasswordResetService passwordReset;

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

    // Public. The answer is the same for every email, so it tells nobody which accounts exist.
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(new ApiResponse<>(passwordReset.ask(request.getEmail(), ClientAddress.of(http))));
    }

    // Public. It does not sign anyone in: the person signs in with the new password.
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(passwordReset.reset(request.getToken(), request.getNewPassword())));
    }

    // Signing out ends the cookie here, because the page cannot reach an HttpOnly cookie to clear it.
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        ResponseCookie expired = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .body(new ApiResponse<>("You are signed out"));
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
