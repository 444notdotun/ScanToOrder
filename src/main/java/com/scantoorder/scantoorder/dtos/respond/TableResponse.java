package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.TableStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableResponse {
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private int capacity;
    private TableStatus status;
    @JsonProperty("isActive")
    private boolean active;
}
