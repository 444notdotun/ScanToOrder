package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateOrderRequest {
 @NotNull(message = "orderItems can not be null")
   private List<CreateOrderItemRequest> orderItems;

 // Optional: the seat this order is for. Defaults to the caller's own seat.
 @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format")
   private String seatId;
 public CreateOrderRequest(){
     orderItems=new ArrayList<>();
    }


}
