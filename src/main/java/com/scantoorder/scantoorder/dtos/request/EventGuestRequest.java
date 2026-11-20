package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventGuestRequest {
    @NotBlank(message = "Enter the guest's name")
    @Size(max = 120, message = "That name is too long")
    private String name;
    @Size(max = 30, message = "That phone number is too long")
    private String phone;
    @Email(message = "That email doesn't look right")
    @Size(max = 254, message = "That email is too long")
    private String email;
    // Assigned seating only. A table alone gets its next free seat; leave both out and the next free seat anywhere is used.
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "That link doesn't look right. Go back and try again.")
    private String tableId;
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "That link doesn't look right. Go back and try again.")
    private String seatId;
}
