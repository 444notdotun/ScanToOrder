package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;

public interface ReceiptService {
    ReceiptResponse getReceiptData(String tenantId, String paymentReference);
    // Sends the receipt to an address the guest typed. Returns what to tell them.
    String emailReceipt(String tenantId, String paymentReference, String email, String address);
}
