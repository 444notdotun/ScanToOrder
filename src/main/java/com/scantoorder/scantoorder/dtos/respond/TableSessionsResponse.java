package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableSessionsResponse {
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private long paidUndeliveredOrders;
    private List<TableSessionView> sessions = new ArrayList<>();
}
