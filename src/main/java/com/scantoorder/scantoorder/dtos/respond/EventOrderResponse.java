package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventOrderResponse {
    private String orderId;
    private String orderNumber;
    private String plate;
    private String status;
    private int platesUsed;
    private Integer plateLimit;
    private String message;
}
