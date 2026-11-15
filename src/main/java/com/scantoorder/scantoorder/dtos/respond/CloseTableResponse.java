package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloseTableResponse {
    private String message;
    private String tableId;
    private int closedSessions;
    private long paidUndeliveredOrders;
}
