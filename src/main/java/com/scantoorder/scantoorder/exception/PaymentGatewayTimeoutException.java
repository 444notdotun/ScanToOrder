package com.scantoorder.scantoorder.exception;

public class PaymentGatewayTimeoutException extends PaymentGatewayException {
    public PaymentGatewayTimeoutException(String message) {
        super(message);
    }
}
