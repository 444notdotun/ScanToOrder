package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmGuestRequest {
    @NotBlank(message = "seat id cannot be blank")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
    private String seatId;

    @NotBlank(message = "guest name cannot be blank")
    @Size(max = 80, message = "guest name is too long")
    private String guestName;
}
