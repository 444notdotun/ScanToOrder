package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableSessionView {
    private String sessionId;
    private String startedAt;
    private long paidUndeliveredOrders;
    private List<SessionSeatView> seats = new ArrayList<>();
}
