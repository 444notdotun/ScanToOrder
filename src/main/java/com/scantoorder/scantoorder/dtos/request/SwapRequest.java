package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SwapRequest {
    @NotEmpty(message = "Choose what the guest will have instead")
    @Size(max = 30, message = "That is too many plates for one swap")
    @Valid
    private List<CreateOrderItemRequest> items = new ArrayList<>();

    // Anything worth remembering about the swap, such as what was settled at the table.
    @Size(max = 300, message = "Keep the note under 300 characters")
    private String note;
}
