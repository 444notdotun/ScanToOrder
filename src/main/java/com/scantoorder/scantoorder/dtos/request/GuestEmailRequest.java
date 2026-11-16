package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GuestEmailRequest {
    @NotBlank(message = "Enter your email")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "That email is too long")
    private String email;
}
