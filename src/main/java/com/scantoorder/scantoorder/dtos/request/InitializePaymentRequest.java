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
    @NotBlank(message = "We can't find that order. Go back and try again.")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.")
    private String orderId;
    // Optional: checked as an email only when the guest gave one.
    @Email(message = "That email doesn't look right")
    private String customerEmail;
}
