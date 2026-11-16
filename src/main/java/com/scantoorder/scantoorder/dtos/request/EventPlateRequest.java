package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventPlateRequest {
    @NotBlank(message = "Describe the plate, for example: Jollof rice, chicken and Coke")
    @Size(max = 160, message = "That name is too long")
    private String name;
    @Size(max = 400, message = "That description is too long")
    private String description;
    // Switch a plate off (for example when it has run out) without deleting it.
    private Boolean available;
}
