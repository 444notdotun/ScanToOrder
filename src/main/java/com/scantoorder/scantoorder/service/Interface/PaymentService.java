package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;

public interface PaymentService {
    // sessionId is set for a guest, who may only pay for orders of their own session.
    InitializePaymentResponse initializePayment(String tenantId, String sessionId, InitializePaymentRequest request);
    PaymentStatusResponse verifyAndSyncPayment(String tenantId, String reference);
    void handleWebhook(String payload, String signatureHeader);
}
