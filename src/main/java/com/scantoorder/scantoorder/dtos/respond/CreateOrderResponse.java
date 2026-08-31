package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateOrderResponse {
    private String orderId;
    private String tableNumber;
    private String seatNumber;
    private OrderStatus OrderStatus;
    private BigDecimal totalPrice;
    List<OrderItemResponse> items;

    public CreateOrderResponse(){
        items=new ArrayList<>();
    }

}
