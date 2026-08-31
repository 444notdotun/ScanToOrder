package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KitchenOrderItemResponse {
    private String tableNumber;
    private String SeatId;
    private String itemName;
    private int quantity;
    private String SpecialInstructions;

}
