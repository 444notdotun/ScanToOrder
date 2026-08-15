package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface Auth extends UserDetailsService {
    AuthResponse login (LoginRequest loginRequest);
}
