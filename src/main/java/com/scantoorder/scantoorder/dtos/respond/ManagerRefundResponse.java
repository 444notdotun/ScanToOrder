package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// One refund in the manager's own list. This is the only place a guest's full account number appears.
@Data
public class ManagerRefundResponse {
    private String refundId;
    private String guestName;
    private String guestPhone;
    private String tableNumber;
    private String seatNumber;
    // What a person reads: "Table 3, Seat 2".
    private Integer tableDisplayNumber;
    private Integer seatPosition;
    private String orderNumber;
    private List<String> items;
    private BigDecimal amount;
    private String paymentReference;
    private LocalDateTime date;
    private String reason;
    private String status;
    private String closedBy;
    // To send by phone or WhatsApp.
    private String guestLink;
    private String guestEmail;
    private String guestBankName;
    private String guestAccountName;
    private String guestAccountNumber;
    private boolean overdue;
    private boolean reportedMissing;
    private String method;
    private String transferReference;
    private LocalDateTime settledAt;
}
