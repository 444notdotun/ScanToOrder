package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitializePaymentRequest {
    @NotBlank(message = "Order ID is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
    private String orderId;
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;
}
