package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Manager;
import com.scantoorder.scantoorder.data.model.Worker;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {
    String generateToken(Worker worker);
    boolean validateToken(String token, UserDetails player);
    Claims extractClaims(String token);
    String generateCustomerToken(String sessionId, String seatId, String tableId);
}
