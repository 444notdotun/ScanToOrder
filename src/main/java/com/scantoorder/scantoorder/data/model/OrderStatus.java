package com.scantoorder.scantoorder.data.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    READY,
    DELIVERED,
    CANCELED,
    UNAVAILABLE,
    REFUND_DUE,
    REFUNDED;
}
