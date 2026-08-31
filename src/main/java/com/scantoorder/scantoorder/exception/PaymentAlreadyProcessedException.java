package com.scantoorder.scantoorder.exception;

public class PaymentAlreadyProcessedException extends ScanToOrderException {
    public PaymentAlreadyProcessedException(String message) {
        super(message);
    }
}
