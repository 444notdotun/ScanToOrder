package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Choose a new password")
    @Size(min = 8, max = 100, message = "Your new password needs at least 8 characters")
    private String newPassword;
}
