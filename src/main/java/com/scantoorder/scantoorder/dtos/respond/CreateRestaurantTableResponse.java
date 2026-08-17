package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scantoorder.scantoorder.data.model.TableStatus;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class CreateRestaurantTableResponse {
    private String tableNumber;
    private TableStatus status;
    private String message;
}
