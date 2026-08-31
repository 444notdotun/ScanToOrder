package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private String reference;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;
    private BigDecimal amount;
}
