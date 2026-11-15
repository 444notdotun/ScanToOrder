package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTableRequest {
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 20;

    @Min(value = MIN_CAPACITY, message = "a table needs at least 1 seat")
    @Max(value = MAX_CAPACITY, message = "a table can have at most 20 seats")
    private int seatCapacity;

    @Min(value = 1, message = "table number must be 1 or more")
    private Integer displayNumber;
}
