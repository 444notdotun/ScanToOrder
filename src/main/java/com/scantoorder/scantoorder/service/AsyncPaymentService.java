package com.scantoorder.scantoorder.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncPaymentService {

    @Autowired
    private WebhookProcessor webhookProcessor;

    // Paystack is answered at once; the work happens here.
    @Async
    public void processWebhookPayload(String payload) {
        webhookProcessor.process(payload);
    }
}
