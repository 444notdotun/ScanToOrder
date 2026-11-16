package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// The orders of a guest's table that this guest may see, and what was paid.
@Data
public class SessionOrdersResponse {
    private List<Order> orders = new ArrayList<>();
    // Paid from this phone: this guest's own orders and those for guests without a phone.
    private BigDecimal paidByYou = BigDecimal.ZERO;
    // Paid by everyone at the table: a total only, never who paid what.
    private BigDecimal paidByTable = BigDecimal.ZERO;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Order {
        private String orderId;
        private String orderNumber;
        // Set once the order is paid; the receipt is found by it.
        private String paymentReference;
        private LocalDateTime createdAt;
        // PENDING_PAYMENT, PAID, PREPARING, READY, DELIVERED, UNAVAILABLE, REFUND_DUE, REFUNDED or CANCELED.
        private String status;
        private List<String> items = new ArrayList<>();
        private BigDecimal amount;
        private Integer forSeatPosition;
        private String forName;
        // True when the order is for the person holding this phone.
        private boolean forMe;
    }
}
