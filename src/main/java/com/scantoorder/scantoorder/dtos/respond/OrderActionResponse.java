package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderActionResponse {
    private String orderId;
    private String orderNumber;
    private String status;
    private String message;
}
