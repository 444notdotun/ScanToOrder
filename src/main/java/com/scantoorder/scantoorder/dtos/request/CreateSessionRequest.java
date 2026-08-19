package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSessionRequest {
    @NotBlank(message = "seat id cannot be blank")
    private String seatId;
    @NotBlank(message = "table id cannot be blank")
    private String tableNumber;
    @NotBlank(message = "customer name cannot be blank")
    private String customerName;
    @NotBlank(message = "customer phone cannot be blank")
    @Min(value = 11,message = "phone number can not be less than 11")
    @Max(value = 11,message = "phone number can not be greater than 11")
    private String customerPhone;
    @Email(message = "invalid email")
    @NotBlank(message = "customer email cannot be blank")
    private String customerEmail;

}
