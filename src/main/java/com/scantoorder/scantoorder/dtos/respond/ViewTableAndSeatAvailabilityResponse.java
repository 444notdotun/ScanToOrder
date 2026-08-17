package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scantoorder.scantoorder.data.model.TableStatus;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ViewTableAndSeatAvailabilityResponse {
    private String tableNumber;
    private int Capacity;
    private TableStatus Status;
    private List<ViewTableSeatResponse> seats;

}
