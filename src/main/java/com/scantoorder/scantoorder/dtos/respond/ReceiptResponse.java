package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {
    private String receiptNumber;
    private String paymentReference;
    private String orderId;
    private String tableNumber;
    private String seatLabel;
    private LocalDateTime paymentDate;
    private List<ReceiptItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal totalPaid;
    private PaymentStatus status;
}
