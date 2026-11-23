package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Type your email.")
    @Size(max = 200, message = "That email is too long.")
    private String email;
}
