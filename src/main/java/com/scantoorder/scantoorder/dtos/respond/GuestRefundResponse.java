package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// What a guest sees at their refund link. The account number is shown by its last four digits only.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestRefundResponse {
    private String refundId;
    private String restaurant;
    private String orderNumber;
    private List<String> items;
    private BigDecimal amount;
    private String status;
    private String whatToDo;
    private String bankName;
    private String accountName;
    private String accountLast4;
    private boolean canAddBankDetails;
    private boolean canConfirmReceived;
    private boolean canReportMissing;
    private boolean reportedMissing;
}
