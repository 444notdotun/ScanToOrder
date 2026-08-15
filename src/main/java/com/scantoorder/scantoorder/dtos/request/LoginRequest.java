package com.scantoorder.scantoorder.dtos.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String Username;
    private String Password;
}
