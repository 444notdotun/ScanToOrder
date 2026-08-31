package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;

public interface ReceiptService {
    ReceiptResponse getReceiptData(String paymentReference);
    byte[] generateReceiptCsv(String paymentReference);
}
