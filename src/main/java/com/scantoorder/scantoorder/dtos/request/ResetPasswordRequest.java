package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "This link has expired or was already used. Ask for a new one.")
    @Size(max = 200, message = "This link has expired or was already used. Ask for a new one.")
    private String token;
    @NotBlank(message = "Your new password needs at least 8 characters.")
    @Size(max = 100, message = "Your new password needs at least 8 characters.")
    private String newPassword;
}
