package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeclineTenantRequest {
    @NotBlank(message = "Say why the application is declined")
    @Size(max = 500, message = "Keep the reason under 500 characters")
    private String reason;
}
