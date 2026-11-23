package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailReceiptRequest {
    @NotBlank(message = "That email doesn't look right.")
    private String email;
}
