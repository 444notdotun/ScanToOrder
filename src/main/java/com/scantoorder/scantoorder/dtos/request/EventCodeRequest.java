package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventCodeRequest {
    @NotBlank(message = "Type your code")
    @Size(max = 20, message = "That code is too long")
    private String code;
}
