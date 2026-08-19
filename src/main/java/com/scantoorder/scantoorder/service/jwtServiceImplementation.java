package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Manager;
import com.scantoorder.scantoorder.data.model.Worker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
@Service
public class jwtServiceImplementation implements JwtService{
    @Value("${jwt.secret}")
    private String secretKey;
    @Value("${jwt.expiration}")
    private Long expiration;

    @Override
    public String generateToken(Worker worker) {
        return Jwts.builder()
                .claim("type", "WORKER")
                .claim("workerId", worker.getWorkerId())
                .claim("role", worker.getRole())
                .signWith(encodeKey(secretKey))
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis()+expiration))
                .subject(worker.getUsername())
                .compact();
    }

    @Override
    public boolean validateToken(String token, UserDetails player) {
        String email = extractClaims(token).getSubject();
        return email.equals(player.getUsername()) && !isTokenExpired(token) ;
    }

    public String generateCustomerToken(String sessionId, String seatId, String tableId) {
        return Jwts.builder()
                .claim("type", "CUSTOMER")
                .claim("sessionId", sessionId)
                .claim("seatId", seatId)
                .claim("tableId", tableId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(encodeKey(secretKey))
                .compact();
    }

    @Override
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(encodeKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        Claims claims = extractClaims(token);
        return claims.getExpiration().before(new Date());
    }

    private SecretKey encodeKey(String secretKey){
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
