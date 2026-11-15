package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClaimSeatRequest {
    @NotBlank(message = "table id cannot be blank")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
    private String tableId;

    @NotBlank(message = "seat id cannot be blank")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
    private String seatId;

    @NotBlank(message = "customer name cannot be blank")
    @Size(max = 80, message = "customer name is too long")
    private String customerName;

    @Email(message = "Invalid email format")
    private String customerEmail;

    @NotBlank(message = "customer phoneNumber cannot be blank")
    @Size(min = 11, max = 11, message = "phone number must be exactly 11 characters")
    private String customerPhoneNumber;
}
