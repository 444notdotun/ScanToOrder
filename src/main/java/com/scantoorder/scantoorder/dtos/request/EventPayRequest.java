package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EventPayRequest {
    @Min(value = 1, message = "Pay for at least one guest")
    private int guests;
}
