package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryRequest {
    @NotBlank(message = "Enter a name for the category")
    String categoryName;
}
