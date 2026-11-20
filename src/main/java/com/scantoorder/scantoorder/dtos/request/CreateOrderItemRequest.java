package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderItemRequest {
    @NotNull(message = "Choose a plate")
    private String itemName;
    @NotNull(message = "Choose how many of that plate you want")
    private int quantity;
    @NotNull(message = "Something went wrong with that plate. Refresh the menu and try again.")
    private String specialInstructions;
}
