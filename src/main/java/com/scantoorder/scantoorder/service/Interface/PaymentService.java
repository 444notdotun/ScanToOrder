package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;

public interface PaymentService {
    InitializePaymentResponse initializePayment(InitializePaymentRequest request);
    PaymentStatusResponse verifyAndSyncPayment(String reference);
    void handleWebhook(String payload, String signatureHeader);
}
