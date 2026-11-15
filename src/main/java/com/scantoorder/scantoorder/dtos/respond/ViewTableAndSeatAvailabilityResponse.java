package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.TableStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ViewTableAndSeatAvailabilityResponse {
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private int Capacity;
    private TableStatus Status;
    private List<ViewTableSeatResponse> seats;
}
