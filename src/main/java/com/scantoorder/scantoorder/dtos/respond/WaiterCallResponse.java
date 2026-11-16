package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WaiterCallResponse {
    private String callId;
    private Integer tableNumber;
    private String where;
    private String what;
    // OPEN or TAKEN (or DONE once finished).
    private String state;
    private long minutesWaiting;
    private String takenBy;
}
