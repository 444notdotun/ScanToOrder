package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventPayRequest {
    @Min(value = 1, message = "Pay for at least one guest")
    private int guests;
    // Where Paystack sends the receipt. Leave it out and the business email is used.
    @Email(message = "That email doesn't look right")
    @Size(max = 254, message = "That email is too long")
    private String receiptEmail;
}
