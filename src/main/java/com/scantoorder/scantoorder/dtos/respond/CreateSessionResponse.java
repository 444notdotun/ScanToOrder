package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

@Data
public class CreateSessionResponse {
    private String sessionId;
    private String tableId;
    private String seatId;
}
