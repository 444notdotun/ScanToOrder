package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClaimSeatRequest {
    @NotBlank(message = "table id cannot be blank")
    private String tableId;
    @NotBlank(message = "seat id cannot be blank")
    private String seatId;
    @NotBlank(message = "customer name cannot be blank")
    private String customerName;
    private String customerEmail;
    @NotBlank(message = "customer phoneNumber cannot be blank")
    private String customerPhoneNumber;
}
