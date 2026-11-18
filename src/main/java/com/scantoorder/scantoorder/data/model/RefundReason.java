package com.scantoorder.scantoorder.data.model;

public enum RefundReason {
    KITCHEN_UNAVAILABLE,
    LATE_PAYMENT,
    DOUBLE_PAYMENT,
    // The guest paid a checkout whose amount no longer matches the order.
    AMOUNT_CHANGED;
}
