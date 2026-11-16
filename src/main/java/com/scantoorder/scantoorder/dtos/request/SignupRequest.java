package com.scantoorder.scantoorder.dtos.request;

import com.scantoorder.scantoorder.data.model.TenantType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
    @NotNull(message = "Choose restaurant or events")
    private TenantType type;

    @NotBlank(message = "Tell us the name of your business")
    @Size(max = 80, message = "That business name is too long")
    private String businessName;

    @NotBlank(message = "Tell us your name")
    @Size(max = 80, message = "That name is too long")
    private String ownerName;

    @NotBlank(message = "Tell us your email")
    @Email(message = "That email doesn't look right")
    @Size(max = 120, message = "That email is too long")
    private String email;

    @NotBlank(message = "Tell us your phone number")
    @Pattern(regexp = "^[0-9+ ()-]{7,20}$", message = "That phone number doesn't look right")
    private String phone;

    private String bankCode;

    @Pattern(regexp = "^([0-9]{10})?$", message = "An account number has 10 digits")
    private String accountNumber;

    private String accountName;
}
