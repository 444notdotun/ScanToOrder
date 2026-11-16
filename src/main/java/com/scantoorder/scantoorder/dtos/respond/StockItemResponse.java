package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One plate on the kitchen's "what we have left" list, and in the waiter's swap choices.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItemResponse {
    private String itemId;
    private String itemName;
    private String categoryName;
    private boolean available;
}
