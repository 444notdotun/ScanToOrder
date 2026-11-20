package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class CreateItemRequest {
    @NotBlank(message = "Enter the item's name")
    private String itemName;
    @NotNull(message = "Add a short description for the item")
    private String itemDescription;
    @NotBlank(message = "Choose a category for the item")
    private String CategoryName;
    @NotNull(message = "Enter the item's price")
    private int itemPrice;
    // Optional: a link to a photo. Leave it out for a dish with no picture.
    @Size(max = 500, message = "That photo link is too long")
    @Pattern(regexp = "^(https?://\\S+)?$", message = "That photo link doesn't look right. It should start with https://")
    private String imageUrl;

}
