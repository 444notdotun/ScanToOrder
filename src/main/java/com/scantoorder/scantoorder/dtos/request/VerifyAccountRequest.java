package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyAccountRequest {
    @NotBlank(message = "Choose your bank")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Choose your bank")
    private String bankCode;

    @NotBlank(message = "Tell us your account number")
    @Pattern(regexp = "^[0-9]{10}$", message = "An account number has 10 digits")
    private String accountNumber;
}
