package com.scantoorder.scantoorder.data.model;

import lombok.Data;

@Data
public class CustomerPrincipal {
    private final String sessionId;
    private final String seatId;
    private final String tableId;

    public CustomerPrincipal(String sessionId, String seatId, String tableId) {
        this.sessionId = sessionId;
        this.seatId = seatId;
        this.tableId = tableId;
    }

}
