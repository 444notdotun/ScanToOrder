package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateOrderRequest {
 @NotNull(message = "orderItems can not be null")
   private List<CreateOrderItemRequest> orderItems;
 public CreateOrderRequest(){
     orderItems=new ArrayList<>();
    }


}
