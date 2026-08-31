package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class CreateItemRequest {
    @NotBlank(message = "Item name cannot be blank")
    private String itemName;
    @NotNull(message = "Item description cannot be null")
    private String itemDescription;
    @NotBlank(message = "Item category cannot be blank")
    private String CategoryName;
    @NotNull(message = "Item price cannot be null")
    private int itemPrice;

}
