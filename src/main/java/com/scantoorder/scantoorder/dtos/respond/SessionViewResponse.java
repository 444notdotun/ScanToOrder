package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SessionViewResponse {
    private String sessionId;
    private String status;
    private String joinCode;
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private String seatId;
    private boolean holdsEnabled;
    private List<SessionSeatView> seats = new ArrayList<>();
}
