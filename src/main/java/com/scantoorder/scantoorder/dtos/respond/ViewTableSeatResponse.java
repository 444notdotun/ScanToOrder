package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class
ViewTableSeatResponse {
    private String seatId;
    private String seatNumber;
    private int position;
    private SeatStatus status;
}
