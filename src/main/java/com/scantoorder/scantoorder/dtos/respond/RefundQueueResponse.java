package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RefundQueueResponse {
    private List<Request> requests = new ArrayList<>();
    private List<Failed> failedRefunds = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String orderId;
        private String orderNumber;
        private String tableNumber;
        private String seatNumber;
        private String guestName;
        private BigDecimal amount;
        private String reason;
        private LocalDateTime requestedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failed {
        private String orderId;
        private String orderNumber;
        private String paymentReference;
        private BigDecimal amount;
        private LocalDateTime failedAt;
    }
}
