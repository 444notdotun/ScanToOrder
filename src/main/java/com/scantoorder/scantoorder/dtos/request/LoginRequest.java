package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Enter your username")
    private String username;
    @NotBlank(message = "Enter your password")
    private String password;
}
