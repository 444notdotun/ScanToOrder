package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventPriceResponse {
    private int guests;
    private long feePerGuestKobo;
    private long amountKobo;
    private BigDecimal amount;
}
