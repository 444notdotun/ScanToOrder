package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderItemRequest {
    @NotNull(message = "itemId can not be null")
    private String itemName;
    @NotNull(message = "itemPrice can not be null")

    private int quantity;
    @NotNull(message = "unitPrice can not be null")

    private String specialInstructions;
}
