package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.TableStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class CreateRestaurantTableResponse {
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private Integer capacity;
    private TableStatus status;
    private String message;
}
