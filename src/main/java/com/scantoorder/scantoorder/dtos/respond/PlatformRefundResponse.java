package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A refund the platform owner may need to chase. No bank account numbers.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRefundResponse {
    private String refundId;
    private String tenantName;
    private String orderNumber;
    private BigDecimal amount;
    private String reason;
    private String status;
    private LocalDateTime dueSince;
    private boolean overdue;
    private boolean reportedMissing;
}
