package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;

public interface ReceiptService {
    ReceiptResponse getReceiptData(String tenantId, String paymentReference);
    byte[] generateReceiptCsv(String tenantId, String paymentReference);
}
