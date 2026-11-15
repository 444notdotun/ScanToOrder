package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatClaimedResponse {
    private String message;
    private String token;
    private String sessionId;
    private String joinCode;
    private String seatId;
    private String seatNumber;
    private Integer position;
    private String tableNumber;
    private Integer displayNumber;
}
