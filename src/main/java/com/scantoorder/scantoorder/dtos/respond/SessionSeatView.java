package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionSeatView {
    private String seatId;
    private String seatNumber;
    private int position;
    private String status;
    private String guestName;
    private String guestPhone;
    private Boolean hasDevice;
    private Boolean mine;
    private LocalDateTime heldUntil;
}
