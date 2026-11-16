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
    private boolean needsEmail;
    private String guestEmail;
    private boolean canAddBankDetails;
    private boolean canConfirmReceived;
    private boolean canReportMissing;
    private boolean reportedMissing;

    public GuestRefundResponse(String refundId, String restaurant, String orderNumber, java.util.List<String> items,
                               java.math.BigDecimal amount, String status, String whatToDo, String bankName, String accountName,
                               String accountLast4, boolean canAddBankDetails, boolean canConfirmReceived, boolean canReportMissing, boolean reportedMissing) {
        this(refundId, restaurant, orderNumber, items, amount, status, whatToDo, bankName, accountName, accountLast4,
                false, null, canAddBankDetails, canConfirmReceived, canReportMissing, reportedMissing);
    }
}
