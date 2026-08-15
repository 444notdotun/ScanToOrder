package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String Username;
}
