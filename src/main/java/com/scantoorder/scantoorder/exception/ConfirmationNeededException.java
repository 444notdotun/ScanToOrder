package com.scantoorder.scantoorder.exception;

public class ConfirmationNeededException extends ScanToOrderException {
    private final long paidUndeliveredOrders;

    public ConfirmationNeededException(String message, long paidUndeliveredOrders) {
        super(message);
        this.paidUndeliveredOrders = paidUndeliveredOrders;
    }

    public long getPaidUndeliveredOrders() {
        return paidUndeliveredOrders;
    }
}
