package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmGuestRequest {
    @NotBlank(message = "Choose a seat")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.")
    private String seatId;

    @NotBlank(message = "Enter the guest's name")
    @Size(max = 80, message = "That name is too long. Keep it under 80 letters.")
    private String guestName;
}
