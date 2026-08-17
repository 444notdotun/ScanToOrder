package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ViewTableSeatResponse {
    private String seatNumber;
    private SeatStatus status;

}
