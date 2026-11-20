package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinSeatRequest {
    @NotBlank(message = "Enter your friend's code")
    @Size(max = 12, message = "That code is too long")
    private String joinCode;

    @NotBlank(message = "Enter your name")
    @Size(max = 80, message = "That name is too long. Keep it under 80 letters.")
    private String customerName;

    @Email(message = "That email doesn't look right")
    private String customerEmail;

    @NotBlank(message = "Enter your 11-digit phone number")
    @Size(min = 11, max = 11, message = "Enter your 11-digit phone number")
    private String customerPhoneNumber;
}
