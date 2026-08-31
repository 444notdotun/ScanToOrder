package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KitchenOrderResponse {
    private String orderId;
    private String tableNumber;
    private String seatId;
    private String seatNumber;
    private LocalDateTime createdAt;
    private String status;
    private BigDecimal totalAmount;
    private List<KitchenOrderItemResponse> items = new ArrayList<>();
}
