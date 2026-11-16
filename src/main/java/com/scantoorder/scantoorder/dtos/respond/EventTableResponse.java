package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventTableResponse {
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private int capacity;
}
