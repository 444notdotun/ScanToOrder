package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PayoutAccountRequest {
    @NotBlank(message = "Enter your current password")
    private String currentPassword;
    @NotBlank(message = "Choose your bank")
    @Pattern(regexp = "^[0-9A-Za-z-]{1,12}$", message = "Choose your bank from the list")
    private String bankCode;
    @NotBlank(message = "Enter the account number")
    @Pattern(regexp = "^[0-9]{10}$", message = "An account number has 10 digits")
    private String accountNumber;
}
