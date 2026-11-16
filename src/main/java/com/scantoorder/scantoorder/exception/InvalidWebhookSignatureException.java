package com.scantoorder.scantoorder.exception;

public class InvalidWebhookSignatureException extends ScanToOrderException {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
