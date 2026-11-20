package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClaimSeatRequest {
    @NotBlank(message = "Scan the card on your table again")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.")
    private String tableId;

    @NotBlank(message = "Choose a seat")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.")
    private String seatId;

    @NotBlank(message = "Enter your name")
    @Size(max = 80, message = "That name is too long. Keep it under 80 letters.")
    private String customerName;

    @Email(message = "That email doesn't look right")
    private String customerEmail;

    @NotBlank(message = "Enter your 11-digit phone number")
    @Size(min = 11, max = 11, message = "Enter your 11-digit phone number")
    private String customerPhoneNumber;
}
