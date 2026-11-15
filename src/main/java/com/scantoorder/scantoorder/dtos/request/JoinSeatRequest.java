package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinSeatRequest {
    @NotBlank(message = "join code cannot be blank")
    @Size(max = 12, message = "join code is too long")
    private String joinCode;

    @NotBlank(message = "customer name cannot be blank")
    @Size(max = 80, message = "customer name is too long")
    private String customerName;

    @Email(message = "Invalid email format")
    private String customerEmail;

    @NotBlank(message = "customer phoneNumber cannot be blank")
    @Size(min = 11, max = 11, message = "phone number must be exactly 11 characters")
    private String customerPhoneNumber;
}
