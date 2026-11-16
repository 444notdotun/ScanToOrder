package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// What a chef or waiter sees of the day: a count, and nothing about money.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodayResponse {
    private long ordersToday;
}
