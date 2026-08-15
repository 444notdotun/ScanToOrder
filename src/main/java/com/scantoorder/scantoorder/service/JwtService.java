package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Manager;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {
    String generateToken(Manager manager);
    boolean validateToken(String token, UserDetails player);
    Claims extractClaims(String token);
}
