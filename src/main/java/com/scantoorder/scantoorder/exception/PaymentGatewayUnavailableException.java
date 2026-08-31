package com.scantoorder.scantoorder.exception;

public class PaymentGatewayUnavailableException extends PaymentGatewayException {
    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }
}
